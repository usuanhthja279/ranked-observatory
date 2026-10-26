package com.valorant.leaderboard;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable, low-priority one-time archive of Tracker's completed PC ranked acts. */
@Service
public class HistoricalActArchiveService {
  private static final Logger log=LoggerFactory.getLogger(HistoricalActArchiveService.class);
  private static final List<String> SOURCES=List.of("global","ap","eu","na","latam","br","kr");
  private final JdbcTemplate db;
  private final TrackerLeaderboardClient client;
  private final GlobalLeaderboardService liveLeaderboard;
  private final ObjectMapper mapper;
  private final TransactionTemplate tx;
  private final String currentAct;
  private final Map<String,String> countries;
  private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"historical-act-archive");t.setDaemon(true);return t;});
  private final ExecutorService pageFetchers=Executors.newFixedThreadPool(PAGE_CONCURRENCY,r->{var t=new Thread(r,"historical-act-page");t.setDaemon(true);return t;});
  private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"historical-act-archive-resume");t.setDaemon(true);return t;});
  private final AtomicBoolean running=new AtomicBoolean();
  private static final int PAGE_CONCURRENCY=5;
  @Value("${leaderboard.pause-all-extraction:false}") private boolean pauseAllExtraction;
  @Value("${leaderboard.historical.max-pages:10000}") private int maxPages=10000;

  public HistoricalActArchiveService(JdbcTemplate db,TrackerLeaderboardClient client,GlobalLeaderboardService liveLeaderboard,ObjectMapper mapper,PlatformTransactionManager tm,
      @Value("${leaderboard.act:d816f426-48ea-f052-117f-9697a155b319}") String currentAct){
    this.db=db;this.client=client;this.liveLeaderboard=liveLeaderboard;this.mapper=mapper;this.currentAct=currentAct;this.tx=new TransactionTemplate(tm);
    try{this.countries=mapper.readValue(new ClassPathResource("global-countries.json").getInputStream(),new TypeReference<LinkedHashMap<String,String>>(){});this.countries.put("kr","South Korea");this.countries.put("unknown","Unknown country");}
    catch(Exception e){throw new IllegalStateException("Unable to load country labels",e);}
  }

  @EventListener(ApplicationReadyEvent.class)
  public void start(){
    try{seedCatalog();db.update("update historical_act_sources set status='PENDING',error=null where status='RUNNING'");}
    catch(Exception e){log.error("Historical act archive could not initialize; it will remain resumable on next startup",e);return;}
    // Delay the first archive request so the current-act extraction can establish its live queue first.
    scheduler.scheduleWithFixedDelay(this::scheduleWork,30,60,TimeUnit.SECONDS);
  }

  private void seedCatalog() throws Exception {
    var acts=mapper.readValue(new ClassPathResource("historical-acts.json").getInputStream(),new TypeReference<List<Map<String,String>>>(){});
    int order=0;
    for(var act:acts){order++;db.update("insert into historical_act_catalog(act,label,episode,act_order,current_act) values(?,?,?,?,false) on conflict(act) do update set label=excluded.label,episode=excluded.episode,act_order=excluded.act_order",act.get("id"),act.get("label"),act.get("episode"),order);}
    db.update("insert into historical_act_catalog(act,label,episode,act_order,current_act) values(?,?,?,0,true) on conflict(act) do update set current_act=true",currentAct,"Current act","Current");
    for(var act:db.queryForList("select act from historical_act_catalog where current_act=false"))for(String source:SOURCES)
      db.update("insert into historical_act_sources(act,source_region) values(?,?) on conflict do nothing",act.get("act"),source);
  }

  private void scheduleWork(){if(pauseAllExtraction||!running.compareAndSet(false,true))return;try{worker.execute(()->{try{drain();}catch(Exception e){log.warn("Historical archive worker paused; page checkpoint retained",e);}finally{running.set(false);}});}catch(RejectedExecutionException e){running.set(false);}}

  private void drain(){
    var acts=db.queryForList("select act,label from historical_act_catalog where current_act=false order by act_order desc");
    for(var row:acts){String act=row.get("act").toString();
      for(String source:SOURCES){
        if(Thread.currentThread().isInterrupted())return;
        var state=db.queryForMap("select status,next_page,retry_after from historical_act_sources where act=? and source_region=?",act,source);
        if("COMPLETE".equals(state.get("status")))continue;
        Object retry=state.get("retry_after");if(retry instanceof OffsetDateTime t&&t.isAfter(OffsetDateTime.now(ZoneOffset.UTC)))continue;
        fetchSource(act,source,((Number)state.get("next_page")).intValue());
      }
    }
    Long incomplete=db.queryForObject("select count(*) from historical_act_sources s join historical_act_catalog c using(act) where c.current_act=false and s.status<>'COMPLETE'",Long.class);
    if(incomplete!=null&&incomplete==0)liveLeaderboard.enableAfterHistoricalArchive();
  }

  private void fetchSource(String act,String source,int page){
    db.update("update historical_act_sources set status='RUNNING',started_at=coalesce(started_at,now()),attempts=attempts+1,error=null where act=? and source_region=?",act,source);
    var pending=new ArrayDeque<PageRequest>();int nextPage=page;
    while(nextPage<=maxPages&&pending.size()<PAGE_CONCURRENCY)pending.addLast(submitHistoricalPage(act,source,nextPage++));
    while(!pending.isEmpty()){
      var request=pending.removeFirst();int pageNumber=request.page();
      try{
        var result=request.result().get();var now=OffsetDateTime.now(ZoneOffset.UTC);
        tx.executeWithoutResult(transaction->{
          db.batchUpdate("insert into historical_act_players(act,source_region,player_region,player_key,display_name,country,source_rank,rr,tier,wins,source_updated_at,fetched_at) values(?,?,?,?,?,?,?,?,?,?,?,?) on conflict(act,source_region,player_region,player_key) do update set display_name=excluded.display_name,country=excluded.country,source_rank=excluded.source_rank,rr=excluded.rr,tier=excluded.tier,wins=excluded.wins,source_updated_at=excluded.source_updated_at,fetched_at=excluded.fetched_at",result.players(),500,(ps,player)->{ps.setString(1,act);ps.setString(2,source);ps.setString(3,player.region());ps.setString(4,player.key().toLowerCase(Locale.ROOT));ps.setString(5,player.key());ps.setString(6,player.country());ps.setInt(7,player.rank());ps.setInt(8,player.rr());ps.setString(9,player.tier());ps.setInt(10,player.wins());ps.setObject(11,result.updatedAt());ps.setObject(12,now);});
          db.update("insert into historical_act_pages(act,source_region,page,status,rows,source_updated_at,fetched_at) values(?,?,?,'COMPLETE',?,?,?) on conflict(act,source_region,page) do update set status='COMPLETE',rows=excluded.rows,source_updated_at=excluded.source_updated_at,fetched_at=excluded.fetched_at,error=null",act,source,pageNumber,result.players().size(),result.updatedAt(),now);
          if(result.players().size()<100){db.update("update historical_act_sources set status='COMPLETE',next_page=?,pages=?,players=(select count(*) from historical_act_players where act=? and source_region=?),source_updated_at=?,completed_at=?,retry_after=null,error=null where act=? and source_region=?",pageNumber+1,pageNumber,act,source,result.updatedAt(),now,act,source);return;}
          db.update("update historical_act_sources set status='RUNNING',next_page=?,pages=?,players=(select count(*) from historical_act_players where act=? and source_region=?),source_updated_at=?,retry_after=null,error=null where act=? and source_region=?",pageNumber+1,pageNumber,act,source,result.updatedAt(),act,source);
        });
        if(result.players().size()<100){cancelPending(pending);log.info("Archived act {} source {} through page {}",act,source,pageNumber);return;}
        if(pageNumber%25==0){var estimate=completionEstimate();log.info("Historical archive act={} source={} completedPage={} rowsOnPage={} estimatedRemainingPages={} eta={} basedOnPagesPerMinute={}",act,source,pageNumber,result.players().size(),estimate.get("remainingPages"),estimate.get("etaFormatted"),estimate.get("pagesPerMinute"));}
        if(nextPage<=maxPages)pending.addLast(submitHistoricalPage(act,source,nextPage++));
      }catch(InterruptedException e){cancelPending(pending);Thread.currentThread().interrupt();pause(act,source,pageNumber,"Worker stopped; checkpoint retained",Duration.ZERO);return;}
      catch(Exception e){cancelPending(pending);Throwable cause=e instanceof ExecutionException&&e.getCause()!=null?e.getCause():e;pause(act,source,pageNumber,cause.getMessage(),Duration.ofMinutes(15));log.warn("Historical archive paused at act={} source={} page={}: {}",act,source,pageNumber,cause.getMessage());return;}
    }
    pause(act,source,maxPages,"Safety cap reached; source is incomplete",Duration.ofHours(6));
  }

  private PageRequest submitHistoricalPage(String act,String source,int page){
    return new PageRequest(page,pageFetchers.submit(()->client.fetchHistoricalRegion(act,source,page)));
  }
  private void cancelPending(Deque<PageRequest> pending){while(!pending.isEmpty())pending.removeFirst().result().cancel(true);}
  private record PageRequest(int page,Future<TrackerLeaderboardClient.Page> result){}

  private Map<String,Object> completionEstimate(){
    var acts=db.queryForMap("""
      select count(*) filter(where done) complete_acts,count(*) total_acts,
       coalesce(sum(pages) filter(where done),0) completed_act_pages,
       coalesce(sum(pages),0) saved_pages
      from (select c.act,count(s.source_region)=7 and bool_and(s.status='COMPLETE') done,coalesce(sum(s.pages),0) pages
       from historical_act_catalog c left join historical_act_sources s using(act)
       where c.current_act=false group by c.act) a
      """);
    int completedActs=((Number)acts.get("complete_acts")).intValue(),totalActs=((Number)acts.get("total_acts")).intValue();
    long savedPages=((Number)acts.get("saved_pages")).longValue(),remainingPages=0;
    double pagesPerMinute=0;Long etaSeconds=null;
    if(completedActs>0&&totalActs>0){double average=(double)((Number)acts.get("completed_act_pages")).longValue()/completedActs;remainingPages=Math.max(0,(long)Math.ceil(average*totalActs)-savedPages);}
    var rate=db.queryForMap("select count(*) pages,extract(epoch from(now()-min(fetched_at))) elapsed_seconds from historical_act_pages where status='COMPLETE' and fetched_at>=now()-interval '5 minutes'");
    long recentPages=((Number)rate.get("pages")).longValue();
    Object elapsedValue=rate.get("elapsed_seconds");
    // PostgreSQL returns NULL for min(fetched_at) when no archive page was fetched
    // during the rolling window (for example immediately after a restart).
    double elapsedSeconds=elapsedSeconds(elapsedValue);
    if(recentPages>0)pagesPerMinute=recentPages/(elapsedSeconds/60.0);
    if(completedActs>0&&pagesPerMinute>0)etaSeconds=(long)Math.ceil(remainingPages*60.0/pagesPerMinute);
    var estimate=new LinkedHashMap<String,Object>();estimate.put("completedActs",completedActs);estimate.put("totalActs",totalActs);estimate.put("savedPages",savedPages);estimate.put("remainingPages",completedActs>0?remainingPages:null);estimate.put("pagesPerMinute",Math.round(pagesPerMinute*10.0)/10.0);estimate.put("etaSeconds",etaSeconds);estimate.put("etaFormatted",etaSeconds==null?"Estimating":"%d min %02d sec".formatted(etaSeconds/60,etaSeconds%60));estimate.put("basis","mean pages per completed act × total acts, minus saved pages; rolling five-minute archive page rate");return estimate;
  }

  static double elapsedSeconds(Object elapsedValue){
    return elapsedValue instanceof Number elapsed?Math.max(60,elapsed.doubleValue()):0;
  }

  private void pause(String act,String source,int page,String error,Duration retry){
    String message=Objects.toString(error,"Source failure");message=message.substring(0,Math.min(500,message.length()));
    db.update("insert into historical_act_pages(act,source_region,page,status,error) values(?,?,?,'FAILED',?) on conflict(act,source_region,page) do update set status='FAILED',error=excluded.error,fetched_at=now()",act,source,page,message);
    db.update("update historical_act_sources set status='PAUSED',next_page=?,retry_after=?,error=? where act=? and source_region=?",page,OffsetDateTime.now(ZoneOffset.UTC).plus(retry),message,act,source);
  }

  public boolean isCurrent(String act){return currentAct.equals(act);}
  public boolean isKnown(String act){return Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from historical_act_catalog where act=?)",Boolean.class,act));}
  public List<Map<String,Object>> acts(){return db.queryForList("""
    select c.act,c.label,c.episode,c.act_order,c.current_act,
      (select count(*) from historical_act_sources s where s.act=c.act and s.status='COMPLETE') completed_sources,
      (select count(*) from historical_act_sources s where s.act=c.act) total_sources,
      coalesce((select sum(s.pages) from historical_act_sources s where s.act=c.act),0) pages,
      coalesce((select sum(s.players) from historical_act_sources s where s.act=c.act),0) source_rows,
      (select max(s.source_updated_at) from historical_act_sources s where s.act=c.act) source_updated_at,
      (select min(s.retry_after) from historical_act_sources s where s.act=c.act and s.status='PAUSED') next_retry_at
    from historical_act_catalog c order by c.current_act desc,c.act_order desc
    """);}
  public Map<String,Object> status(String act){
    if(isCurrent(act))return Map.of("archiveOnly",false,"act",act);
    var sources=db.queryForList("select source_region,status,next_page,pages,players,source_updated_at,completed_at,retry_after,error from historical_act_sources where act=? order by case when source_region='global' then 0 else 1 end,source_region",act);
    int completed=(int)sources.stream().filter(s->"COMPLETE".equals(s.get("status"))).count();
    var totalRows=db.queryForObject("select count(*) from historical_act_players where act=? and source_region='global'",Long.class,act);
    var latest=db.queryForObject("select max(fetched_at) from historical_act_pages where act=? and status='COMPLETE'",OffsetDateTime.class,act);
    var sourceTime=db.queryForObject("select max(source_updated_at) from historical_act_sources where act=?",OffsetDateTime.class,act);
    int pages=sources.stream().mapToInt(s->((Number)s.get("pages")).intValue()).sum();int sourceRows=sources.stream().mapToInt(s->((Number)s.get("players")).intValue()).sum();
    var progress=new LinkedHashMap<String,Object>();progress.put("completedSources",completed);progress.put("totalSources",7);progress.put("pages",pages);progress.put("sourceRows",sourceRows);progress.put("players",totalRows);progress.put("lastPageFetchedAt",latest==null?"":latest.toString());progress.put("sourceUpdatedAt",sourceTime==null?"":sourceTime.toString());progress.putAll(completionEstimate());
    var result=new LinkedHashMap<String,Object>();result.put("act",act);result.put("archiveOnly",true);result.put("enabled",false);result.put("paused",pauseAllExtraction);result.put("running",running.get());result.put("pageConcurrency",PAGE_CONCURRENCY);result.put("lastError",null);result.put("regions",GlobalLeaderboardService.REGIONS);result.put("countries",countries);result.put("schedule",Map.of("scheduled",!pauseAllExtraction,"paused",pauseAllExtraction,"intervalMillis",60000,"overlapPolicy","one low-priority archive worker; five page requests in flight with ordered durable commits; live requests have priority"));result.put("runs",sources);result.put("latestSnapshot",List.of(Map.of("id",act,"act",act,"status",completed==7?"COMPLETE":"PARTIAL","completed_at",latest==null?"":latest.toString(),"pages",pages,"players",totalRows)));result.put("archiveProgress",progress);return result;
  }

  public Map<String,Object> confidence(String act,String region,String country){
    String source=region.equals("all")?"global":region;
    var row=db.queryForMap("select count(*) players,count(*) filter(where country is not null) known_country,count(*) filter(where country is null) unknown_country,min(source_updated_at) source_from,max(source_updated_at) source_to from historical_act_players where act=? and source_region=? and (?='all' or player_region=?) and (?='all' or coalesce(country,'unknown')=?)",act,source,region,region,country,country);
    row.put("snapshot_id",act);row.put("archive_only",true);row.put("source_region",source);row.put("complete",Boolean.TRUE.equals(db.queryForObject("select status='COMPLETE' from historical_act_sources where act=? and source_region=?",Boolean.class,act,source)));return row;
  }

  public List<Map<String,Object>> countries(String act,String region,String country){
    String source=region.equals("all")?"global":region;
    var rows=db.queryForList("""
      select coalesce(p.country,'unknown') country,count(*) players,count(*) filter(where p.tier='Radiant') radiant,
       count(*) filter(where p.tier='Immortal') immortal,count(*) filter(where p.tier='Immortal') combined_immortal,
       count(*) filter(where p.tier in ('Immortal 1','Immortal 2','Immortal 3')) split_immortal,
       count(*) filter(where p.tier in ('Immortal','Immortal 1','Immortal 2','Immortal 3')) immortal_all,
       count(*) filter(where p.tier='Immortal 3') immortal3,
       count(*) filter(where p.tier='Immortal 2') immortal2,count(*) filter(where p.tier='Immortal 1') immortal1,
       count(*) filter(where p.tier='Immortal 3' or (p.tier='Immortal' and p.rr>=case when c.act_order>=32 then case when p.player_region in ('latam','kr') then 150 else 200 end else case when p.player_region='br' then 230 when p.player_region in ('latam','kr') then 150 else 200 end end)) estimated_immortal3,
       count(*) filter(where p.tier='Immortal 2' or (p.tier='Immortal' and p.rr>=case when c.act_order>=32 then case when p.player_region in ('latam','kr') then 90 else 100 end else case p.player_region when 'ap' then 80 when 'eu' then 100 when 'br' then 100 else 90 end end and p.rr<case when c.act_order>=32 then case when p.player_region in ('latam','kr') then 150 else 200 end else case when p.player_region='br' then 230 when p.player_region in ('latam','kr') then 150 else 200 end end)) estimated_immortal2,
       count(*) filter(where p.tier='Immortal 1' or (p.tier='Immortal' and p.rr<case when c.act_order>=32 then case when p.player_region in ('latam','kr') then 90 else 100 end else case p.player_region when 'ap' then 80 when 'eu' then 100 when 'br' then 100 else 90 end end)) estimated_immortal1,
       percentile_cont(0.5) within group(order by rr) median_rr,max(source_updated_at) source_updated_at,
       max(case when c.act_order>=32 then 'V26_ACT_II_PLUS' else 'EPISODE_3_BACKCAST' end) threshold_profile,
       case when count(*) filter(where p.tier='Immortal')>0 and count(*) filter(where p.tier in ('Immortal 1','Immortal 2','Immortal 3'))>0 then 'MIXED'
         when count(*) filter(where p.tier='Immortal')>0 then 'COMBINED_ONLY'
         when count(*) filter(where p.tier in ('Immortal 1','Immortal 2','Immortal 3'))>0 then 'SPLIT'
         else 'NO_IMMORTAL_ROWS' end tier_detail
      from historical_act_players p join historical_act_catalog c on c.act=p.act where p.act=? and p.source_region=? and (?='all' or p.player_region=?) and (?='all' or coalesce(p.country,'unknown')=?)
      group by coalesce(p.country,'unknown') order by players desc,country
      """,act,source,region,region,country,country);
    for(var row:rows){row.put("source_basis",source.equals("global")?"global_source_ladder":"regional_source_ladder");row.put("threshold_policy",source.equals("global")?"RR thresholds vary by player region; use regional archived tier coverage for exact cutoffs.":"RR thresholds use the selected regional source ladder.");row.put("tier_semantics","immortal is the legacy combined-only source label; immortal_all includes combined and source-reported Immortal 1/2/3; estimated subtiers split combined labels by RR.");}
    return rows;
  }

  public List<Map<String,Object>> regions(String act,String country){return db.queryForList("""
    with counts as(
      select p.player_region region,count(*) players,count(*) filter(where p.tier='Radiant') radiant,
       count(*) filter(where p.tier='Immortal') immortal,count(*) filter(where p.tier='Immortal') combined_immortal,
       count(*) filter(where p.tier in ('Immortal 1','Immortal 2','Immortal 3')) split_immortal,
       count(*) filter(where p.tier in ('Immortal','Immortal 1','Immortal 2','Immortal 3')) immortal_all,
       count(*) filter(where p.tier='Immortal 3') immortal3,count(*) filter(where p.tier='Immortal 2') immortal2,
       count(*) filter(where p.tier='Immortal 1') immortal1,
       max(case when c.act_order>=32 then 'V26_ACT_II_PLUS' else 'EPISODE_3_BACKCAST' end) threshold_profile,
       max(case when c.act_order>=32 then case when p.player_region in ('latam','kr') then 90 else 100 end else case p.player_region when 'ap' then 80 when 'eu' then 100 when 'br' then 100 else 90 end end) immortal2_threshold_rr,
       max(case when c.act_order>=32 then case when p.player_region in ('latam','kr') then 150 else 200 end else case when p.player_region='br' then 230 when p.player_region in ('latam','kr') then 150 else 200 end end) immortal3_threshold_rr,
       count(*) filter(where p.tier='Immortal 3' or (p.tier='Immortal' and p.rr>=case when c.act_order>=32 then case when p.player_region in ('latam','kr') then 150 else 200 end else case when p.player_region='br' then 230 when p.player_region in ('latam','kr') then 150 else 200 end end)) estimated_immortal3,
       count(*) filter(where p.tier='Immortal 2' or (p.tier='Immortal' and p.rr>=case when c.act_order>=32 then case when p.player_region in ('latam','kr') then 90 else 100 end else case p.player_region when 'ap' then 80 when 'eu' then 100 when 'br' then 100 else 90 end end and p.rr<case when c.act_order>=32 then case when p.player_region in ('latam','kr') then 150 else 200 end else case when p.player_region='br' then 230 when p.player_region in ('latam','kr') then 150 else 200 end end)) estimated_immortal2,
       count(*) filter(where p.tier='Immortal 1' or (p.tier='Immortal' and p.rr<case when c.act_order>=32 then case when p.player_region in ('latam','kr') then 90 else 100 end else case p.player_region when 'ap' then 80 when 'eu' then 100 when 'br' then 100 else 90 end end)) estimated_immortal1,
       count(*) filter(where p.country is null) unknown_country,
       case when count(*) filter(where p.tier='Immortal')>0 and count(*) filter(where p.tier in ('Immortal 1','Immortal 2','Immortal 3'))>0 then 'MIXED'
         when count(*) filter(where p.tier='Immortal')>0 then 'COMBINED_ONLY'
         when count(*) filter(where p.tier in ('Immortal 1','Immortal 2','Immortal 3'))>0 then 'SPLIT'
         else 'NO_IMMORTAL_ROWS' end tier_detail
      from historical_act_players p join historical_act_catalog c on c.act=p.act
      join historical_act_sources s on s.act=p.act and s.source_region=p.source_region and s.status='COMPLETE'
      where p.act=? and p.source_region=p.player_region and (?='all' or coalesce(p.country,'unknown')=?)
      group by p.player_region,c.act_order
    ), leaders as(
      select region,top_player,top_rank,top_rr from (
        select p.player_region region,p.player_key top_player,p.source_rank top_rank,p.rr top_rr,
          row_number() over(partition by p.player_region order by p.source_rank) row_num
        from historical_act_players p join historical_act_sources s on s.act=p.act and s.source_region=p.source_region and s.status='COMPLETE'
        where p.act=? and p.source_region=p.player_region and (?='all' or coalesce(p.country,'unknown')=?)
      ) ranked where row_num=1
    )
    select c.*,l.top_player,l.top_rank,l.top_rr,'completed_regional_source_ladders' source_basis,
      'regional RR thresholds are shown in the threshold columns.' threshold_policy,
      'immortal is the legacy combined-only label; immortal_all includes combined and reported subtiers; estimated subtiers allocate combined labels by RR.' tier_semantics
    from counts c left join leaders l using(region) order by c.region
    """,act,country,country,act,country,country);}

  public Map<String,Object> players(String act,String region,String country,String search,int page,int size){
    return players(act,region,country,search,page,size,"globalRank","asc");
  }
  public Map<String,Object> players(String act,String region,String country,String search,int page,int size,String sort,String direction){
    String source=region.equals("all")?"global":region;int offset=(page-1)*size;
    var params=new Object[]{act,source,region,region,country,country,"%"+search.toLowerCase(Locale.ROOT)+"%",size,offset};
    String column=switch(sort){case "name"->"p.display_name";case "country"->"p.country";case "regionalRank"->"case when p.source_region<>'global' then p.source_rank else r.source_rank end";case "tier"->"p.tier";case "rr"->"p.rr";case "updated"->"p.source_updated_at";default->"case when p.source_region='global' then p.source_rank else g.source_rank end";};
    String order=column+" "+(direction.equals("desc")?"desc":"asc")+" nulls last,global_rank nulls last,regional_rank nulls last,p.player_key";
    var items=db.queryForList("""
      select p.display_name,p.player_key,p.country,p.player_region region,
       case when p.source_region='global' then p.source_rank else g.source_rank end global_rank,
       case when p.source_region<>'global' then p.source_rank else r.source_rank end regional_rank,
       p.rr,p.tier,p.wins,p.source_updated_at,null::integer rr_change,false rank_ambiguous,false stats_ambiguous,false carried_forward
      from historical_act_players p left join historical_act_players g on g.act=p.act and g.source_region='global' and g.player_region=p.player_region and g.player_key=p.player_key
      left join historical_act_players r on r.act=p.act and r.source_region=p.player_region and r.player_region=p.player_region and r.player_key=p.player_key
      where p.act=? and p.source_region=? and (?='all' or p.player_region=?)
       and (?='all' or coalesce(p.country,'unknown')=?) and lower(p.player_key) like ?
      order by "+order+" limit ? offset ?
      """,params);
    Long total=db.queryForObject("select count(*) from historical_act_players p where p.act=? and p.source_region=? and (?='all' or p.player_region=?) and (?='all' or coalesce(p.country,'unknown')=?) and lower(p.player_key) like ?",Long.class,act,source,region,region,country,country,"%"+search.toLowerCase(Locale.ROOT)+"%");
    return Map.of("items",items,"total",total,"page",page,"size",size,"archiveOnly",true);
  }

  public List<Map<String,Object>> history(String act,String player,String region){String source=region.equals("all")?"global":region;return db.queryForList("""
    select p.act,p.source_region,p.player_region region,p.player_key,p.display_name,p.source_rank rr_rank,
      case when p.source_region='global' then p.source_rank else g.source_rank end global_rank,
      case when p.source_region='global' then r.source_rank else p.source_rank end regional_rank,
      p.rr,p.tier,p.wins,p.country,p.source_updated_at completed_at,p.source_updated_at
    from historical_act_players p left join historical_act_players g on g.act=p.act and g.source_region='global' and g.player_region=p.player_region and g.player_key=p.player_key
    left join historical_act_players r on r.act=p.act and r.source_region=p.player_region and r.player_region=p.player_region and r.player_key=p.player_key
    where p.act=? and p.source_region=? and p.player_key=? order by p.player_region
    """,act,source,player.toLowerCase(Locale.ROOT));}
  public Map<String,Object> playerSummary(String act,String player,String region){var rows=history(act,player,region);if(rows.isEmpty())return Map.of("act",act,"current",List.of(),"peak",List.of(),"best",Map.of("observation_count",0),"archiveOnly",true);var row=rows.get(0);var best=new LinkedHashMap<String,Object>();best.put("observation_count",rows.size());best.put("best_rr",row.get("rr"));best.put("best_global_rank",region.equals("all")?row.get("rr_rank"):null);best.put("best_regional_rank",region.equals("all")?null:row.get("rr_rank"));return Map.of("act",act,"current",List.of(row),"peak",List.of(row),"best",best,"archiveOnly",true);}
  public Map<String,Object> countryPage(String act,String region,String country,int page,String search){var status=status(act);var latest=(List<Map<String,Object>>)status.get("latestSnapshot");Object id=latest.isEmpty()?act:latest.get(0).get("id");return Map.of("snapshotId",id,"countries",countries(act,region,"all"),"players",players(act,region,country,search,page,10),"confidence",confidence(act,region,country));}
  public Map<String,Object> unavailable(String act,String feature){return Map.of("act",act,"archiveOnly",true,"available",false,"message",feature+" is unavailable for a single end-of-act leaderboard snapshot.");}
  @PreDestroy public void close(){scheduler.shutdownNow();worker.shutdownNow();pageFetchers.shutdownNow();}
}
