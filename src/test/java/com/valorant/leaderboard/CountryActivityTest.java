package com.valorant.leaderboard;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class CountryActivityTest {
 @Test @EnabledIfEnvironmentVariable(named="LEADERBOARD_TEST_DB_URL",matches=".+")
 void countsObservedCrossingsAndActivityWithoutInventingGames()throws Exception{
  String url=System.getenv("LEADERBOARD_TEST_DB_URL"),user=System.getenv("LEADERBOARD_TEST_DB_USERNAME"),pw=System.getenv("LEADERBOARD_TEST_DB_PASSWORD"),schema="activity_test_"+UUID.randomUUID().toString().replace("-","");
  var admin=new JdbcTemplate(new DriverManagerDataSource(url,user,pw));admin.execute("create schema "+schema);
  var db=new JdbcTemplate(new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,user,pw));
  try{
   for(String file:List.of("V3__global_leaderboard_snapshots.sql","V4__hourly_regional_observation_cache.sql"))for(String statement:new String(getClass().getResourceAsStream("/db/migration/"+file).readAllBytes()).split(";"))if(!statement.isBlank())db.execute(statement);
   var end=OffsetDateTime.parse("2026-10-21T00:40:00Z");
   for(int run=1;run<=3;run++){
    var stamp=end.minusMinutes((3-run)*20L);db.update("insert into global_leaderboard_runs(id,act,started_at,completed_at,status) values(?,'test',?,?,'COMPLETE')",run,stamp,stamp);
    for(String player:List.of("climb","fall","balanced","unchanged","duplicate","carried","conflict","countrychange","reset","outside")){
     String tier=switch(player){case "climb"->run==1?"Immortal 1":run==2?"Immortal 2":"Radiant";case "fall"->run==1?"Radiant":run==2?"Immortal 3":"Immortal 2";default->"Immortal 1";};
     int rr=player.equals("unchanged")||player.equals("duplicate")||player.equals("balanced")?50:player.equals("fall")?100-run:50+run;
     int wins=player.equals("unchanged")||player.equals("duplicate")?10:player.equals("reset")?20-run:10+run;
     var source=player.equals("duplicate")?end.minusMinutes(40):player.equals("outside")?end.minusDays(1):stamp;
     db.update("insert into global_leaderboard_players(run_id,region,player_key,display_name,country,rr,tier,wins,source_updated_at,carried_forward,stats_ambiguous) values(?,'ap',?,?,?,?,?,?,?,?,?)",run,player,player,player.equals("countrychange")&&run!=2?"sg":"in",rr,tier,wins,source,player.equals("carried"),player.equals("conflict"));
    }
   }
   var service=new CountryActivity(db,"test");var result=service.get("ap",60);
   @SuppressWarnings("unchecked")var rows=(List<Map<String,Object>>)result.get("countries");var india=rows.stream().filter(r->r.get("country").equals("in")).findFirst().orElseThrow();
   assertEquals(4,((Number)india.get("comparable")).intValue());assertEquals(3,((Number)india.get("active")).intValue());assertEquals(6,((Number)india.get("wins_added")).intValue());
   for(String k:List.of("up12","up23","up34","down43","down32"))assertEquals(1,((Number)india.get(k)).intValue(),k);
   assertEquals(1,((Number)india.get("rr_positive")).intValue());assertEquals(1,((Number)india.get("rr_negative")).intValue());assertEquals(2,((Number)india.get("losses_added")).intValue(),"Two decreasing intervals from one player count as two estimated losses");
   assertEquals(0,((Number)india.get("down21")).intValue());
   assertEquals(3,((Number)result.get("snapshot_count")).intValue());
   assertTrue(((List<?>)service.get("ap",15).get("countries")).isEmpty());assertThrows(Exception.class,()->service.get("all",60));assertThrows(Exception.class,()->service.get("ap",7));
   var overview=service.overview(60);
   @SuppressWarnings("unchecked")var totals=(List<Map<String,Object>>)overview.get("regions");assertEquals(6,totals.size());var ap=totals.stream().filter(r->r.get("region").equals("ap")).findFirst().orElseThrow();assertEquals(8L,ap.get("matches"));assertEquals(6L,ap.get("wins"));assertEquals(2L,ap.get("losses"));
   @SuppressWarnings("unchecked")var top=(List<Map<String,Object>>)overview.get("countries");assertEquals(1,top.size());assertEquals("in",top.getFirst().get("country"));assertEquals("ap",top.getFirst().get("region"));assertEquals(8L,top.getFirst().get("matches"));
   assertEquals(30,db.queryForObject("select count(*) from global_leaderboard_players",Integer.class));
  }finally{admin.execute("drop schema "+schema+" cascade");}
 }
}
