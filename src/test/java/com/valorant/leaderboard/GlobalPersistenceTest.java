package com.valorant.leaderboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalPersistenceTest {
  private List<TrackerLeaderboardClient.Player> world(int rating,int rank){
    return List.of(new TrackerLeaderboardClient.Player("same#id",null,rank,rating,"Radiant",10,"ap"),
      new TrackerLeaderboardClient.Player("same#id","kr",20,600,"Radiant",10,"eu"),
      new TrackerLeaderboardClient.Player("korea#id","kr",30,550,"Radiant",10,"kr"),
      new TrackerLeaderboardClient.Player("north#id","us",40,500,"Radiant",10,"na"),
      new TrackerLeaderboardClient.Player("brazil#id","br",50,450,"Radiant",10,"br"),
      new TrackerLeaderboardClient.Player("latin#id","ar",60,400,"Radiant",10,"latam"));
  }
  @Test @EnabledIfEnvironmentVariable(named="LEADERBOARD_TEST_DB_URL",matches=".+")
  void filtersUnknownsHistoryAndAtomicPublicationWorkOnPostgres()throws Exception {
    String url=System.getenv("LEADERBOARD_TEST_DB_URL"),user=System.getenv("LEADERBOARD_TEST_DB_USERNAME"),password=System.getenv("LEADERBOARD_TEST_DB_PASSWORD");
    var admin=new JdbcTemplate(new DriverManagerDataSource(url,user,password));String schema="global_test_"+UUID.randomUUID().toString().replace("-","");admin.execute("create schema "+schema);
    var scoped=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,user,password);var db=new JdbcTemplate(scoped);
    try{
      for(String file:List.of("V1__ranked_leaderboard_history.sql","V2__duplicate_observation_diagnostics.sql","V3__global_leaderboard_snapshots.sql","V4__hourly_regional_observation_cache.sql","V5__observatory_accounts.sql")){
        String source=new String(getClass().getResourceAsStream("/db/migration/"+file).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        for(String statement:source.split(";"))if(!statement.isBlank())db.execute(statement);
      }
      var client=mock(TrackerLeaderboardClient.class);var service=new GlobalLeaderboardService(db,client,new ObjectMapper(),new DataSourceTransactionManager(scoped));
      ReflectionTestUtils.setField(service,"act","test-act");ReflectionTestUtils.setField(service,"maxPages",4);ReflectionTestUtils.setField(service,"delay",0L);
      try{
        var now=OffsetDateTime.now();when(client.fetchRegion("test-act","global",1)).thenReturn(new TrackerLeaderboardClient.Page(world(500,100),now));
        ReflectionTestUtils.invokeMethod(service,"collectSnapshot");
        assertEquals(6,service.regions("all").size());assertEquals(1L,service.players("ap","unknown","",1,100).get("total"));
        assertEquals(0L,service.players("eu","unknown","",1,100).get("total"));assertEquals(2L,service.players("all","kr","",1,100).get("total"));
        assertEquals(2,((List<?>)service.players("all","all","",1,2).get("items")).size());
        when(client.fetchRegion("test-act","global",1)).thenReturn(new TrackerLeaderboardClient.Page(world(510,90),now.plusMinutes(30)));
        ReflectionTestUtils.invokeMethod(service,"collectSnapshot");
        @SuppressWarnings("unchecked") var rows=(List<Map<String,Object>>)service.players("ap","unknown","",1,100).get("items");var row=rows.getFirst();
        assertEquals(10,((Number)row.get("rr_change")).intValue());assertEquals(10,((Number)row.get("rank_gain")).intValue());assertNull(row.get("regional_rank"));
        var playerSummary=service.playerSummary("SAME#ID","ap");
        @SuppressWarnings("unchecked") var currentSummary=(List<Map<String,Object>>)playerSummary.get("current");
        @SuppressWarnings("unchecked") var bestSummary=(Map<String,Object>)playerSummary.get("best");
        assertEquals(510,currentSummary.getFirst().get("rr"));assertEquals(510,bestSummary.get("best_rr"));assertEquals(90,bestSummary.get("best_global_rank"));assertEquals(2L,bestSummary.get("observation_count"));
        @SuppressWarnings("unchecked") var separateRegion=(List<Map<String,Object>>)service.playerSummary("same#id","eu").get("current");assertEquals(600,separateRegion.getFirst().get("rr"));
        db.update("insert into leaderboard_runs(id,act,country,started_at,completed_at,status) values(1,'test-act','in',current_timestamp,current_timestamp,'COMPLETE')");
        db.update("insert into leaderboard_players(run_id,player_key,country,regional_rank,rr,tier,wins) values(1,'same#id','in',100,450,'Radiant',5)");
        assertEquals(3,service.history("SAME#ID","ap").size());assertEquals(2,service.history("same#id","eu").size());
        assertEquals(2,service.countryHistory("ap","unknown").size());
        var conflict=new ArrayList<>(world(510,90));conflict.add(new TrackerLeaderboardClient.Player("same#id",null,89,511,"Radiant",10,"ap"));
        when(client.fetchRegion("test-act","global",1)).thenReturn(new TrackerLeaderboardClient.Page(conflict,now.plusMinutes(60)));
        ReflectionTestUtils.invokeMethod(service,"collectSnapshot");
        assertEquals(1L,service.comparison("ap","unknown").getFirst().get("radiant"));
        assertEquals(0L,service.comparison("ap","unknown").getFirst().get("radiant_confirmed"));
        @SuppressWarnings("unchecked") var uncertain=(List<Map<String,Object>>)service.players("ap","unknown","",1,100).get("items");assertNull(uncertain.getFirst().get("rr_change"));
        when(client.fetchRegion("test-act","global",1)).thenReturn(new TrackerLeaderboardClient.Page(List.of(world(520,80).getFirst()),now));
        ReflectionTestUtils.invokeMethod(service,"collectSnapshot");
        assertEquals(1,db.queryForObject("select count(*) from global_leaderboard_runs where status='FAILED'",Integer.class));
        assertEquals(18,db.queryForObject("select count(*) from global_leaderboard_players",Integer.class));assertEquals(6L,service.players("all","all","",1,100).get("total"));
        // Dynamic thresholds use the entire region; half-open bands must not overlap
        // when the observed Radiant cutoff is above half of the leader's RR.
        long latest=db.queryForObject("select max(id) from global_leaderboard_runs where status='COMPLETE'",Long.class);
        db.update("update global_leaderboard_players set rr=600,stats_ambiguous=false where run_id=? and region='ap'",latest);
        for(int rating:new int[]{0,99,100,299,300,509,510,800,899,900,1000}){
          db.update("insert into global_leaderboard_players(run_id,region,player_key,display_name,country,rr,tier,wins,source_updated_at,rank_ambiguous,stats_ambiguous) values(?,'ap',?,?,null,?,?,1,current_timestamp,false,false)",latest,"boundary"+rating,"boundary"+rating,rating,rating==1000?"Radiant":"Immortal 1");
        }
        var bands=service.comparison("ap","unknown").getFirst();
        assertEquals(600,((Number)bands.get("radiant_threshold")).intValue());
        assertEquals(1000,((Number)bands.get("top_rr")).intValue());
        for(var expected:Map.of("rr_0_100",2,"rr_100_300",2,"rr_300_cutoff",3,"rr_cutoff_half",0,"rr_half_ninety",3,"rr_top_ten",2).entrySet())
          assertEquals(expected.getValue().longValue(),((Number)bands.get(expected.getKey())).longValue(),expected.getKey());
        assertEquals(12L,bands.get("players"));assertEquals(10L,bands.get("immortal1"));
        assertEquals(12L,service.regions("unknown").stream().filter(r->r.get("region").equals("ap")).findFirst().orElseThrow().get("players"));
        @SuppressWarnings("unchecked") var insight=(Map<String,Object>)service.insights("ap","unknown").get("summary");
        assertEquals(true,insight.get("has_previous"));assertEquals(12L,insight.get("players"));assertEquals(11L,insight.get("entered"));
        // Out-of-order network completion must still publish page-order first observations.
        var firstPage=new ArrayList<>(world(700,1));
        for(int n=0;n<94;n++)firstPage.add(new TrackerLeaderboardClient.Player("first"+n,"in",n+7,10,"Immortal 1",1,"ap"));
        var secondPage=new ArrayList<TrackerLeaderboardClient.Player>();
        secondPage.add(new TrackerLeaderboardClient.Player("same#id",null,2,710,"Radiant",11,"ap"));
        for(int n=0;n<99;n++)secondPage.add(new TrackerLeaderboardClient.Player("second"+n,"in",n+102,10,"Immortal 1",1,"ap"));
        var secondFinished=new java.util.concurrent.CountDownLatch(1);
        when(client.fetchRegion("test-act","global",1)).thenAnswer(call->{assertTrue(secondFinished.await(3,java.util.concurrent.TimeUnit.SECONDS));return new TrackerLeaderboardClient.Page(firstPage,now);});
        when(client.fetchRegion("test-act","global",2)).thenAnswer(call->{secondFinished.countDown();return new TrackerLeaderboardClient.Page(secondPage,now);});
        when(client.fetchRegion("test-act","global",3)).thenReturn(new TrackerLeaderboardClient.Page(secondPage,now));
        when(client.fetchRegion("test-act","global",4)).thenReturn(new TrackerLeaderboardClient.Page(List.of(),now));
        ReflectionTestUtils.invokeMethod(service,"collectSnapshot");
        assertEquals(199L,service.players("all","all","",1,100).get("total"));
        @SuppressWarnings("unchecked") var ordered=(List<Map<String,Object>>)service.players("ap","unknown","",1,100).get("items");
        assertEquals(700,ordered.getFirst().get("rr"));assertEquals(true,ordered.getFirst().get("stats_ambiguous"));
        // Hourly reconciliation seeds an independent regional cache.
        reset(client);ReflectionTestUtils.setField(service,"regionalFallback",true);
        var regionalTime=OffsetDateTime.now().minusMinutes(5).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        when(client.fetchRegion("test-act","global",1)).thenReturn(new TrackerLeaderboardClient.Page(world(800,1),now));
        for(String shard:GlobalLeaderboardService.REGIONS.keySet()){
          var local=new ArrayList<>(world(800,1).stream().filter(p->p.region().equals(shard)).toList());
          if(shard.equals("ap"))local.add(new TrackerLeaderboardClient.Player("regional-only#test","in",42,275,"Immortal 2",4,"ap"));
          when(client.fetchRegion("test-act",shard,1)).thenReturn(new TrackerLeaderboardClient.Page(local,regionalTime));
          for(int pageNumber=2;pageNumber<=4;pageNumber++)when(client.fetchRegion("test-act",shard,pageNumber)).thenReturn(new TrackerLeaderboardClient.Page(List.of(),regionalTime));
        }
        for(int pageNumber=2;pageNumber<=4;pageNumber++)when(client.fetchRegion("test-act","global",pageNumber)).thenReturn(new TrackerLeaderboardClient.Page(List.of(),now));
        ReflectionTestUtils.invokeMethod(service,"collectSnapshot");
        assertEquals(7L,service.players("all","all","",1,100).get("total"));
        assertEquals(7,db.queryForObject("select count(*) from regional_observation_cache",Integer.class));
        assertEquals(1,service.history("regional-only#test","ap").size());
        clearInvocations(client);
        when(client.fetchRegion("test-act","global",1)).thenReturn(new TrackerLeaderboardClient.Page(world(810,1),now.plusMinutes(20)));
        ReflectionTestUtils.invokeMethod(service,"collectSnapshot");
        for(String shard:GlobalLeaderboardService.REGIONS.keySet())verify(client,never()).fetchRegion(eq("test-act"),eq(shard),anyInt());
        @SuppressWarnings("unchecked") var carried=(List<Map<String,Object>>)service.players("ap","in","regional-only",1,20).get("items");
        assertEquals(1,carried.size());assertEquals(true,carried.getFirst().get("carried_forward"));assertEquals("CARRIED_FORWARD",carried.getFirst().get("movement"));
        assertEquals(275,carried.getFirst().get("rr"));assertNull(carried.getFirst().get("rr_change"));assertNull(carried.getFirst().get("rank_gain"));
        assertEquals(regionalTime.toInstant(),((java.sql.Timestamp)carried.getFirst().get("source_updated_at")).toInstant());
        assertEquals(1,service.history("regional-only#test","ap").size());
        @SuppressWarnings("unchecked") var fresh=(List<Map<String,Object>>)service.players("ap","unknown","same",1,20).get("items");
        assertEquals(false,fresh.getFirst().get("carried_forward"));assertEquals(10,fresh.getFirst().get("rr_change"));assertNull(fresh.getFirst().get("regional_rank_gain"));
        ReflectionTestUtils.invokeMethod(service,"collectSnapshot");assertEquals(1,service.history("regional-only#test","ap").size());
        // A new global sighting replaces stale statistics, without inventing a delta from a carry.
        var returned=new ArrayList<>(world(820,1));returned.add(new TrackerLeaderboardClient.Player("regional-only#test","in",7,300,"Immortal 3",5,"ap"));
        when(client.fetchRegion("test-act","global",1)).thenReturn(new TrackerLeaderboardClient.Page(returned,now.plusMinutes(40)));
        ReflectionTestUtils.invokeMethod(service,"collectSnapshot");
        @SuppressWarnings("unchecked") var returnedRows=(List<Map<String,Object>>)service.players("ap","in","regional-only",1,20).get("items");
        assertEquals(false,returnedRows.getFirst().get("carried_forward"));assertEquals(300,returnedRows.getFirst().get("rr"));assertNull(returnedRows.getFirst().get("rr_change"));
        assertEquals(2,service.history("regional-only#test","ap").size());
        // Failed reconciliation leaves the published snapshot and independent cache intact.
        db.update("update global_leaderboard_runs set regional_reconciled_at=current_timestamp-interval '2 hours' where regional_reconciled_at is not null");
        long beforeFailure=db.queryForObject("select max(id) from global_leaderboard_runs where status='COMPLETE'",Long.class);
        when(client.fetchRegion("test-act","na",1)).thenThrow(new IllegalStateException("regional source unavailable"));
        ReflectionTestUtils.invokeMethod(service,"collectSnapshot");
        assertEquals(beforeFailure,db.queryForObject("select max(id) from global_leaderboard_runs where status='COMPLETE'",Long.class));
        assertEquals(7,db.queryForObject("select count(*) from regional_observation_cache",Integer.class));
        // Next successful reconciliation expires a player absent from both current sources.
        when(client.fetchRegion("test-act","global",1)).thenReturn(new TrackerLeaderboardClient.Page(world(830,1),now.plusMinutes(60)));
        for(String shard:GlobalLeaderboardService.REGIONS.keySet())doReturn(new TrackerLeaderboardClient.Page(world(830,1).stream().filter(p->p.region().equals(shard)).toList(),now.plusMinutes(60))).when(client).fetchRegion("test-act",shard,1);
        ReflectionTestUtils.invokeMethod(service,"collectSnapshot");
        assertEquals(6L,service.players("all","all","",1,100).get("total"));assertEquals(6,db.queryForObject("select count(*) from regional_observation_cache",Integer.class));
        assertEquals(2,service.history("regional-only#test","ap").size());
        // Time windows select real completed snapshots; direction and stale-row rules apply.
        long earliest=db.queryForObject("select min(id) from global_leaderboard_runs where status='COMPLETE'",Long.class);
        db.update("update global_leaderboard_runs set completed_at=current_timestamp-interval '25 minutes' where id=?",earliest);
        var shortWindow=service.trends("ap","all",15,"increment");
        @SuppressWarnings("unchecked") var shortMovers=(List<Map<String,Object>>)shortWindow.get("movers");
        assertEquals(1,shortMovers.size());assertEquals(510,shortMovers.getFirst().get("previous_rr"));assertEquals(830,shortMovers.getFirst().get("current_rr"));assertEquals(320,shortMovers.getFirst().get("rr_change"));
        @SuppressWarnings("unchecked") var longMovers=(List<Map<String,Object>>)service.trends("ap","all",30,"overall").get("movers");
        assertEquals(500,longMovers.getFirst().get("previous_rr"));assertEquals(330,longMovers.getFirst().get("rr_change"));
        assertTrue(((List<?>)service.trends("ap","all",30,"decrement").get("movers")).isEmpty());
        db.update("update global_leaderboard_players set source_updated_at=current_timestamp-interval '2 hours' where run_id=? and region='ap'",earliest);
        @SuppressWarnings("unchecked") var sourceWindow=(List<Map<String,Object>>)service.trends("ap","all",30,"increment").get("movers");
        assertEquals(510,sourceWindow.getFirst().get("previous_rr"));assertEquals(320,sourceWindow.getFirst().get("rr_change"));

        assertFalse(((List<?>)shortWindow.get("regionPoints")).isEmpty());
        long current=db.queryForObject("select max(id) from global_leaderboard_runs where status='COMPLETE'",Long.class);
        db.update("update global_leaderboard_players set carried_forward=true where run_id=? and region='ap'",current);
        assertTrue(((List<?>)service.trends("ap","all",30,"overall").get("movers")).isEmpty());
        // Percentile groups count Radiant players, rather than percentages of the leader's RR.
        for(int n=0;n<10;n++)db.update("insert into global_leaderboard_players(run_id,region,player_key,display_name,country,rr,tier,wins,source_updated_at) values(?,'ap',?,?,?,?,'Radiant',5,current_timestamp)",current,"percentile"+n,"percentile"+n,n>=5?"in":"ph",600+n*100);
        db.update("insert into global_leaderboard_players(run_id,region,player_key,display_name,country,rr,tier,wins,source_updated_at,stats_ambiguous) values(?,'ap','excluded','excluded','in',2000,'Radiant',5,current_timestamp,true)",current);
        db.update("insert into global_leaderboard_players(run_id,region,player_key,display_name,country,rr,tier,wins,source_updated_at,carried_forward) values(?,'ap','stale','stale','in',1900,'Radiant',5,current_timestamp,true)",current);
        for(int rating:new int[]{575,600})db.update("insert into global_leaderboard_players(run_id,region,player_key,display_name,country,rr,tier,wins,source_updated_at) values(?,'ap',?,?,'in',?,'Immortal 3',5,current_timestamp)",current,"near"+rating,"near"+rating,rating);
        var india=service.comparison("ap","in").getFirst();var philippines=service.comparison("ap","ph").getFirst();
        assertEquals(5L,india.get("radiant_top50"));assertEquals(1L,india.get("radiant_top10"));assertEquals(2L,india.get("near_radiant"));
        assertEquals(0L,philippines.get("radiant_top50"));assertEquals(0L,philippines.get("radiant_top10"));assertEquals(1L,philippines.get("radiant_boundary"));
        @SuppressWarnings("unchecked") var conflictedBest=(Map<String,Object>)service.playerSummary("excluded","ap").get("best");assertNull(conflictedBest.get("best_rr"));
        @SuppressWarnings("unchecked") var carriedBest=(Map<String,Object>)service.playerSummary("stale","ap").get("best");assertNull(carriedBest.get("best_rr"));
        // Median represents the middle player despite a high-RR outlier; conflicts are excluded.
        for(int rating:new int[]{10,20,1000})db.update("insert into global_leaderboard_players(run_id,region,player_key,display_name,country,rr,tier,wins,source_updated_at) values(?,'ap',?,?,'nz',?,'Immortal 1',5,current_timestamp)",current,"median"+rating,"median"+rating,rating);
        db.update("insert into global_leaderboard_players(run_id,region,player_key,display_name,country,rr,tier,wins,source_updated_at,stats_ambiguous) values(?,'ap','median-conflict','median-conflict','nz',9000,'Radiant',5,current_timestamp,true)",current);
        assertEquals(20.0,((Number)service.comparison("ap","nz").getFirst().get("median_rr")).doubleValue());
        db.update("insert into global_leaderboard_players(run_id,region,player_key,display_name,country,rr,tier,wins,source_updated_at) values(?,'ap','median30','median30','nz',30,'Immortal 1',5,current_timestamp)",current);
        assertEquals(25.0,((Number)service.comparison("ap","nz").getFirst().get("median_rr")).doubleValue());
      }finally{service.close();}
    }finally{admin.execute("drop schema "+schema+" cascade");}
  }
}
