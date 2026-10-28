package com.valorant.leaderboard.matchintelligence;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.DriverManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Uses only a uniquely named disposable schema inside the dedicated M1/M2 integration-test database. */
@EnabledIfEnvironmentVariable(named="LEADERBOARD_TEST_DB_URL", matches=".*ranked_observatory_redesign_test.*")
class PostgresMatchIntelligenceIntegrationTest {
  @Test void migratesAndPersistsDuplicateImportInIsolatedSchema() throws Exception {
    String baseUrl=System.getenv("LEADERBOARD_TEST_DB_URL");
    String user=System.getenv("LEADERBOARD_TEST_DB_USERNAME");
    String password=System.getenv("LEADERBOARD_TEST_DB_PASSWORD");
    String schema="mi_acceptance_"+UUID.randomUUID().toString().replace("-", "");
    try(var connection=DriverManager.getConnection(baseUrl,user,password);
        var statement=connection.createStatement()) {
      statement.execute("create schema "+schema);
    }
    try {
      String scopedUrl=baseUrl+(baseUrl.contains("?")?"&":"?")+"currentSchema="+schema;
      DriverManagerDataSource source=new DriverManagerDataSource(scopedUrl,user,password);
      Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration")
          .cleanDisabled(true).load().migrate();
      var db=new JdbcTemplate(source); var mapper=new ObjectMapper().findAndRegisterModules();
      var store=new MatchIntelligenceStore(db,mapper,new MatchExportParser(mapper),new DataSourceTransactionManager(source),90);
      String payload=MatchIntelligenceFixtures.JSON.writeValueAsString(MatchIntelligenceFixtures.export(2,2));
      var first=store.importExport("synthetic-owner",payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      var allMatches=store.listMatches("synthetic-owner",null,0,20);
      assertEquals(1,allMatches.total());
      assertEquals(MatchIntelligenceFixtures.MATCH_ID,allMatches.matches().getFirst().providerMatchId());
      var playerMatches=store.listMatches("synthetic-owner",MatchIntelligenceFixtures.PLAYER,0,20);
      assertEquals(1,playerMatches.total());
      assertEquals(0,store.listMatches("synthetic-owner","Other#TAG",0,20).total());
      int eventCount=db.queryForObject("select count(*) from mi_match_events",Integer.class);
      var second=store.importExport("synthetic-owner",payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      assertEquals("COMPLETE",first.status()); assertEquals("COMPLETE",second.status());
      assertEquals(1,db.queryForObject("select count(*) from mi_matches",Integer.class));
      assertEquals(1,db.queryForObject("select count(*) from mi_match_versions",Integer.class));
      assertTrue(eventCount>0);
      assertEquals(eventCount,db.queryForObject("select count(*) from mi_match_events",Integer.class));
      assertEquals(20,db.queryForObject("select count(*) from mi_player_round_stats",Integer.class));
      assertEquals(2,db.queryForObject("select count(*) from mi_import_job_matches",Integer.class));
      store.deleteImport("synthetic-owner",first.jobId());
      assertEquals(1,db.queryForObject("select count(*) from mi_match_versions",Integer.class));
      store.deleteImport("synthetic-owner",second.jobId());
      assertEquals(0,db.queryForObject("select count(*) from mi_match_versions",Integer.class));
    } finally {
      try(var connection=DriverManager.getConnection(baseUrl,user,password);
          var statement=connection.createStatement()) {
        statement.execute("drop schema if exists "+schema+" cascade");
      }
    }
  }

  @Test void migratesAndPersistsOwnerScopedBenchmarkCohorts() throws Exception {
    String baseUrl=System.getenv("LEADERBOARD_TEST_DB_URL");
    String user=System.getenv("LEADERBOARD_TEST_DB_USERNAME");
    String password=System.getenv("LEADERBOARD_TEST_DB_PASSWORD");
    String schema="mi_benchmark_"+UUID.randomUUID().toString().replace("-","");
    try(var connection=DriverManager.getConnection(baseUrl,user,password);var statement=connection.createStatement()) {
      statement.execute("create schema "+schema);
    }
    try {
      String scopedUrl=baseUrl+(baseUrl.contains("?")?"&":"?")+"currentSchema="+schema;
      DriverManagerDataSource source=new DriverManagerDataSource(scopedUrl,user,password);
      Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").cleanDisabled(true).load().migrate();
      var mapper=new ObjectMapper().findAndRegisterModules(); var db=new JdbcTemplate(source);
      var store=new MatchIntelligenceStore(db,mapper,new MatchExportParser(mapper),new DataSourceTransactionManager(source),90);
      var imported=store.importExport("benchmark-owner",MatchIntelligenceFixtures.JSON.writeValueAsBytes(MatchIntelligenceFixtures.export(2,2)));
      var service=new MatchBenchmarkService(db,mapper,new DeterministicMatchAnalyzer());
      List<MatchBenchmarkService.MemberInput> members=new java.util.ArrayList<>();
      for(int rank=1;rank<=10;rank++) members.add(new MatchBenchmarkService.MemberInput(rank,
          rank==1?MatchIntelligenceFixtures.PLAYER:"Radiant "+rank+"#TAG","Selected #"+rank,20));
      var cohort=service.create("benchmark-owner",new MatchBenchmarkService.CreateInput("Synthetic owner cohort","ap","synthetic-act",
          java.time.LocalDate.parse("2026-10-27"),"OWNER_REPORTED_PERMISSION",members));
      assertEquals(10,cohort.members().size()); assertEquals(1,cohort.uniqueMatches());
      assertTrue(cohort.members().getFirst().identityAvailable());
      assertEquals(1,cohort.members().getFirst().matchesSuccessfullyImported());
      assertTrue(cohort.dataAccessStatusMeaning().contains("not independently verified"));
      assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.get("another-owner",cohort.cohortId()));
      assertEquals(10,db.queryForObject("select count(*) from mi_benchmark_memberships where cohort_id=?",Integer.class,cohort.cohortId()));
      var annotations=new TacticalAnnotationService(db,store);
      var input=new TacticalAnnotationService.Input(1,null,null,"OPENING","Synthetic reviewer note",null,false,60,null,"agreement-1");
      var annotation=annotations.create("benchmark-owner",MatchIntelligenceFixtures.MATCH_ID,input);
      assertEquals(1,annotation.revision());
      var corrected=annotations.correct("benchmark-owner",annotation.annotationId(),1,
          new TacticalAnnotationService.Input(1,null,null,"OPENING","Corrected synthetic note",null,false,70,null,"agreement-1"),false);
      assertEquals(2,corrected.revision());
      assertThrows(org.springframework.web.server.ResponseStatusException.class,()->annotations.correct("benchmark-owner",annotation.annotationId(),1,input,false));
      var withdrawn=annotations.withdraw("benchmark-owner",annotation.annotationId(),2);
      assertEquals("WITHDRAWN",withdrawn.status());assertEquals(3,withdrawn.revision());
      assertEquals("WITHDRAWN",annotations.list("benchmark-owner",MatchIntelligenceFixtures.MATCH_ID).getFirst().status());
      store.deleteImport("benchmark-owner",imported.jobId());
      assertEquals(0,service.get("benchmark-owner",cohort.cohortId()).uniqueMatches());
      assertEquals(0,db.queryForObject("select count(*) from mi_tactical_annotations",Integer.class));
    } finally {
      try(var connection=DriverManager.getConnection(baseUrl,user,password);var statement=connection.createStatement()) { statement.execute("drop schema if exists "+schema+" cascade"); }
    }
  }

  @Test void forcedLateSqlFailureRollsBackWholeImport() throws Exception {
    String baseUrl=System.getenv("LEADERBOARD_TEST_DB_URL"), user=System.getenv("LEADERBOARD_TEST_DB_USERNAME"), password=System.getenv("LEADERBOARD_TEST_DB_PASSWORD");
    String schema="mi_rollback_"+UUID.randomUUID().toString().replace("-","");
    try(var connection=DriverManager.getConnection(baseUrl,user,password);var statement=connection.createStatement()){statement.execute("create schema "+schema);}
    try {
      String scopedUrl=baseUrl+(baseUrl.contains("?")?"&":"?")+"currentSchema="+schema;
      DriverManagerDataSource source=new DriverManagerDataSource(scopedUrl,user,password);
      Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").cleanDisabled(true).load().migrate();
      var db=new JdbcTemplate(source); var mapper=new ObjectMapper().findAndRegisterModules();
      var store=new MatchIntelligenceStore(db,mapper,new MatchExportParser(mapper),new DataSourceTransactionManager(source),90);
      db.execute("create function reject_import_link() returns trigger language plpgsql as $$ begin raise exception 'synthetic forced failure'; end $$");
      db.execute("create trigger reject_import_link before insert on mi_import_job_matches for each row execute function reject_import_link()");
      byte[] payload=MatchIntelligenceFixtures.JSON.writeValueAsBytes(MatchIntelligenceFixtures.export(2,2));
      assertThrows(org.springframework.dao.DataAccessException.class,()->store.importExport("rollback-owner",payload));
      assertEquals(0,db.queryForObject("select count(*) from mi_import_jobs",Integer.class));
      assertEquals(0,db.queryForObject("select count(*) from mi_raw_imports",Integer.class));
      assertEquals(0,db.queryForObject("select count(*) from mi_matches",Integer.class));
      assertEquals(0,db.queryForObject("select count(*) from mi_match_versions",Integer.class));
      assertEquals(0,db.queryForObject("select count(*) from mi_match_events",Integer.class));
    } finally {
      try(var connection=DriverManager.getConnection(baseUrl,user,password);var statement=connection.createStatement()){statement.execute("drop schema if exists "+schema+" cascade");}
    }
  }

  @Test void concurrentDuplicateImportsShareOneCanonicalVersion() throws Exception {
    String baseUrl=System.getenv("LEADERBOARD_TEST_DB_URL"), user=System.getenv("LEADERBOARD_TEST_DB_USERNAME"), password=System.getenv("LEADERBOARD_TEST_DB_PASSWORD");
    String schema="mi_concurrent_"+UUID.randomUUID().toString().replace("-","");
    try(var connection=DriverManager.getConnection(baseUrl,user,password);var statement=connection.createStatement()){statement.execute("create schema "+schema);}
    try {
      String scopedUrl=baseUrl+(baseUrl.contains("?")?"&":"?")+"currentSchema="+schema;
      DriverManagerDataSource source=new DriverManagerDataSource(scopedUrl,user,password);
      Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").cleanDisabled(true).load().migrate();
      var db=new JdbcTemplate(source); var mapper=new ObjectMapper().findAndRegisterModules();
      var store=new MatchIntelligenceStore(db,mapper,new MatchExportParser(mapper),new DataSourceTransactionManager(source),90);
      byte[] payload=MatchIntelligenceFixtures.JSON.writeValueAsBytes(MatchIntelligenceFixtures.export(2,2));
      var ready=new java.util.concurrent.CountDownLatch(2); var start=new java.util.concurrent.CountDownLatch(1);
      var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
      try {
        var one=pool.submit(()->{ready.countDown();start.await();return store.importExport("parallel-owner",payload);});
        var two=pool.submit(()->{ready.countDown();start.await();return store.importExport("parallel-owner",payload);});
        assertTrue(ready.await(5,java.util.concurrent.TimeUnit.SECONDS)); start.countDown();
        assertEquals("COMPLETE",one.get(30,java.util.concurrent.TimeUnit.SECONDS).status());
        assertEquals("COMPLETE",two.get(30,java.util.concurrent.TimeUnit.SECONDS).status());
      } finally { pool.shutdownNow(); }
      assertEquals(2,db.queryForObject("select count(*) from mi_import_jobs",Integer.class));
      assertEquals(1,db.queryForObject("select count(*) from mi_matches",Integer.class));
      assertEquals(1,db.queryForObject("select count(*) from mi_match_versions",Integer.class));
      assertEquals(20,db.queryForObject("select count(*) from mi_player_round_stats",Integer.class));
      assertEquals(2,db.queryForObject("select count(*) from mi_import_job_matches",Integer.class));
    } finally {
      try(var connection=DriverManager.getConnection(baseUrl,user,password);var statement=connection.createStatement()){statement.execute("drop schema if exists "+schema+" cascade");}
    }
  }

  @Test
  @EnabledIfEnvironmentVariable(named="LEADERBOARD_TEST_MATCH_EXPORT", matches=".+")
  void importsTheLocalFiveMatchSampleWithoutPrintingIt() throws Exception {
    String baseUrl=System.getenv("LEADERBOARD_TEST_DB_URL");
    String user=System.getenv("LEADERBOARD_TEST_DB_USERNAME");
    String password=System.getenv("LEADERBOARD_TEST_DB_PASSWORD");
    String schema="mi_sample_"+UUID.randomUUID().toString().replace("-", "");
    try(var connection=DriverManager.getConnection(baseUrl,user,password);
        var statement=connection.createStatement()) {
      statement.execute("create schema "+schema);
    }
    try {
      String scopedUrl=baseUrl+(baseUrl.contains("?")?"&":"?")+"currentSchema="+schema;
      DriverManagerDataSource source=new DriverManagerDataSource(scopedUrl,user,password);
      Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration")
          .cleanDisabled(true).load().migrate();
      var mapper=new ObjectMapper().findAndRegisterModules();
      var store=new MatchIntelligenceStore(new JdbcTemplate(source),mapper,new MatchExportParser(mapper),
          new DataSourceTransactionManager(source),90);
      byte[] payload=Files.readAllBytes(Path.of(System.getenv("LEADERBOARD_TEST_MATCH_EXPORT")));
      var receipt=store.importExport("synthetic-owner",payload);
      assertEquals("COMPLETE",receipt.status());
      assertEquals(5,receipt.acceptedMatches());
      assertEquals(0,receipt.partialMatches());
      assertEquals(0,receipt.rejectedMatches());
      assertEquals(5,new JdbcTemplate(source).queryForObject("select count(*) from mi_match_versions",Integer.class));
      store.deleteImport("synthetic-owner",receipt.jobId());
    } finally {
      try(var connection=DriverManager.getConnection(baseUrl,user,password);
          var statement=connection.createStatement()) {
        statement.execute("drop schema if exists "+schema+" cascade");
      }
    }
  }
}
