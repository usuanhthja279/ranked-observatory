package com.valorant.leaderboard;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Service
public class LeaderboardService {
  private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);
  private final JdbcTemplate db;
  private final TrackerLeaderboardClient client;
  private final TransactionTemplate tx;
  private final Map<String,String> countries;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final AtomicBoolean running = new AtomicBoolean();
  @Value("${leaderboard.act:d816f426-48ea-f052-117f-9697a155b319}") private String act;
  @Value("${leaderboard.enabled:true}") private boolean enabled;
  @Value("${leaderboard.max-pages:200}") private int maxPages;
  @Value("${leaderboard.request-delay-ms:1000}") private long delay;
  private volatile String lastError;

  public LeaderboardService(JdbcTemplate db, TrackerLeaderboardClient client, PlatformTransactionManager tm, ObjectMapper mapper) throws Exception {
    this.db=db; this.client=client; this.tx=new TransactionTemplate(tm);
    try (var input = new ClassPathResource("leaderboard-countries.json").getInputStream()) {
      countries=Collections.unmodifiableMap(mapper.readValue(input, new TypeReference<LinkedHashMap<String,String>>() {}));
    }
  }
  // Legacy AP country collector retained for historical access; scheduling belongs to GlobalLeaderboardService.
  public void scheduled() { if (enabled) collect(); }
  public boolean collect() {
    return collect(countries.keySet());
  }
  public boolean collect(Collection<String> requestedCountries) {
    List<String> selected=List.copyOf(new LinkedHashSet<>(requestedCountries));
    if (selected.isEmpty() || !selected.stream().allMatch(countries::containsKey))
      throw new IllegalArgumentException("Unknown or empty country selection");
    if (!enabled || !running.compareAndSet(false,true)) return false;
    worker.submit(() -> {
      try {
        // Session lock also prevents two app instances from collecting simultaneously.
        db.execute((ConnectionCallback<Void>) connection -> {
          try (var statement=connection.createStatement(); var result=statement.executeQuery("select pg_try_advisory_lock(782613504)")) {
            result.next(); if (!result.getBoolean(1)) return null;
          }
          try {
            for (String country:selected) {
              if (Thread.currentThread().isInterrupted()) break;
              collectCountry(country);
            }
          } finally {
            try (var statement=connection.createStatement()) { statement.execute("select pg_advisory_unlock(782613504)"); }
          }
          return null;
        });
      } catch (Exception error) { lastError=error.getClass().getSimpleName(); log.warn("Leaderboard collection failed",error); }
      finally { running.set(false); }
    });
    return true;
  }
  private void collectCountry(String country) {
    Long run=db.queryForObject("insert into leaderboard_runs(act,country,started_at,status) values (?,?,?,'RUNNING') returning id",Long.class,act,country,OffsetDateTime.now(ZoneOffset.UTC));
    var observations=new LeaderboardObservations(); int pages=0;
    try {
      boolean ended=false;
      for (int page=1;page<=maxPages;page++) {
        Thread.sleep(Math.max(250,delay));
        var response=client.fetch(act,country,page);
        observations.add(response);
        if (!response.players().isEmpty()) pages++;
        if (response.players().size()<100) { ended=true; break; }
      }
      if (!ended) throw new IllegalStateException("Page safety limit reached");
      final int pageCount=pages;
      tx.executeWithoutResult(status -> {
        db.batchUpdate("insert into leaderboard_players(run_id,player_key,country,regional_rank,rr,tier,wins,source_updated_at,rank_ambiguous) values (?,?,?,?,?,?,?,?,?)", observations.rows(), 100,
            (ps,row) -> { var p=row.player(); ps.setLong(1,run); ps.setString(2,p.key()); ps.setString(3,p.country()); ps.setInt(4,p.rank()); ps.setInt(5,p.rr()); ps.setString(6,p.tier()); ps.setInt(7,p.wins()); ps.setObject(8,row.updated()); ps.setBoolean(9,row.rankAmbiguous()); });
        db.update("update leaderboard_runs set status='COMPLETE',completed_at=?,pages=?,players=?,raw_rows=?,duplicate_rows=?,ambiguous_ranks=? where id=?",OffsetDateTime.now(ZoneOffset.UTC),pageCount,observations.size(),observations.rawRows(),observations.duplicates(),observations.ambiguousRanks(),run);
      });
      log.info("Leaderboard {}: {} pages, {} unique players, {} duplicate rows, {} uncertain ranks",country,pages,observations.size(),observations.duplicates(),observations.ambiguousRanks());
    } catch (Exception error) {
      if (error instanceof InterruptedException) Thread.currentThread().interrupt();
      String message=Objects.toString(error.getMessage(),error.getClass().getSimpleName());
      db.update("update leaderboard_runs set status='FAILED',completed_at=?,pages=?,players=?,raw_rows=?,duplicate_rows=?,ambiguous_ranks=?,error=? where id=?",OffsetDateTime.now(ZoneOffset.UTC),pages,observations.size(),observations.rawRows(),observations.duplicates(),observations.ambiguousRanks(),message.substring(0,Math.min(500,message.length())),run);
      log.warn("Leaderboard {} failed: {}",country,message);
    }
  }
  public Map<String,Object> status() {
    Map<String,Object> result=new LinkedHashMap<>();
    result.put("act",act);result.put("region","ap");result.put("enabled",enabled);result.put("running",running.get());result.put("lastError",lastError);result.put("countries",countries);
    result.put("runs",db.queryForList("select r.* from leaderboard_runs r join (select country,max(id) id from leaderboard_runs where act=? group by country) x on r.id=x.id order by r.country",act));
    return result;
  }
  public List<Map<String,Object>> comparison() {
    return db.queryForList("""
      select r.country,r.id run_id,r.completed_at,r.pages,r.players,r.raw_rows,r.duplicate_rows,r.ambiguous_ranks,
      count(p.player_key) filter(where p.tier='Radiant') radiant,
      count(p.player_key) filter(where p.tier like 'Immortal%') immortal,
      count(p.player_key) filter(where p.regional_rank<=100 and not p.rank_ambiguous) top100,
      count(p.player_key) filter(where p.regional_rank<=500 and not p.rank_ambiguous) top500,
      avg(p.rr) avg_rr,min(p.regional_rank) filter(where not p.rank_ambiguous) best_rank
      from leaderboard_runs r join
      (select country,max(id) id from leaderboard_runs where act=? and status='COMPLETE' group by country) x on r.id=x.id
      left join leaderboard_players p on p.run_id=r.id
      group by r.country,r.id,r.completed_at,r.pages,r.players order by r.players desc,r.country
      """,act);
  }
  public List<Map<String,Object>> players(String country) {
    return db.queryForList("""
      with recent as (select id from leaderboard_runs where act=? and country=? and status='COMPLETE' order by id desc limit 2),
      latest as (select max(id) id from recent), previous as (select min(id) id from recent having count(*)=2)
      select p.*,old.rr previous_rr,old.regional_rank previous_rank,
      p.rr-old.rr rr_change,case when p.rank_ambiguous or old.rank_ambiguous then null else old.regional_rank-p.regional_rank end rank_gain,
      case when old.player_key is not null then 'RETURNING' when (select id from previous) is null then 'BASELINE' else 'ENTERED' end movement
      from leaderboard_players p join latest l on p.run_id=l.id
      left join leaderboard_players old on old.run_id=(select id from previous) and old.player_key=p.player_key
      order by p.regional_rank
      """,act,country);
  }
  public List<Map<String,Object>> history(String player) {
    return db.queryForList("""
      select r.completed_at,r.country,p.regional_rank,p.rr,p.tier,p.wins,p.source_updated_at,p.rank_ambiguous
      from leaderboard_players p join leaderboard_runs r on r.id=p.run_id
      where r.act=? and r.status='COMPLETE' and p.player_key=?
      order by r.id desc limit 1000
      """,act,player);
  }
  public List<Map<String,Object>> countryHistory(String country) {
    return db.queryForList("select completed_at,pages,players from leaderboard_runs where act=? and country=? and status='COMPLETE' order by id desc limit 1000",act,country);
  }
  public boolean validCountry(String country) { return countries.containsKey(country); }
  @PreDestroy public void close() { worker.shutdownNow(); }
}
