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
public class LeaderboardConfidence {
  private final NamedParameterJdbcTemplate sql;
  private final String act;
  private final HistoricalActArchiveService archive;
  public LeaderboardConfidence(JdbcTemplate db,HistoricalActArchiveService archive,@Value("${leaderboard.act:d816f426-48ea-f052-117f-9697a155b319}")String act){this.sql=new NamedParameterJdbcTemplate(db);this.act=act;this.archive=archive;}
  private void validate(String region){if(!region.equals("all")&&!GlobalLeaderboardService.REGIONS.containsKey(region))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown region");}

  @GetMapping("/api/leaderboard/confidence")
  @Cacheable(cacheNames="dashboardReads",keyGenerator="snapshotKeyGenerator",sync=true)
  public Map<String,Object> confidence(@RequestParam(defaultValue="all")String region,@RequestParam(defaultValue="all")String country,@RequestParam(required=false)String act){
    validate(region);if(!country.matches("all|unknown|[a-z]{2}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown country");
    if(act!=null&&!act.equals(this.act)){if(!act.matches("[a-fA-F0-9-]{36}")||!archive.isKnown(act))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Act is not in the archive catalogue");return archive.confidence(act,region,country);}
    return sql.queryForMap("""
      with latest as (select id from global_leaderboard_runs where act=:act and status='COMPLETE' order by id desc limit 1)
      select (select id from latest) snapshot_id,count(*) players,
        count(*) filter(where country is not null) known_country,
        count(*) filter(where carried_forward) carried,
        count(*) filter(where stats_ambiguous or rank_ambiguous) conflicting,
        min(source_updated_at) filter(where not carried_forward) source_from,
        max(source_updated_at) filter(where not carried_forward) source_to
      from global_leaderboard_players where run_id=(select id from latest)
        and (:region='all' or region=:region) and (:country='all' or coalesce(country,'unknown')=:country)
      """,Map.of("act",act,"region",region,"country",country));
  }

  @GetMapping("/api/leaderboard/radiant-race")
  @Cacheable(cacheNames="dashboardReads",keyGenerator="snapshotKeyGenerator",sync=true)
  public Map<String,Object> race(@RequestParam String region,@RequestParam String player,@RequestParam(defaultValue="60")int minutes){
    validate(region);if(region.equals("all")||player.isBlank()||player.length()>300||!Set.of(15,30,60,360,720,1440).contains(minutes))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid race scope");
    var rows=sql.queryForList("""
      with latest as (select id,completed_at from global_leaderboard_runs where act=:act and status='COMPLETE' order by id desc limit 1),
      selected as (select r.id,r.completed_at from global_leaderboard_runs r,latest l
        where r.act=:act and r.status='COMPLETE' and r.id<=l.id and r.completed_at between l.completed_at-(:minutes * interval '1 minute') and l.completed_at),
      cutoffs as (select p.run_id,min(p.rr) cutoff,count(*) radiant_count,
        min(p.source_updated_at) cutoff_source_from,max(p.source_updated_at) cutoff_source_to
        from global_leaderboard_players p join selected r on r.id=p.run_id
        where p.region=:region and p.tier='Radiant' and not p.carried_forward and not p.stats_ambiguous and not p.rank_ambiguous group by p.run_id)
      select r.id snapshot_id,r.completed_at,c.cutoff,coalesce(c.radiant_count,0) radiant_count,c.cutoff_source_from,c.cutoff_source_to,
        case when not p.carried_forward and not p.stats_ambiguous then p.rr end rr,
        p.tier,p.source_updated_at player_source_at
      from selected r left join cutoffs c on c.run_id=r.id
      left join global_leaderboard_players p on p.run_id=r.id and p.region=:region and p.player_key=:player
      order by r.completed_at,r.id
      """,Map.of("act",act,"region",region,"player",player.toLowerCase(Locale.ROOT),"minutes",minutes));
    return Map.of("region",region,"minutes",minutes,"points",rows,"definition","Lowest RR among observed, unflagged, non-carried Radiant players in this region. Not an official promotion requirement; incomplete coverage can bias this boundary. Both series are aligned by completed snapshot, not match time.");
  }
}
