package com.valorant.leaderboard;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class PlayerTargets {
  private final NamedParameterJdbcTemplate sql;
  private final String act;
  public PlayerTargets(JdbcTemplate db,@Value("${leaderboard.act:d816f426-48ea-f052-117f-9697a155b319}")String act){this.sql=new NamedParameterJdbcTemplate(db);this.act=act;}
  private void region(String r){if(!r.equals("all")&&!GlobalLeaderboardService.REGIONS.containsKey(r))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown region");}

  @GetMapping("/api/leaderboard/overtake-targets")
  @Cacheable(cacheNames="dashboardReads",keyGenerator="snapshotKeyGenerator",sync=true)
  public Map<String,Object> targets(@RequestParam String region,@RequestParam String player,@RequestParam(defaultValue="60")int minutes){
    region(region);if(region.equals("all")||player.isBlank()||player.length()>300||!Set.of(15,30,60,360,720,1440).contains(minutes))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid target scope");
    var rows=sql.queryForList("""
      with latest as (select id,completed_at from global_leaderboard_runs where act=:act and status='COMPLETE' order by id desc limit 1),
      self as (select p.* from global_leaderboard_players p,latest l where p.run_id=l.id and p.region=:region and p.player_key=:player
        and not p.carried_forward and not p.stats_ambiguous and not p.rank_ambiguous),
      targets as (select q.*,q.rr-s.rr rr_gap,s.rr self_rr,s.source_updated_at self_source_at
        from global_leaderboard_players q join self s on q.run_id=s.run_id and q.region=s.region
        where q.rr>s.rr and not q.carried_forward and not q.stats_ambiguous and not q.rank_ambiguous
        order by q.rr,q.regional_rank nulls last,q.player_key limit 5)
      select t.player_key,t.display_name,t.region,t.country,t.tier,t.rr,t.regional_rank,t.rr_gap,t.source_updated_at,t.self_source_at,
        l.id snapshot_id,l.completed_at,baseline.rr_gap baseline_gap,baseline.completed_at baseline_at,
        t.rr_gap-baseline.rr_gap gap_change
      from targets t cross join latest l left join lateral (
        select q.rr-s.rr rr_gap,r.completed_at from global_leaderboard_runs r
        join global_leaderboard_players q on q.run_id=r.id and q.region=:region and q.player_key=t.player_key
        join global_leaderboard_players s on s.run_id=r.id and s.region=:region and s.player_key=:player
        where r.act=:act and r.status='COMPLETE' and r.id<l.id
          and r.completed_at>=l.completed_at-(:minutes * interval '1 minute')
          and not q.carried_forward and not s.carried_forward and not q.stats_ambiguous and not s.stats_ambiguous
          and not q.rank_ambiguous and not s.rank_ambiguous
        order by r.completed_at,r.id limit 1
      ) baseline on true order by t.rr,t.regional_rank nulls last,t.player_key
      """,Map.of("act",act,"region",region,"player",player.toLowerCase(Locale.ROOT),"minutes",minutes));
    var latest=sql.queryForMap("select id snapshot_id,completed_at from global_leaderboard_runs where act=:act and status='COMPLETE' order by id desc limit 1",Map.of("act",act));
    return Map.of("items",rows,"snapshot",latest,"minutes",minutes,"region",region,
      "definition","Five nearest higher-RR players in the same region among usable current observations; ties at your RR are omitted. Gap change compares each target with the earliest shared usable snapshot in the selected window. Negative means closing; positive means widening. These are observed RR targets, not a guaranteed official rank order.");
  }

  @GetMapping("/api/leaderboard/boundary-signals")
  @Cacheable(cacheNames="dashboardReads",keyGenerator="snapshotKeyGenerator",sync=true)
  public Map<String,Object> boundary(@RequestParam(defaultValue="all")String region,@RequestParam(defaultValue="all")String country){
    region(region);if(!country.matches("all|unknown|[a-z]{2}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown country");
    String cte="""
      with recent as (select id,started_at,completed_at,regional_reconciled_at from global_leaderboard_runs where act=:act and status='COMPLETE' order by id desc limit 2),
      latest as (select * from recent order by id desc limit 1),previous as (select * from recent order by id limit 1),
      eligible as (select (select count(*) from recent)=2 and
        (select started_at from latest)>=(select completed_at from previous) valid),
      populations as (select p.region,count(*) filter(where p.run_id=(select id from latest) and not p.carried_forward) current_count,
        count(*) filter(where p.run_id=(select id from previous) and not p.carried_forward) previous_count
        from global_leaderboard_players p where p.run_id in(select id from recent) group by p.region),
      candidates as (
        select p.player_key,p.display_name,p.region,p.country,p.rr,p.source_updated_at,'possible_promotion' signal
        from global_leaderboard_players p join populations n on n.region=p.region
        where p.run_id=(select id from latest) and (select valid from eligible)
          and n.current_count>=n.previous_count*0.8 and n.previous_count>0
          and p.tier='Immortal 1' and p.rr between 0 and 25 and not p.carried_forward and not p.stats_ambiguous and not p.rank_ambiguous
          and p.source_updated_at>=(select completed_at from previous)
          and not exists(select 1 from global_leaderboard_players old where old.run_id=(select id from previous) and old.player_key=p.player_key)
        union all
        select p.player_key,p.display_name,p.region,p.country,p.rr,p.source_updated_at,'possible_demotion' signal
        from global_leaderboard_players p join populations n on n.region=p.region
        where p.run_id=(select id from previous) and (select valid from eligible)
          and (select regional_reconciled_at from latest) is not null
          and n.current_count>=n.previous_count*0.8 and n.previous_count>0
          and p.tier='Immortal 1' and p.rr between 0 and 25 and not p.carried_forward and not p.stats_ambiguous and not p.rank_ambiguous
          and not exists(select 1 from global_leaderboard_players now where now.run_id=(select id from latest) and now.player_key=p.player_key)
      ),scoped as (select * from candidates where (:region='all' or region=:region) and (:country='all' or coalesce(country,'unknown')=:country))
      """;
    var params=Map.of("act",act,"region",region,"country",country);
    var summary=sql.queryForMap(cte+"select (select id from latest) snapshot_id,(select completed_at from latest) completed_at,(select completed_at from previous) previous_at,(select valid from eligible) comparable,(select regional_reconciled_at is not null from latest) demotion_scan_eligible,count(*) filter(where signal='possible_promotion') possible_promotions,count(*) filter(where signal='possible_demotion') possible_demotions from scoped",params);
    var items=sql.queryForList(cte+"select * from scoped order by signal,rr,region,player_key limit 20",params);
    return Map.of("summary",summary,"items",items,"definition","Heuristic signals between the last two successful, non-overlapping scans in this act. New usable Immortal 1 at 0–25 RR suggests a promotion; prior usable Immortal 1 at 0–25 RR absent from every region suggests a demotion only after full regional reconciliation. At least 80% of the region's previous fresh population must remain. Missing records, renamed identities and delayed sources can mimic crossings. Country is reported country. These are not confirmed rank changes and never enter movement totals or tier counts.");
  }
}
