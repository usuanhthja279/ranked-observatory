package com.valorant.leaderboard;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class CountryActivity {
 private final NamedParameterJdbcTemplate sql;
 private final String act;
 public CountryActivity(JdbcTemplate db,@Value("${leaderboard.act:d816f426-48ea-f052-117f-9697a155b319}")String act){this.sql=new NamedParameterJdbcTemplate(db);this.act=act;}

 @org.springframework.cache.annotation.Cacheable(cacheNames="dashboardReads",keyGenerator="snapshotKeyGenerator",sync=true)
 @GetMapping("/api/leaderboard/activity-overview")
 public Map<String,Object> overview(@RequestParam(defaultValue="60")int minutes){
  var regions=new ArrayList<Map<String,Object>>();var countries=new HashMap<String,long[]>();var countryRegions=new HashMap<String,String>();var countryRegionActivity=new HashMap<String,Long>();Map<String,Object> coverage=null;
  for(String region:new TreeSet<>(GlobalLeaderboardService.REGIONS.keySet())){
   var activity=get(region,minutes);
   if(coverage!=null&&!Objects.equals(coverage.get("window_end"),activity.get("window_end")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Snapshot changed; retry activity overview");
   coverage=activity;long wins=0,losses=0;
   @SuppressWarnings("unchecked") var rows=(List<Map<String,Object>>)activity.get("countries");
   for(var row:rows){long w=((Number)row.get("wins_added")).longValue(),l=((Number)row.get("losses_added")).longValue();wins+=w;losses+=l;
    String country=row.get("country").toString();if(!country.equals("unknown")){var total=countries.computeIfAbsent(country,k->new long[2]);total[0]+=w;total[1]+=l;if(w+l>countryRegionActivity.getOrDefault(country,-1L)){countryRegionActivity.put(country,w+l);countryRegions.put(country,region);}}
   }
   regions.add(Map.of("region",region,"wins",wins,"losses",losses,"matches",wins+losses));
  }
  var activeRegions=new HashMap<String,Long>();var activeCountries=new HashMap<String,Long>();
  if(coverage!=null&&coverage.get("window_end")!=null)for(var row:activePlayers(minutes,coverage.get("window_end"))){
    (row.get("scope").equals("region")?activeRegions:activeCountries).put(row.get("key").toString(),((Number)row.get("active")).longValue());
  }
  regions.replaceAll(row->{var enriched=new LinkedHashMap<String,Object>(row);enriched.put("active",activeRegions.getOrDefault(row.get("region").toString(),0L));return enriched;});
  regions.sort(Comparator.<Map<String,Object>>comparingLong(r->((Number)r.get("matches")).longValue()).reversed().thenComparing(r->r.get("region").toString()));
  var top=countries.entrySet().stream().map(e->Map.<String,Object>of("country",e.getKey(),"region",countryRegions.get(e.getKey()),"wins",e.getValue()[0],"losses",e.getValue()[1],"matches",e.getValue()[0]+e.getValue()[1],"active",activeCountries.getOrDefault(e.getKey(),0L)))
   .filter(r->((Number)r.get("matches")).longValue()>0).sorted(Comparator.<Map<String,Object>>comparingLong(r->((Number)r.get("matches")).longValue()).reversed().thenComparing(r->r.get("country").toString())).limit(10).toList();
  var result=new LinkedHashMap<String,Object>();result.put("regions",regions);result.put("countries",top);result.put("minutes",minutes);
  if(coverage!=null)for(String key:List.of("window_start","window_end","first_snapshot","last_snapshot","snapshot_count"))result.put(key,coverage.get(key));
  return result;
 }

 private List<Map<String,Object>> activePlayers(int minutes,Object end){
  return sql.queryForList("""
   with fresh as (
    select p.region,p.player_key,p.country,p.source_updated_at,p.rr,p.wins,p.tier,
      row_number() over(partition by p.region,p.player_key,p.source_updated_at order by r.completed_at desc,r.id desc) duplicate
    from (select id,completed_at from global_leaderboard_runs where act=:act and status='COMPLETE'
      and completed_at between cast(:end as timestamptz)-(:minutes * interval '1 minute') and cast(:end as timestamptz)) r
    cross join lateral (select region,player_key,country,source_updated_at,rr,wins,tier
      from global_leaderboard_players p where p.run_id=r.id
      and p.source_updated_at between cast(:end as timestamptz)-(:minutes * interval '1 minute') and cast(:end as timestamptz)
      and not p.carried_forward and not p.stats_ambiguous offset 0) p
   ), paired as (
    select *,lag(rr) over w prior_rr,lag(wins) over w prior_wins,lag(tier) over w prior_tier,
      lag(coalesce(country,'unknown')) over w prior_country
    from fresh where duplicate=1 window w as(partition by region,player_key order by source_updated_at)
   ), active as (
    select region,player_key,coalesce(country,'unknown') country from paired
    where prior_rr is not null and wins>=prior_wins and coalesce(country,'unknown')=prior_country
      and (rr<>prior_rr or wins>prior_wins or tier<>prior_tier)
   )
   select case when grouping(region)=0 then 'region' else 'country' end scope,
     case when grouping(region)=0 then region else country end key,count(distinct player_key) active
   from active group by grouping sets ((region),(country))
   """,Map.of("act",act,"minutes",minutes,"end",end));
 }

 @org.springframework.cache.annotation.Cacheable(cacheNames="dashboardReads",keyGenerator="snapshotKeyGenerator",sync=true)
 @GetMapping("/api/leaderboard/country-activity")
 public Map<String,Object> get(@RequestParam String region,@RequestParam(defaultValue="60")int minutes){
  if(!GlobalLeaderboardService.REGIONS.containsKey(region)||!Set.of(15,30,60,360,720,1440).contains(minutes))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid region or activity window");
  var params=Map.<String,Object>of("act",act,"region",region,"minutes",minutes);
  var coverage=sql.queryForMap("""
   with latest as (select max(completed_at) stamp from global_leaderboard_runs where act=:act and status='COMPLETE')
   select (select stamp from latest) window_end,(select stamp from latest)-(:minutes * interval '1 minute') window_start,
     min(completed_at) first_snapshot,max(completed_at) last_snapshot,count(*) snapshot_count
   from global_leaderboard_runs where act=:act and status='COMPLETE'
     and completed_at between (select stamp from latest)-(:minutes * interval '1 minute') and (select stamp from latest)
   """,params);
  var rows=sql.queryForList("""
   with latest as (select max(completed_at) stamp from global_leaderboard_runs where act=:act and status='COMPLETE'),
   fresh as (
    select p.player_key,p.source_updated_at,p.rr,p.wins,p.country,row_number() over(partition by p.player_key,p.source_updated_at order by r.completed_at desc,r.id desc) duplicate,
      case p.tier when 'Immortal 1' then 1 when 'Immortal 2' then 2 when 'Immortal 3' then 3 when 'Radiant' then 4 end level
    from (select id,completed_at from global_leaderboard_runs where act=:act and status='COMPLETE'
      and completed_at between (select stamp from latest)-(:minutes * interval '1 minute') and (select stamp from latest)) r
    cross join lateral (select p.* from global_leaderboard_players p where p.run_id=r.id and p.region=:region
      and p.source_updated_at between (select stamp from latest)-(:minutes * interval '1 minute') and (select stamp from latest)
      and not p.carried_forward and not p.stats_ambiguous offset 0) p
   ), paired as (
    select *,lag(level) over w prior_level,lag(rr) over w prior_rr,lag(wins) over w prior_wins,
      lag(coalesce(country,'unknown')) over w prior_country
    from fresh where duplicate=1 window w as(partition by player_key order by source_updated_at)
   ), comparable as (
    select * from paired where prior_rr is not null and coalesce(country,'unknown')=prior_country and wins>=prior_wins
   ), player_net as (
    select coalesce(country,'unknown') country,player_key,sum(rr-prior_rr) net_rr
    from comparable group by coalesce(country,'unknown'),player_key
   ), country_net as (
    select country,count(*) filter(where net_rr>0) positive,count(*) filter(where net_rr<0) negative
    from player_net group by country
   )
   select coalesce(country,'unknown') country,count(distinct player_key) comparable,
    count(distinct player_key) filter(where rr<>prior_rr or level<>prior_level or wins>prior_wins) active,
    coalesce(sum(wins-prior_wins),0) wins_added,
    count(*) filter(where rr<prior_rr) losses_added,
    (select n.positive from country_net n where n.country=coalesce(comparable.country,'unknown')) rr_positive,
    (select n.negative from country_net n where n.country=coalesce(comparable.country,'unknown')) rr_negative,
    count(distinct player_key) filter(where prior_level<2 and level>=2) up12,
    count(distinct player_key) filter(where prior_level<3 and level>=3) up23,
    count(distinct player_key) filter(where prior_level<4 and level>=4) up34,
    count(distinct player_key) filter(where prior_level>=2 and level<2) down21,
    count(distinct player_key) filter(where prior_level>=3 and level<3) down32,
    count(distinct player_key) filter(where prior_level>=4 and level<4) down43
   from comparable group by country order by active desc,country
   """,params);
  var result=new LinkedHashMap<String,Object>(coverage);result.put("countries",rows);result.put("minutes",minutes);return result;
 }
}
