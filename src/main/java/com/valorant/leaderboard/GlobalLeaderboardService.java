package com.valorant.leaderboard;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GlobalLeaderboardService {
  public static final Map<String,String> REGIONS=Map.of("ap","Asia-Pacific","kr","South Korea","eu","Europe","na","North America","latam","Latin America","br","Brazil");
  private static final Logger log=LoggerFactory.getLogger(GlobalLeaderboardService.class);
  private static final String LATEST="(select max(id) from global_leaderboard_runs where act=:act and status='COMPLETE')";
  private static final String FILTER=" (:region='all' or p.region=:region) and (:country='all' or coalesce(p.country,'unknown')=:country) ";
  private final JdbcTemplate db;
  private final NamedParameterJdbcTemplate sql;
  private final TrackerLeaderboardClient client;
  private final ObjectMapper mapper;
  private final TransactionTemplate tx;
  private final Map<String,String> countryNames;
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private final ExecutorService fetchers=Executors.newFixedThreadPool(10);
  private final AtomicBoolean running=new AtomicBoolean();
  @Value("${leaderboard.schedule-zone:Asia/Kolkata}") private String scheduleZone="Asia/Kolkata";
  @Value("${leaderboard.act:d816f426-48ea-f052-117f-9697a155b319}") private String act;
  @Value("${leaderboard.enabled:true}") private volatile boolean enabled;
  @Value("${leaderboard.pause-all-extraction:false}") private volatile boolean pauseAllExtraction;
  @Value("${LEADERBOARD_LIVE_PAUSE_MARKER:}") private String livePauseMarker="";
  @Value("${leaderboard.global.max-pages:2000}") private int maxPages;
  @Value("${leaderboard.global.request-delay-ms:500}") private long delay;
  @Value("${leaderboard.global.concurrency:5}") private int concurrency=5;
  @Value("${leaderboard.global.regional-fallback:true}") private boolean regionalFallback;
  @Value("${leaderboard.bootstrap-directory:}") private volatile String bootstrapDirectory="";
  private volatile String lastError;
  @Value("${leaderboard.live-cache-directory:}") private String liveCacheDirectory="";
  @Value("${leaderboard.resume-capture:}") private String resumeCapture="";
  private volatile LiveCapture pendingCapture;
  @Value("${leaderboard.collection-interval-ms:1200000}") private long collectionInterval=1200000;
  private final ScheduledExecutorService collectionScheduler=Executors.newSingleThreadScheduledExecutor();
  private volatile ScheduledFuture<?> scheduleFuture;
  private final Object scheduleGuard=new Object();
  private volatile boolean pendingScheduledRun,closing;
  private volatile String lastScheduledOutcome="not triggered";
  private final AtomicBoolean schedulerStarted=new AtomicBoolean();
  private volatile Instant lastScheduledTriggerAt,collectionStartedAt,lastProgressAt;
  private volatile boolean lastScheduledAccepted;
  private volatile String activeSource="";
  private volatile int activePage,pagesFetched;

  public GlobalLeaderboardService(JdbcTemplate db,TrackerLeaderboardClient client,ObjectMapper mapper,PlatformTransactionManager tm)throws Exception {
    this.db=db;this.sql=new NamedParameterJdbcTemplate(db);this.client=client;this.mapper=mapper;this.tx=new TransactionTemplate(tm);
    try(var stream=new ClassPathResource("global-countries.json").getInputStream()){countryNames=mapper.readValue(stream,new TypeReference<LinkedHashMap<String,String>>(){});}
    countryNames.put("kr","South Korea");countryNames.put("unknown","Unknown country");
  }
  @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
  public void startCollectionSchedule(){
    recoverInterruptedCollection();
    if(!enabled||pauseAllExtraction||!schedulerStarted.compareAndSet(false,true))return;
    scheduleCollection();
  }
  private void recoverInterruptedCollection(){
    try{db.execute((ConnectionCallback<Void>)connection->{
      try(var statement=connection.createStatement();var result=statement.executeQuery("select pg_try_advisory_lock(782613504)")){
        result.next();if(!result.getBoolean(1))return null;
      }
      try{db.update("update global_leaderboard_runs set status='FAILED',completed_at=?,error='Previous process stopped before publication; durable page checkpoint retained' where act=? and status='RUNNING'",OffsetDateTime.now(ZoneOffset.UTC),act);}
      finally{try(var statement=connection.createStatement()){statement.execute("select pg_advisory_unlock(782613504)");}}
      return null;
    });}catch(Exception error){log.warn("Could not reconcile interrupted live collection; durable checkpoint remains available",error);}
  }
  private void scheduleCollection(){
    long interval=Math.max(1000,collectionInterval);
    scheduleFuture=collectionScheduler.scheduleAtFixedRate(this::safeScheduledTrigger,interval,interval,TimeUnit.MILLISECONDS);
    safeScheduledTrigger();
  }
  public void enableAfterHistoricalArchive(){
    synchronized(scheduleGuard){
      if(closing||enabled||pauseAllExtraction)return;
      enabled=true;
      if(!schedulerStarted.compareAndSet(false,true))return;
      if(!livePauseMarker.isBlank())try{java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(livePauseMarker));}catch(Exception e){log.warn("Live extraction resumed but pause marker could not be removed: {}",e.getMessage());}
      log.info("All historical act ladders are complete; resuming live current-act extraction");
    }
    scheduleCollection();
  }
  private void safeScheduledTrigger(){try{scheduled();}catch(Exception error){lastScheduledAccepted=false;lastError="Scheduler trigger failed: "+Objects.toString(error.getMessage(),error.getClass().getSimpleName());log.error("Scheduled collection trigger failed; future triggers remain scheduled",error);}}
  public void scheduled(){if(pauseAllExtraction)return;synchronized(scheduleGuard){
    lastScheduledTriggerAt=Instant.now();
    if(!enabled||closing){lastScheduledAccepted=false;lastScheduledOutcome="disabled";return;}
    if(running.get()){pendingScheduledRun=true;lastScheduledAccepted=false;lastScheduledOutcome="queued after current extraction";return;}
    lastScheduledAccepted=collect();lastScheduledOutcome=lastScheduledAccepted?"started":"not accepted";
  }}
  public boolean collect(){
    synchronized(scheduleGuard){return !pauseAllExtraction&&launchCollection();}
  }
  private boolean launchCollection(){
    if(closing||pauseAllExtraction)return false;
    if(!enabled||!running.compareAndSet(false,true))return false;
    lastError=null;
    collectionStartedAt=Instant.now();lastProgressAt=null;activeSource="starting";activePage=0;pagesFetched=0;
    try{worker.submit(()->{
      try{db.execute((ConnectionCallback<Void>)connection->{
        try(var statement=connection.createStatement();var result=statement.executeQuery("select pg_try_advisory_lock(782613504)")){
          result.next();if(!result.getBoolean(1))return null;
        }
        try{
          db.update("update global_leaderboard_runs set status='FAILED',completed_at=?,error='Previous process stopped before publication' where status='RUNNING'",OffsetDateTime.now(ZoneOffset.UTC));
          collectSnapshot();
        }finally{try(var statement=connection.createStatement()){statement.execute("select pg_advisory_unlock(782613504)");}}
        return null;
      });}catch(Exception error){lastError=Objects.toString(error.getMessage(),error.getClass().getSimpleName());log.warn("Global collection failed",error);}
      finally{synchronized(scheduleGuard){
        running.set(false);
        if(pendingScheduledRun&&enabled&&!closing){
          pendingScheduledRun=false;
          log.info("Starting the coalesced pending extraction after the prior run finished");
          try{lastScheduledAccepted=collect();lastScheduledOutcome=lastScheduledAccepted?"started queued extraction":"queued extraction not accepted";}
          catch(Exception error){lastError="Queued extraction failed to start: "+error.getMessage();log.error("Queued extraction start failed",error);}
        }
      }}
    });}catch(RejectedExecutionException error){running.set(false);throw error;}return true;
  }
  private void collectSnapshot(){
    VerifiedCapture capture;
    try{capture=bootstrapDirectory.isBlank()?null:new VerifiedCapture(bootstrapDirectory,act,mapper);}
    catch(Exception error){throw new IllegalStateException("Invalid bootstrap capture",error);}
    if(pendingCapture!=null&&pendingCapture.started().isBefore(OffsetDateTime.now(ZoneOffset.UTC).minusHours(2))){log.warn("Collection checkpoint exceeded two hours; retaining files and starting fresh");pendingCapture=null;}
    if(capture==null&&!liveCacheDirectory.isBlank()&&pendingCapture==null){
      try{pendingCapture=new LiveCapture(liveCacheDirectory,resumeCapture,act,mapper);resumeCapture="";}
      catch(Exception error){throw new IllegalStateException("Cannot initialize live checkpoint",error);}
    }
    final LiveCapture live=capture==null?pendingCapture:null;
    boolean reconcile=regionalFallback&&regionalDue();
    long run=db.queryForObject("insert into global_leaderboard_runs(act,started_at,status,source) values (?,?,'RUNNING',?) returning id",Long.class,act,capture==null?(live==null?OffsetDateTime.now(ZoneOffset.UTC):live.started()):capture.started(),(reconcile?"global+regional":"global")+(capture==null?"":" verified capture"));
    var observations=new GlobalObservations();var regionalObservations=new GlobalObservations();var coverage=new LinkedHashMap<String,Object>();int totalPages=0;
    try{
      List<String> sources=new ArrayList<>(List.of("global"));if(reconcile)sources.addAll(new TreeSet<>(REGIONS.keySet()));
      long collectionStarted=System.nanoTime();
      for(String source:sources){
        activeSource=source;
        long sourceStarted=System.nanoTime();
        var sourceIds=new HashSet<String>();int sourcePages=0,consecutiveNoProgress=0;boolean ended=false;
        int window=Math.max(1,Math.min(10,concurrency));
        var pending=new ArrayDeque<Future<TrackerLeaderboardClient.Page>>();int nextPage=1;
        try{
          for(;nextPage<=Math.min(window,maxPages);nextPage++)pending.add(submitPage(capture,source,nextPage));
          while(!pending.isEmpty()){
            activePage=sourcePages+1;
            var response=pending.removeFirst().get();int added=0;
            lastProgressAt=Instant.now();
            for(var player:response.players())if(sourceIds.add(player.region()+"|"+player.key().toLowerCase(Locale.ROOT)))added++;
            consecutiveNoProgress=response.players().size()==100&&added==0?consecutiveNoProgress+1:0;
            if(consecutiveNoProgress>=5)throw new IllegalStateException("Five consecutive full pages made no identity progress in "+source);
            observations.add(response,source);if(!source.equals("global"))regionalObservations.add(response,source);
            if(!response.players().isEmpty()){sourcePages++;totalPages++;pagesFetched=totalPages;}
            if(response.players().size()<100){ended=true;break;}
            if(totalPages%10==0){
              var progress=new LinkedHashMap<String,Object>(coverage);
              progress.put("activeSource",source);progress.put("sourcePages",sourcePages);
              progress.put("elapsedSeconds",(System.nanoTime()-collectionStarted)/1_000_000_000L);
              progress.put("sourceElapsedSeconds",(System.nanoTime()-sourceStarted)/1_000_000_000L);
              db.update("update global_leaderboard_runs set pages=?,players=?,raw_rows=?,duplicate_rows=?,uncertain_players=?,coverage_json=? where id=?",totalPages,observations.size(),observations.rawRows,observations.duplicates,observations.uncertain(),mapper.writeValueAsString(progress),run);
            }
            if(nextPage<=maxPages)pending.addLast(submitPage(capture,source,nextPage++));
          }
        }finally{for(var future:pending)future.cancel(true);}
        if(!ended)throw new IllegalStateException("Page safety limit reached in "+source);
        coverage.put(source,Map.of("pages",sourcePages,"uniquePlayers",sourceIds.size(),"durationSeconds",(System.nanoTime()-sourceStarted)/1_000_000_000.0));
      }
      if(regionalFallback&&!reconcile)observations.supplement(cachedRegionalRows());
      coverage.put("carriedForwardPlayers",observations.carried());coverage.put("regionalReconciliation",reconcile);
      if(live!=null){coverage.put("liveCaptureDirectory",live.directory());coverage.put("reusedPages",live.reusedPages());coverage.put("captureStartedAt",live.started().toString());}
      coverage.put("durationSeconds",(System.nanoTime()-collectionStarted)/1_000_000_000.0);
      coverage.put("concurrency",Math.max(1,Math.min(10,concurrency)));
      coverage.put("requestIntervalMillis",client.requestIntervalMillis());
      coverage.put("requestMetrics",client.requestMetrics());
      if(!observations.regionCounts().keySet().containsAll(REGIONS.keySet()))throw new IllegalStateException("Global response did not include all six regions");
      coverage.put("regions",observations.regionCounts());
      coverage.put("unknownCountry",observations.rows().stream().filter(r->r.player.country()==null).count());
      coverage.put("verifiedCapture",capture!=null);
      if(capture!=null){coverage.put("observedFrom",capture.started().toString());coverage.put("observedThrough",capture.through());}
      coverage.put("notes","First observed statistics retained; conflicting observations flagged and excluded from deltas. Tier totals use the first observed tier. Regional ranks are only populated from regional sources.");
      String coverageJson=mapper.writeValueAsString(coverage);final int pages=totalPages;
      final OffsetDateTime reconciledAt=reconcile?OffsetDateTime.now(ZoneOffset.UTC):null;
      tx.executeWithoutResult(status->{
        if(reconcile){
          db.update("delete from regional_observation_cache where act=?",act);
          db.batchUpdate("insert into regional_observation_cache(act,region,player_key,display_name,country,regional_rank,rr,tier,wins,source_updated_at,rank_ambiguous,stats_ambiguous,observation_source,regional_rank_updated_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",regionalObservations.rows(),500,(ps,row)->{
            var p=row.player;ps.setString(1,act);ps.setString(2,p.region());ps.setString(3,row.key());ps.setString(4,p.key());ps.setString(5,p.country());ps.setInt(6,row.regionalRank);ps.setInt(7,p.rr());ps.setString(8,p.tier());ps.setInt(9,p.wins());ps.setObject(10,row.updated);ps.setBoolean(11,row.rankAmbiguous);ps.setBoolean(12,row.statsAmbiguous);ps.setString(13,row.observationSource);ps.setObject(14,row.regionalRankUpdated);
          });
        }
        db.batchUpdate("insert into global_leaderboard_players(run_id,region,player_key,display_name,country,global_rank,regional_rank,rr,tier,wins,source_updated_at,rank_ambiguous,stats_ambiguous,carried_forward,observation_source,regional_rank_updated_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",observations.rows(),500,(ps,row)->{
          var p=row.player;ps.setLong(1,run);ps.setString(2,p.region());ps.setString(3,row.key());ps.setString(4,p.key());ps.setString(5,p.country());ps.setObject(6,row.globalRank);ps.setObject(7,row.regionalRank);ps.setInt(8,p.rr());ps.setString(9,p.tier());ps.setInt(10,p.wins());ps.setObject(11,row.updated);ps.setBoolean(12,row.rankAmbiguous);ps.setBoolean(13,row.statsAmbiguous);ps.setBoolean(14,row.carriedForward);ps.setString(15,row.observationSource);ps.setObject(16,row.regionalRankUpdated);
        });
        db.update("update global_leaderboard_runs set status='COMPLETE',completed_at=?,pages=?,players=?,raw_rows=?,duplicate_rows=?,uncertain_players=?,coverage_json=?,regional_reconciled_at=? where id=?",OffsetDateTime.now(ZoneOffset.UTC),pages,observations.size(),observations.rawRows,observations.duplicates,observations.uncertain(),coverageJson,reconciledAt,run);
      });bootstrapDirectory="";pendingCapture=null;log.info("Global snapshot {} published: {} players, {} pages, {} uncertain",run,observations.size(),pages,observations.uncertain());
    }catch(Exception error){if(error instanceof InterruptedException)Thread.currentThread().interrupt();lastError=Objects.toString(error.getMessage(),error.getClass().getSimpleName());db.update("update global_leaderboard_runs set status='FAILED',completed_at=?,pages=?,players=?,raw_rows=?,duplicate_rows=?,uncertain_players=?,error=? where id=?",OffsetDateTime.now(ZoneOffset.UTC),totalPages,observations.size(),observations.rawRows,observations.duplicates,observations.uncertain(),lastError.substring(0,Math.min(500,lastError.length())),run);if(lastError.contains("Five consecutive full pages made no identity progress")){pendingCapture=null;log.warn("Discarding failed capture from retry reuse; files retained for diagnosis");}log.warn("Global snapshot {} rejected",run,error);}
  }
  static boolean regionalDueAt(OffsetDateTime last,Instant now,ZoneId zone){
    var slot=now.atZone(zone).withMinute(0).withSecond(0).withNano(0).toInstant();
    return last==null||last.toInstant().isBefore(slot);
  }
  private OffsetDateTime lastReconciliation(){return db.queryForObject("select max(regional_reconciled_at) from global_leaderboard_runs where act=? and status='COMPLETE'",OffsetDateTime.class,act);}
  private boolean regionalDue(){return regionalDueAt(lastReconciliation(),Instant.now(),ZoneId.of(scheduleZone));}
  private List<GlobalObservations.Row> cachedRegionalRows(){return db.query("select * from regional_observation_cache where act=?",(rs,n)->{
    var p=new TrackerLeaderboardClient.Player(rs.getString("display_name"),rs.getString("country"),rs.getInt("regional_rank"),rs.getInt("rr"),rs.getString("tier"),rs.getInt("wins"),rs.getString("region"));
    var row=new GlobalObservations.Row(p,rs.getObject("source_updated_at",OffsetDateTime.class),p.region());row.rankAmbiguous=rs.getBoolean("rank_ambiguous");row.statsAmbiguous=rs.getBoolean("stats_ambiguous");row.observationSource=rs.getString("observation_source");row.regionalRankUpdated=rs.getObject("regional_rank_updated_at",OffsetDateTime.class);return row;
  },act);}
  private Future<TrackerLeaderboardClient.Page> submitPage(VerifiedCapture capture,String source,int page){
    return fetchers.submit(()->{try{return capture!=null?capture.page(source,page):pendingCapture!=null?pendingCapture.page(source,page,()->client.fetchRegion(act,source,page)):client.fetchRegion(act,source,page);}catch(Exception error){throw new IllegalStateException("Source "+source+" page "+page+": "+error.getMessage(),error);}});
  }
  private Map<String,Object> params(String region,String country){var p=new HashMap<String,Object>();p.put("act",act);p.put("region",region);p.put("country",country);return p;}
  public Map<String,Object> status(){
    return status(false);
  }
  public Map<String,Object> status(boolean compact){
    var timer=scheduleFuture;boolean timerActive=enabled&&timer!=null&&!timer.isDone()&&!timer.isCancelled();
    var schedule=new LinkedHashMap<String,Object>();schedule.put("scheduled",timerActive);schedule.put("intervalMillis",Math.max(1000,collectionInterval));schedule.put("nextRunAt",timerActive?Instant.now().plusMillis(Math.max(0,timer.getDelay(TimeUnit.MILLISECONDS))):null);schedule.put("lastTriggerAt",lastScheduledTriggerAt);schedule.put("lastTriggerAccepted",lastScheduledAccepted);schedule.put("pendingRun",pendingScheduledRun);schedule.put("lastTriggerOutcome",lastScheduledOutcome);schedule.put("overlapPolicy","coalesce one pending run; start immediately after current extraction");
    var progress=new LinkedHashMap<String,Object>();progress.put("startedAt",collectionStartedAt);progress.put("source",activeSource);progress.put("sourcePage",activePage);progress.put("pagesFetched",pagesFetched);progress.put("lastProgressAt",lastProgressAt);
    var result=new LinkedHashMap<String,Object>();result.put("act",act);result.put("region","global");result.put("enabled",enabled);result.put("paused",pauseAllExtraction);result.put("running",running.get());result.put("lastError",lastError);result.put("regions",REGIONS);result.put("countries",countryNames);result.put("regionalFallback",regionalFallback);result.put("globalIntervalMinutes",20);result.put("regionalIntervalHours",1);result.put("scheduleZone",scheduleZone);result.put("lastRegionalReconciliation",lastReconciliation());result.put("regionalReconciliationDue",regionalFallback&&regionalDue());result.put("bootstrapPending",!bootstrapDirectory.isBlank());result.put("concurrency",Math.max(1,Math.min(10,concurrency)));result.put("requestIntervalMillis",client.requestIntervalMillis());result.put("requestMetrics",client.requestMetrics());result.put("liveCaptureDirectory",pendingCapture==null?null:pendingCapture.directory());
    result.put("schedule",schedule);result.put("liveProgress",progress);
    if(compact)result.remove("countries");
    result.put("runs",db.queryForList(compact?"select id,status,started_at,completed_at,pages,error,source from global_leaderboard_runs where act=? order by id desc limit 1":"select * from global_leaderboard_runs where act=? order by id desc limit 10",act));
    result.put("latestSnapshot",db.queryForList(compact?"select id,act,status,started_at,completed_at,pages,players from global_leaderboard_runs where act=? and status='COMPLETE' order by id desc limit 1":"select * from global_leaderboard_runs where act=? and status='COMPLETE' order by id desc limit 1",act));return result;
  }
  public List<Map<String,Object>> comparison(String region,String country){return sql.queryForList("""
    with world as (select * from global_leaderboard_players where (:region='all' or region=:region) and run_id=
    """+LATEST+"""
    ), thresholds as (
      select region,min(rr) filter(where tier='Radiant' and not stats_ambiguous and not carried_forward) radiant_threshold,
        max(rr) filter(where tier='Radiant' and not stats_ambiguous and not carried_forward) top_rr from world group by region
    ), ranked_radiants as (
      select region,player_key,row_number() over(partition by region order by rr desc,player_key) radiant_position,
        count(*) over(partition by region) radiant_population
      from world where tier='Radiant' and not stats_ambiguous and not carried_forward
    ), bands as (
      select w.*,t.radiant_threshold,t.top_rr,r.radiant_position,r.radiant_population,
        greatest(300,t.radiant_threshold) cutoff,
        greatest(300,t.radiant_threshold,ceil(t.top_rr*0.5)) midpoint,
        greatest(300,t.radiant_threshold,ceil(t.top_rr*0.5),ceil(t.top_rr*0.9)) highpoint
      from world w join thresholds t using(region) left join ranked_radiants r using(region,player_key)
    )
    select coalesce(p.country,'unknown') country,count(*) players,count(*) filter(where p.carried_forward) carried_forward,
      count(*) filter(where p.tier='Radiant') radiant,
      count(*) filter(where p.tier='Radiant' and not p.stats_ambiguous) radiant_confirmed,
      count(*) filter(where p.tier like 'Immortal%') immortal,
      count(*) filter(where p.tier='Immortal 3') immortal3,
      count(*) filter(where p.tier='Immortal 2') immortal2,
      count(*) filter(where p.tier='Immortal 1') immortal1,
      count(*) filter(where p.stats_ambiguous or p.rank_ambiguous) uncertain,
      avg(p.rr) filter(where not p.stats_ambiguous) avg_rr,
      percentile_cont(0.5) within group (order by p.rr) filter(where not p.stats_ambiguous) median_rr,
      count(*) filter(where not p.stats_ambiguous and p.rr>=0 and p.rr<100) rr_0_100,
      count(*) filter(where not p.stats_ambiguous and p.rr>=100 and p.rr<300) rr_100_300,
      count(*) filter(where not p.stats_ambiguous and p.radiant_threshold is not null and p.rr>=300 and p.rr<p.cutoff) rr_300_cutoff,
      count(*) filter(where not p.stats_ambiguous and p.radiant_threshold is not null and p.rr>=p.cutoff and p.rr<p.midpoint) rr_cutoff_half,
      count(*) filter(where not p.stats_ambiguous and p.radiant_threshold is not null and p.rr>=p.midpoint and p.rr<p.highpoint) rr_half_ninety,
      count(*) filter(where not p.stats_ambiguous and p.radiant_threshold is not null and p.rr>=p.highpoint) rr_top_ten,
      count(*) filter(where not p.stats_ambiguous and not p.carried_forward and p.tier like 'Immortal%' and p.rr>=p.radiant_threshold-50 and p.rr<=p.radiant_threshold) near_radiant,
      count(*) filter(where not p.stats_ambiguous and not p.carried_forward and p.tier='Radiant' and p.rr<p.radiant_threshold+50) radiant_boundary,
      count(*) filter(where p.radiant_position<=ceil(p.radiant_population*0.5)) radiant_top50,
      count(*) filter(where p.radiant_position<=ceil(p.radiant_population*0.1)) radiant_top10,
      min(p.radiant_threshold) radiant_threshold,min(p.top_rr) top_rr,
      min(p.cutoff) cutoff,min(p.midpoint) midpoint,min(p.highpoint) highpoint
    from bands p where
    """+FILTER+" group by p.country order by players desc,country",params(region,country));}
  public List<Map<String,Object>> regions(String country){return sql.queryForList("select p.region,(select q.display_name from global_leaderboard_players q where q.run_id="+LATEST+" and q.region=p.region and not q.stats_ambiguous and not q.rank_ambiguous order by q.regional_rank asc nulls last,q.rr desc,q.player_key limit 1) top_player,(select q.regional_rank from global_leaderboard_players q where q.run_id="+LATEST+" and q.region=p.region and not q.stats_ambiguous and not q.rank_ambiguous order by q.regional_rank asc nulls last,q.rr desc,q.player_key limit 1) top_rank,(select q.rr from global_leaderboard_players q where q.run_id="+LATEST+" and q.region=p.region and not q.stats_ambiguous and not q.rank_ambiguous order by q.regional_rank asc nulls last,q.rr desc,q.player_key limit 1) top_rr,count(*) players,count(*) filter(where p.carried_forward) carried_forward,count(*) filter(where p.tier='Radiant') radiant,count(*) filter(where p.tier='Radiant' and not p.stats_ambiguous) radiant_confirmed,count(*) filter(where p.tier='Immortal 3') immortal3,count(*) filter(where p.tier='Immortal 2') immortal2,count(*) filter(where p.tier='Immortal 1') immortal1,count(*) filter(where p.country is null) unknown_country,count(*) filter(where p.stats_ambiguous) uncertain from global_leaderboard_players p where p.run_id="+LATEST+" and (:country='all' or coalesce(p.country,'unknown')=:country) group by p.region order by p.region",params("all",country));}
  public Map<String,Object> insights(String region,String country){
    String scope="""
      with recent as (select id from global_leaderboard_runs where act=:act and status='COMPLETE' order by id desc limit 2),
      current_rows as (select p.* from global_leaderboard_players p where p.run_id=(select max(id) from recent) and
      """+FILTER+"""
      ), previous_rows as (select p.* from global_leaderboard_players p where p.run_id=(select min(id) from recent having count(*)=2) and
      """+FILTER+"""
      )
      """;
    var p=params(region,country);
    var summary=sql.queryForMap(scope+"""
      select (select count(*) from recent)=2 has_previous,
      (select count(*) from current_rows) players,(select count(*) from previous_rows) previous_players,
      (select count(*) from current_rows c where not c.carried_forward and not exists(select 1 from previous_rows o where o.region=c.region and o.player_key=c.player_key)) entered,
      (select count(*) from previous_rows o where not exists(select 1 from current_rows c where o.region=c.region and o.player_key=c.player_key)) exited,
      (select count(*) from current_rows where stats_ambiguous or rank_ambiguous) uncertain,
      (select count(*) from current_rows where global_rank is null) regional_only,
      (select count(*) from current_rows where carried_forward) carried_forward,
      (select count(*) from current_rows where country is null) unknown_country
      """,p);
    var movers=sql.queryForList(scope+"""
      select c.display_name,c.player_key,c.region,c.rr,c.rr-o.rr rr_change from current_rows c
      join previous_rows o using(region,player_key)
      where not c.carried_forward and not o.carried_forward and not c.stats_ambiguous and not o.stats_ambiguous and c.rr<>o.rr
      order by abs(c.rr-o.rr) desc,c.region,c.player_key limit 10
      """,p);
    return Map.of("summary",summary,"movers",movers);
  }
  public Map<String,Object> trends(String region,String country,int minutes,String direction){
    var p=params(region,country);p.put("minutes",minutes);p.put("direction",direction);
    String selected="""
      with latest as (select max(completed_at) stamp from global_leaderboard_runs where act=:act and status='COMPLETE'),
      selected as (select id,completed_at from global_leaderboard_runs where act=:act and status='COMPLETE'
        and completed_at>=(select stamp from latest)-(:minutes * interval '1 minute'))
      """;
    var points=sql.queryForList(selected+"""
      select r.completed_at,count(p.player_key) players,
        count(p.player_key) filter(where p.tier='Radiant') radiant,
        count(p.player_key) filter(where p.tier='Immortal 3') immortal3,
        count(p.player_key) filter(where p.tier='Immortal 2') immortal2,
        count(p.player_key) filter(where p.tier='Immortal 1') immortal1,
        count(p.player_key) filter(where p.carried_forward) carried_forward
      from selected r left join lateral (select p.* from global_leaderboard_players p where p.run_id=r.id and
      """+FILTER+" offset 0) p on true group by r.id,r.completed_at order by r.completed_at",p);
    var regionPoints=sql.queryForList(selected+"""
      select r.completed_at,p.region,count(p.player_key) players from selected r
      join lateral (select p.* from global_leaderboard_players p where p.run_id=r.id and
      """+FILTER+" offset 0) p on true group by r.id,r.completed_at,p.region order by r.completed_at,p.region",p);
    var movers=sql.queryForList(selected+"""
      ,current_rows as materialized (
        select c.* from global_leaderboard_players c
        where c.run_id=(select id from selected order by completed_at desc,id desc limit 1)
          and not c.carried_forward and not c.stats_ambiguous
          and (:region='all' or c.region=:region) and (:country='all' or coalesce(c.country,'unknown')=:country)
      ),baseline_rows as (
        select distinct on(prior.region,prior.player_key) prior.region,prior.player_key,prior.rr,prior.source_updated_at,prior.carried_forward,prior.stats_ambiguous
        from selected baseline cross join lateral (
          select prior.* from global_leaderboard_players prior where prior.run_id=baseline.id
            and (:region='all' or prior.region=:region)
            and not prior.carried_forward and not prior.stats_ambiguous
            and prior.source_updated_at>=(select stamp from latest)-(:minutes * interval '1 minute') offset 0
        ) prior join current_rows target on target.region=prior.region and target.player_key=prior.player_key
        where baseline.id<>(select id from selected order by completed_at desc,id desc limit 1)
        order by prior.region,prior.player_key,baseline.completed_at,baseline.id
      )
      select c.player_key,c.display_name,c.region,c.country,o.rr previous_rr,c.rr current_rr,c.rr-o.rr rr_change,
        o.source_updated_at previous_observed_at,c.source_updated_at current_observed_at
      from current_rows c join baseline_rows o on o.region=c.region and o.player_key=c.player_key
      where c.run_id=(select id from selected order by completed_at desc,id desc limit 1)
        and (select count(*) from selected)>1 and not c.carried_forward and not o.carried_forward
        and not c.stats_ambiguous and not o.stats_ambiguous and c.source_updated_at>o.source_updated_at
        and c.rr<>o.rr and (:direction='overall' or (:direction='increment' and c.rr>o.rr) or (:direction='decrement' and c.rr<o.rr))
        and (:region='all' or c.region=:region) and (:country='all' or coalesce(c.country,'unknown')=:country)
      order by abs(c.rr-o.rr) desc,c.region,c.player_key limit 20
      """,p);
    return Map.of("points",points,"regionPoints",regionPoints,"movers",movers,"windowMinutes",minutes,"direction",direction,"snapshotCount",points.size());
  }
  public Map<String,Object> players(String region,String country,String search,int page,int size){
    var p=params(region,country);p.put("search",search.toLowerCase(Locale.ROOT));p.put("pattern","%"+search.toLowerCase(Locale.ROOT)+"%");p.put("limit",size);p.put("offset",(page-1)*size);
    String match=FILTER+" and (:search='' or lower(p.display_name) like :pattern) ";
    Long total=sql.queryForObject("select count(*) from global_leaderboard_players p where p.run_id="+LATEST+" and "+match,p,Long.class);
    var rows=sql.queryForList("""
      with recent as (select id from global_leaderboard_runs where act=:act and status='COMPLETE' order by id desc limit 2),
      previous as (select min(id) id from recent having count(*)=2)
      select p.*,old.rr previous_rr,
      case when p.carried_forward or old.carried_forward or p.stats_ambiguous or old.stats_ambiguous then null else p.rr-old.rr end rr_change,
      case when p.carried_forward or old.carried_forward or p.rank_ambiguous or old.rank_ambiguous or p.stats_ambiguous or old.stats_ambiguous then null else old.global_rank-p.global_rank end rank_gain,
      case when p.carried_forward or old.carried_forward or p.regional_rank_updated_at is null or old.regional_rank_updated_at is null or p.regional_rank_updated_at is not distinct from old.regional_rank_updated_at or p.rank_ambiguous or old.rank_ambiguous or p.stats_ambiguous or old.stats_ambiguous then null else old.regional_rank-p.regional_rank end regional_rank_gain,
      case when p.carried_forward then 'CARRIED_FORWARD' when p.stats_ambiguous then 'UNCERTAIN' when old.player_key is not null then 'RETURNING' when (select id from previous) is null then 'BASELINE' else 'ENTERED' end movement
      from global_leaderboard_players p left join global_leaderboard_players old on old.run_id=(select id from previous) and old.region=p.region and old.player_key=p.player_key
      where p.run_id=
      """+LATEST+" and "+match+" order by p.global_rank nulls last,p.regional_rank nulls last,p.region,p.player_key limit :limit offset :offset",p);
    return Map.of("items",rows,"total",total,"page",page,"size",size);
  }
  public Map<String,Object> playerSummary(String player,String region){
    var p=params(region,"all");p.put("player",player.toLowerCase(Locale.ROOT));
    var current=sql.queryForList("select p.* from global_leaderboard_players p where p.run_id="+LATEST+" and p.region=:region and p.player_key=:player",p);
    var best=sql.queryForList("""
      select max(p.rr) filter(where not p.stats_ambiguous) best_rr,
        min(p.global_rank) filter(where not p.rank_ambiguous and not p.stats_ambiguous and p.global_rank>0) best_global_rank,
        min(p.regional_rank) filter(where not p.rank_ambiguous and not p.stats_ambiguous and p.regional_rank>0) best_regional_rank,
        min(p.source_updated_at) first_observed_at,max(p.source_updated_at) last_observed_at,
        count(distinct p.source_updated_at) observation_count
      from global_leaderboard_players p join global_leaderboard_runs r on r.id=p.run_id
      where r.act=:act and r.status='COMPLETE' and p.region=:region and p.player_key=:player and not p.carried_forward
      """,p).getFirst();
    var peak=sql.queryForList("""
      select p.tier,p.rr,p.source_updated_at from global_leaderboard_players p join global_leaderboard_runs r on r.id=p.run_id
      where r.act=:act and r.status='COMPLETE' and p.region=:region and p.player_key=:player and not p.carried_forward and not p.stats_ambiguous
      order by case p.tier when 'Radiant' then 4 when 'Immortal 3' then 3 when 'Immortal 2' then 2 when 'Immortal 1' then 1 else 0 end desc,p.rr desc,p.source_updated_at desc limit 1
      """,p);
    return Map.of("current",current,"best",best,"peak",peak,"act",act,"scope","Retained complete snapshots in the current act; not all-time account peak");
  }
  public List<Map<String,Object>> history(String player,String region){return db.queryForList("""
      select * from (
      select r.completed_at,p.source_updated_at observed_at,p.observation_source,p.regional_rank_updated_at,p.region,p.display_name,p.country,p.global_rank,p.regional_rank,p.rr,p.tier,p.wins,p.source_updated_at,p.rank_ambiguous,p.stats_ambiguous,r.source
      from global_leaderboard_players p join global_leaderboard_runs r on p.run_id=r.id where r.act=? and r.status='COMPLETE' and p.player_key=? and p.region=? and not p.carried_forward
      union all
      select r.completed_at,p.source_updated_at observed_at,'legacy AP' observation_source,p.source_updated_at regional_rank_updated_at,'ap' region,p.player_key display_name,p.country,null global_rank,p.regional_rank,p.rr,p.tier,p.wins,p.source_updated_at,p.rank_ambiguous,false stats_ambiguous,'legacy AP country' source
      from leaderboard_players p join leaderboard_runs r on p.run_id=r.id where r.act=? and r.status='COMPLETE' and lower(p.player_key)=? and ?='ap'
      ) h order by completed_at desc limit 1000
      """,act,player.toLowerCase(Locale.ROOT),region,act,player.toLowerCase(Locale.ROOT),region);}
  public List<Map<String,Object>> countryHistory(String region,String country){return sql.queryForList("select r.completed_at,count(p.player_key) players,count(p.player_key) filter(where p.carried_forward) carried_forward from global_leaderboard_runs r left join global_leaderboard_players p on p.run_id=r.id and "+FILTER+" where r.act=:act and r.status='COMPLETE' group by r.id,r.completed_at order by r.id desc limit 1000",params(region,country));}
  @PreDestroy public void close(){synchronized(scheduleGuard){closing=true;pendingScheduledRun=false;}collectionScheduler.shutdownNow();worker.shutdownNow();fetchers.shutdownNow();}
}
