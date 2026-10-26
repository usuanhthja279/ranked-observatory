package com.valorant.leaderboard;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class CountryModelEvidenceTest {
 @Test @EnabledIfEnvironmentVariable(named="LEADERBOARD_TEST_DB_URL",matches=".+")
 void resolvesOnlyConsistentFreshReportsWithoutModifyingSource()throws Exception{
  String url=System.getenv("LEADERBOARD_TEST_DB_URL"),user=System.getenv("LEADERBOARD_TEST_DB_USERNAME"),pw=System.getenv("LEADERBOARD_TEST_DB_PASSWORD"),schema="country_test_"+UUID.randomUUID().toString().replace("-","");
  var admin=new JdbcTemplate(new DriverManagerDataSource(url,user,pw));admin.execute("create schema "+schema);
  var db=new JdbcTemplate(new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,user,pw));
  try{
   for(String file:List.of("V3__global_leaderboard_snapshots.sql","V4__hourly_regional_observation_cache.sql","V6__reported_profile_countries.sql"))for(String statement:new String(getClass().getResourceAsStream("/db/migration/"+file).readAllBytes()).split(";"))if(!statement.isBlank())db.execute(statement);
   var now=OffsetDateTime.now();
   for(int day=3;day>=0;day--){
    db.update("insert into global_leaderboard_runs(id,act,started_at,completed_at,status) values(?,'test',?,?,'COMPLETE')",4-day,now.minusDays(day),now.minusDays(day));
    for(String key:List.of("stable#id","conflict#id","profile#id","stale#id","known#id")){
     String country=day==0?(key.equals("known#id")?"jp":null):switch(key){case "stable#id"->"in";case "conflict#id"->day==3?"in":"sg";case "stale#id"->"in";default->null;};
     db.update("insert into global_leaderboard_players(run_id,region,player_key,display_name,country,rr,tier,wins,source_updated_at) values(?,'ap',?,?,?,500,'Radiant',1,?)",4-day,key,key,country,key.equals("stale#id")?now.minusDays(10):now.minusDays(day));
    }
   }
   db.update("insert into reported_profile_countries values('profile#id','ph',?),('known#id','sg',?),('conflict#id','in',?)",now,now,now);
   for(int rr:List.of(149,150))db.update("insert into global_leaderboard_players(run_id,region,player_key,display_name,country,rr,tier,wins,source_updated_at) values(4,'ap',?,?,'in',?,'Immortal 2',1,?)","edge"+rr+"#id","edge"+rr+"#id",rr,now);
   var result=new CountryModelEvidence(db,"test").get();
   @SuppressWarnings("unchecked") var recovery=(List<Map<String,Object>>)result.get("recoveries");
   assertEquals(2,recovery.size());assertEquals(Set.of("in","ph"),new HashSet<>(recovery.stream().map(r->r.get("country").toString()).toList()));
   assertEquals(4L,db.queryForObject("select count(*) from global_leaderboard_players where run_id=4 and country is null",Long.class));
   assertEquals(3,result.get("policyVersion"));
   @SuppressWarnings("unchecked") var inventory=(List<Map<String,Object>>)result.get("inventory");
   assertEquals(Set.of("below150","150+"),new HashSet<>(inventory.stream().filter(r->r.get("country").equals("in")&&r.get("tier").equals("Immortal 2")).map(r->r.get("rr_band").toString()).toList()));
   @SuppressWarnings("unchecked") var stats=(List<Map<String,Object>>)result.get("countryStats");assertEquals(150.0,((Number)stats.stream().filter(r->r.get("country").equals("in")).findFirst().orElseThrow().get("median_rr")).doubleValue());
   @SuppressWarnings("unchecked") var history=(List<Map<String,Object>>)result.get("history");assertEquals(3,history.stream().map(r->r.get("completed_at").toString().substring(0,10)).distinct().count());
  }finally{admin.execute("drop schema "+schema+" cascade");}
 }
}
