package com.valorant.leaderboard;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Read-only evidence. Inferred labels never enter the source snapshot tables. */
@Service
public class CountryModelEvidence {
 private final NamedParameterJdbcTemplate sql;
 private final String act;
 private final Map<String,Object> serverAccess=loadServerAccess();
 private static Map<String,Object> loadServerAccess(){
  try(var input=new org.springframework.core.io.ClassPathResource("server-access-data.json").getInputStream()){
   return new com.fasterxml.jackson.databind.ObjectMapper().readValue(input,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
  }catch(Exception unavailable){return Map.of("enabled",false,"reason","Published network measurements unavailable");}
 }
 private Map<String,Object> cached;
 private Object cachedProfileRevision;
 public CountryModelEvidence(JdbcTemplate db,@Value("${leaderboard.act:d816f426-48ea-f052-117f-9697a155b319}")String act){this.sql=new NamedParameterJdbcTemplate(db);this.act=act;}
 public synchronized Map<String,Object> get(){
  var latest=sql.queryForList("select id,completed_at from global_leaderboard_runs where act=:act and status='COMPLETE' order by id desc limit 1",Map.of("act",act));
  if(latest.isEmpty())return Map.of("regions",Map.of());
  Object profileRevision=sql.queryForObject("select max(fetched_at) from reported_profile_countries",Map.of(),java.sql.Timestamp.class);
  if(cached!=null&&Objects.equals(cachedProfileRevision,profileRevision)&&Objects.equals(cached.get("snapshotId"),latest.getFirst().get("id")))return cached;
  var p=new HashMap<String,Object>();p.put("act",act);p.put("run",latest.getFirst().get("id"));p.put("at",latest.getFirst().get("completed_at"));
  String resolvedCte="""
   with recent as (
    select h.region,h.player_key,min(h.country) country,count(distinct h.country) choices
    from global_leaderboard_players h join global_leaderboard_runs r on r.id=h.run_id
    where r.act=:act and r.status='COMPLETE' and r.id<=:run
     and r.completed_at>=cast(:at as timestamptz)-interval '7 days'
     and h.source_updated_at>=cast(:at as timestamptz)-interval '7 days' and h.source_updated_at<=cast(:at as timestamptz)
     and h.country is not null and not h.carried_forward and not h.stats_ambiguous
    group by h.region,h.player_key
   ), resolved as (
    select p.region,p.tier,p.rr,p.stats_ambiguous,p.country original_country,
     case when p.country is not null then p.country
          when h.choices=1 and (f.country is null or f.country=h.country) then h.country
          when h.choices is null then f.country end country,
     case when h.choices=1 then 'recent leaderboard' else 'reported profile' end source
    from global_leaderboard_players p left join recent h using(region,player_key)
    left join reported_profile_countries f on f.player_key=lower(p.display_name)
     and f.fetched_at>=cast(:at as timestamptz)-interval '7 days' and f.fetched_at<=current_timestamp
    where p.run_id=:run
   )
   """;
  var recoveries=sql.queryForList(resolvedCte+"select region,country,tier,source,case when rr>=150 then '150+' else 'below150' end rr_band,count(*) players from resolved where original_country is null and country is not null group by region,country,tier,source,rr_band",p);
  var countryStats=sql.queryForList(resolvedCte+"select region,country,percentile_cont(0.5) within group(order by rr) filter(where not stats_ambiguous) median_rr from resolved where country is not null group by region,country",p);
  var history=sql.queryForList("""
   with daily as (
    select distinct on ((r.completed_at at time zone 'UTC')::date) r.id,r.completed_at
    from global_leaderboard_runs r where r.act=:act and r.status='COMPLETE' and r.id<:run
     and r.completed_at>=cast(:at as timestamptz)-interval '28 days'
     and (r.completed_at at time zone 'UTC')::date<(cast(:at as timestamptz) at time zone 'UTC')::date
    order by (r.completed_at at time zone 'UTC')::date,r.id desc
   ) select p.region,p.country,p.tier,d.completed_at,count(*) players
    from daily d join global_leaderboard_players p on p.run_id=d.id
    where p.country is not null and not p.carried_forward and not p.stats_ambiguous
    group by p.region,p.country,p.tier,d.completed_at
   """,p);
  var inventory=sql.queryForList("select region,coalesce(country,'unknown') country,tier,case when rr>=150 then '150+' else 'below150' end rr_band,count(*) players from global_leaderboard_players where run_id=:run group by region,country,tier,rr_band",p);
  var result=new LinkedHashMap<String,Object>();result.put("snapshotId",p.get("run"));result.put("completedAt",p.get("at"));result.put("recoveries",recoveries);result.put("history",history);result.put("inventory",inventory);result.put("countryStats",countryStats);result.put("policyVersion",3);result.put("serverAccess",serverAccess);result.put("missingLabelOdds","policy weights; no measured missing-label odds");cached=Collections.unmodifiableMap(result);
  cachedProfileRevision=profileRevision;return cached;
 }
}
