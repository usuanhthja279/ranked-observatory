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
public class BoundaryMovement {
  private final NamedParameterJdbcTemplate sql;
  private final String act;
  public BoundaryMovement(JdbcTemplate db,@Value("${leaderboard.act:d816f426-48ea-f052-117f-9697a155b319}")String act){this.sql=new NamedParameterJdbcTemplate(db);this.act=act;}

  @GetMapping("/api/leaderboard/movement-boundary-signals")
  @Cacheable(cacheNames="dashboardReads",keyGenerator="snapshotKeyGenerator",sync=true)
  public Map<String,Object> get(@RequestParam String region,@RequestParam(defaultValue="all")String country,
      @RequestParam(defaultValue="60")int minutes,@RequestParam(defaultValue="all")String signal,@RequestParam(defaultValue="1")int page){
    if(!GlobalLeaderboardService.REGIONS.containsKey(region)||!country.matches("all|unknown|[a-z]{2}")||!Set.of(15,30,60,360,720,1440).contains(minutes)
        ||!Set.of("all","possible_promotion","possible_demotion").contains(signal)||page<1||page>100000)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid boundary movement scope");
    String cte="""
      with latest as (select id,completed_at from global_leaderboard_runs where act=:act and status='COMPLETE' order by id desc limit 1),
      timeline as (select r.id,r.started_at,r.completed_at,r.regional_reconciled_at,
        lag(r.id) over(order by r.id) previous_id,lag(r.completed_at) over(order by r.id) previous_at
        from global_leaderboard_runs r where r.act=:act and r.status='COMPLETE'),
      pairs as (select t.* from timeline t,latest l where t.id<=l.id and t.previous_at>=l.completed_at-(:minutes * interval '1 minute')
        and t.started_at>=t.previous_at),
      populations as (select p.run_id,count(*) fresh from global_leaderboard_players p
        where p.region=:region and not p.carried_forward and p.run_id in(select id from pairs union select previous_id from pairs) group by p.run_id),
      eligible as (select r.* from pairs r join populations n on n.run_id=r.id join populations o on o.run_id=r.previous_id
        where o.fresh>0 and n.fresh>=o.fresh*0.8),
      candidates as (
        select p.player_key,p.display_name,p.region,coalesce(p.country,'unknown') country,p.rr,p.source_updated_at,
          r.completed_at event_at,'possible_promotion' signal
        from eligible r join global_leaderboard_players p on p.run_id=r.id and p.region=:region
        where p.tier='Immortal 1' and p.rr between 0 and 25 and not p.carried_forward and not p.stats_ambiguous and not p.rank_ambiguous
          and p.source_updated_at>=r.previous_at
          and not exists(select 1 from global_leaderboard_players old where old.run_id=r.previous_id and old.player_key=p.player_key)
        union all
        select p.player_key,p.display_name,p.region,coalesce(p.country,'unknown') country,p.rr,p.source_updated_at,
          r.completed_at event_at,'possible_demotion' signal
        from eligible r join global_leaderboard_players p on p.run_id=r.previous_id and p.region=:region cross join latest l
        where r.regional_reconciled_at is not null and p.tier='Immortal 1' and p.rr between 0 and 25
          and not p.carried_forward and not p.stats_ambiguous and not p.rank_ambiguous
          and p.source_updated_at>=l.completed_at-(:minutes * interval '1 minute')
          and not exists(select 1 from global_leaderboard_players now where now.run_id=r.id and now.player_key=p.player_key)
      ),deduplicated as (
        select distinct on(country,signal,player_key) * from candidates order by country,signal,player_key,event_at desc
      ),scoped as (select * from deduplicated where (:country='all' or country=:country)),
      selected as (select * from scoped where (:signal='all' or signal=:signal))
      """;
    var p=new HashMap<String,Object>(Map.of("act",act,"region",region,"country",country,"minutes",minutes,"signal",signal,"offset",(page-1)*20));
    var summary=sql.queryForMap(cte+"select (select id from latest) snapshot_id,(select completed_at from latest) window_end,(select completed_at from latest)-(:minutes * interval '1 minute') window_start,(select count(*) from eligible) eligible_pairs,(select count(*) from eligible where regional_reconciled_at is not null) demotion_eligible_pairs,(select count(*) from selected) total",p);
    var countries=sql.queryForList(cte+"select country,count(*) filter(where signal='possible_promotion') possible_promotions,count(*) filter(where signal='possible_demotion') possible_demotions from scoped group by country order by country",p);
    var items=sql.queryForList(cte+"select * from selected order by event_at desc,signal,rr,player_key limit 20 offset :offset",p);
    return Map.of("summary",summary,"countries",countries,"items",items,"page",page,"size",20,"minutes",minutes,
      "definition","Possible crossings, not confirmed tier changes. Counts are distinct players per reported country and direction in the selected window. Both completed snapshots must be inside the window; collection windows must not overlap and fresh regional coverage must retain at least 80%. Demotions require full regional reconciliation and absence from every tracked region. Carried and conflicting observations are excluded. Renames or missing provider data can mimic crossings. These columns do not contribute to confirmed movement totals.");
  }
}
