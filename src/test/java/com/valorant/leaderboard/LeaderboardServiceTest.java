package com.valorant.leaderboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import static org.junit.jupiter.api.Assertions.*;

class LeaderboardServiceTest {
  @Test
  @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="LEADERBOARD_TEST_DB_URL", matches=".+")
  void migrationAndComparisonWorkOnPostgresInIsolatedSchema() throws Exception {
    var ds=new DriverManagerDataSource(System.getenv("LEADERBOARD_TEST_DB_URL"),System.getenv("LEADERBOARD_TEST_DB_USERNAME"),System.getenv("LEADERBOARD_TEST_DB_PASSWORD"));
    String schema="leaderboard_test_"+UUID.randomUUID().toString().replace("-", "");
    var admin=new JdbcTemplate(ds);
    admin.execute("create schema "+schema);
    try {
      var scoped=new DriverManagerDataSource(System.getenv("LEADERBOARD_TEST_DB_URL")+(System.getenv("LEADERBOARD_TEST_DB_URL").contains("?")?"&":"?")+"currentSchema="+schema,System.getenv("LEADERBOARD_TEST_DB_USERNAME"),System.getenv("LEADERBOARD_TEST_DB_PASSWORD"));
      var db=new JdbcTemplate(scoped);
      String sql=new String(getClass().getResourceAsStream("/db/migration/V1__ranked_leaderboard_history.sql").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)+new String(getClass().getResourceAsStream("/db/migration/V2__duplicate_observation_diagnostics.sql").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
      for(String statement:sql.split(";"))if(!statement.isBlank())db.execute(statement);
      var source=org.mockito.Mockito.mock(TrackerLeaderboardClient.class);
      var service=new LeaderboardService(db,source,new DataSourceTransactionManager(scoped),new ObjectMapper());
      org.springframework.test.util.ReflectionTestUtils.setField(service,"act","act-a");
      org.springframework.test.util.ReflectionTestUtils.setField(service,"maxPages",3);
      org.springframework.test.util.ReflectionTestUtils.setField(service,"delay",0L);
      try {
        var player=new TrackerLeaderboardClient.Player("a#b","in",81,537,"Radiant",35);
        var now=java.time.OffsetDateTime.now();
        org.mockito.Mockito.when(source.fetch("act-a","in",1)).thenReturn(new TrackerLeaderboardClient.Page(List.of(player),now));
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"collectCountry","in");
        var result=service.comparison().getFirst();
        assertEquals(1L,((Number)result.get("radiant")).longValue());
        assertEquals(537,((Number)result.get("avg_rr")).intValue());
        assertEquals("BASELINE",service.players("in").getFirst().get("movement"));
        assertEquals(1,service.history("a#b").size());
        var full=new ArrayList<TrackerLeaderboardClient.Player>();
        full.add(player);
        full.add(new TrackerLeaderboardClient.Player("a#b","in",82,537,"Radiant",35));
        for(int i=1;i<99;i++)full.add(new TrackerLeaderboardClient.Player("player"+i+"#id","in",100+i,400,"Immortal 3",10));
        org.mockito.Mockito.when(source.fetch("act-a","in",1)).thenReturn(new TrackerLeaderboardClient.Page(full,now));
        org.mockito.Mockito.when(source.fetch("act-a","in",2)).thenReturn(new TrackerLeaderboardClient.Page(List.of(new TrackerLeaderboardClient.Player("last#id","in",999,300,"Immortal 3",10)),now));
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"collectCountry","in");
        assertEquals(2,service.history("a#b").size());
        assertNull(service.players("in").getFirst().get("rank_gain"));
        assertEquals(1,((Number)service.comparison().getFirst().get("duplicate_rows")).intValue());
        assertEquals(1,((Number)service.comparison().getFirst().get("ambiguous_ranks")).intValue());
        org.mockito.Mockito.verify(source).fetch("act-a","in",2);
        org.mockito.Mockito.when(source.fetch("act-a","in",1)).thenReturn(new TrackerLeaderboardClient.Page(List.of(player,new TrackerLeaderboardClient.Player("a#b","in",81,999,"Radiant",35)),now));
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"collectCountry","in");
        assertEquals(1,db.queryForObject("select count(*) from leaderboard_runs where status='FAILED'",Integer.class));
        assertEquals(101,db.queryForObject("select count(*) from leaderboard_players",Integer.class));
        assertEquals(2,service.history("a#b").size());
      } finally { service.close(); }
    } finally { admin.execute("drop schema "+schema+" cascade"); }
  }
  @Test void historyDeltasIgnoreFailedRunsAndScopeToActAndCountry() throws Exception {
    var ds=new DriverManagerDataSource("jdbc:h2:mem:leaderboard;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");
    var db=new JdbcTemplate(ds);
    String sql=new String(getClass().getResourceAsStream("/db/migration/V1__ranked_leaderboard_history.sql").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)+new String(getClass().getResourceAsStream("/db/migration/V2__duplicate_observation_diagnostics.sql").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
    for(String statement:sql.split(";")) if(!statement.isBlank())db.execute(statement);
    var service=new LeaderboardService(db,new TrackerLeaderboardClient(new ObjectMapper()),new DataSourceTransactionManager(ds),new ObjectMapper());
    org.springframework.test.util.ReflectionTestUtils.setField(service,"act","act-a");
    try {
      for(int id=1;id<=3;id++)db.update("insert into leaderboard_runs(id,act,country,started_at,status) values (?,'act-a','in',current_timestamp,?)",id,id==2?"FAILED":"COMPLETE");
      db.update("insert into leaderboard_players(run_id,player_key,country,regional_rank,rr,tier,wins) values (1,'a#b','in',100,400,'Radiant',10),(3,'a#b','in',81,537,'Radiant',12),(3,'new#id','in',90,450,'Radiant',5)");
      var rows=service.players("in");
      assertEquals(2,rows.size());
      assertEquals(137,((Number)rows.getFirst().get("RR_CHANGE")).intValue());
      assertEquals(19,((Number)rows.getFirst().get("RANK_GAIN")).intValue());
      assertEquals("ENTERED",rows.get(1).get("MOVEMENT"));
      assertTrue(service.players("jp").isEmpty());
      assertEquals(2,service.history("a#b").size());
    } finally { service.close(); }
  }
}
