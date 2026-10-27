package com.valorant.leaderboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class HistoricalActArchiveSummaryTest {
  @Test void regionalSummaryUsesCanonicalLadderAndSeparatesImmortalSourceLabels(){
    var source=new DriverManagerDataSource("jdbc:h2:mem:archive-summary;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
    var db=new JdbcTemplate(source);
    db.execute("create table historical_act_catalog(act varchar primary key,act_order integer not null)");
    db.execute("create table historical_act_sources(act varchar,source_region varchar,status varchar,primary key(act,source_region))");
    db.execute("create table historical_act_players(act varchar,source_region varchar,player_region varchar,country varchar,player_key varchar,display_name varchar,source_rank integer,rr integer,tier varchar,wins integer,source_updated_at timestamp with time zone)");
    db.update("insert into historical_act_catalog(act,act_order) values('fixture-act',20)");
    db.update("insert into historical_act_sources(act,source_region,status) values('fixture-act','global','COMPLETE'),('fixture-act','ap','COMPLETE'),('fixture-act','eu','COMPLETE'),('fixture-act','na','RUNNING')");
    insert(db,"global","ap","global-ap","in",1,250,"Radiant");
    insert(db,"global","ap","global-ap2","in",2,200,"Immortal 3");
    insert(db,"ap","ap","ap-combined","in",1,120,"Immortal");
    insert(db,"ap","ap","ap-split","in",2,70,"Immortal 2");
    insert(db,"ap","ap","ap-radiant","in",3,250,"Radiant");
    insert(db,"eu","eu","eu-player","fr",1,30,"Diamond 1");
    insert(db,"na","na","incomplete-na","us",1,100,"Immortal 1");

    var service=new HistoricalActArchiveService(db,null,null,new ObjectMapper(),mock(PlatformTransactionManager.class),"current-act");
    try{
      List<Map<String,Object>> rows=service.regions("fixture-act","all");
      var ap=rows.stream().filter(r->"ap".equals(r.get("region"))).findFirst().orElseThrow();
      assertEquals(3,((Number)ap.get("players")).intValue(),"global rows must not replace the APAC regional ladder");
      assertEquals(2,((Number)ap.get("immortal_all")).intValue());
      assertEquals(1,((Number)ap.get("immortal")).intValue(),"legacy field remains combined-only");
      assertEquals(1,((Number)ap.get("combined_immortal")).intValue());
      assertEquals(1,((Number)ap.get("split_immortal")).intValue());
      assertEquals("MIXED",ap.get("tier_detail"));
      assertEquals("completed_regional_source_ladders",ap.get("source_basis"));
      assertEquals("EPISODE_3_BACKCAST",ap.get("threshold_profile"));
      assertEquals(80,((Number)ap.get("immortal2_threshold_rr")).intValue());
      assertEquals(200,((Number)ap.get("immortal3_threshold_rr")).intValue());
      assertEquals("ap-combined",ap.get("top_player"));
      assertTrue(rows.stream().anyMatch(r->"eu".equals(r.get("region"))));
      assertTrue(rows.stream().noneMatch(r->"na".equals(r.get("region"))),"in-progress regional sources must not appear as complete summaries");

      var india=service.countries("fixture-act","ap","all").stream().filter(r->"in".equals(r.get("country"))).findFirst().orElseThrow();
      assertEquals(2,((Number)india.get("immortal_all")).intValue());
      assertEquals(1,((Number)india.get("immortal")).intValue());
      assertEquals("regional_source_ladder",india.get("source_basis"));
    }finally{service.close();}
  }

  @Test void archivedGlobalPlayersApplyValidatedOrderingAndPagination(){
    var source=new DriverManagerDataSource("jdbc:h2:mem:archive-player-page;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
    var db=new JdbcTemplate(source);
    db.execute("create table historical_act_players(act varchar,source_region varchar,player_region varchar,country varchar,player_key varchar,display_name varchar,source_rank integer,rr integer,tier varchar,wins integer,source_updated_at timestamp with time zone)");
    db.update("insert into historical_act_players values('fixture-act','global','ap','in','z-player','Zed#1',2,200,'Immortal 2',10,current_timestamp)");
    db.update("insert into historical_act_players values('fixture-act','global','ap','in','a-player','Alpha#1',1,250,'Radiant',12,current_timestamp)");
    var service=new HistoricalActArchiveService(db,null,null,new ObjectMapper(),mock(PlatformTransactionManager.class),"current-act");
    try{
      var response=service.players("fixture-act","all","all","",1,1,"globalRank","asc");
      assertEquals(2,((Number)response.get("total")).intValue());
      var items=(List<Map<String,Object>>)response.get("items");
      assertEquals(1,items.size());
      assertEquals("Alpha#1",items.get(0).get("display_name"));
      assertEquals(1,((Number)items.get(0).get("global_rank")).intValue());
    }finally{service.close();}
  }

  private static void insert(JdbcTemplate db,String source,String region,String key,String country,int rank,int rr,String tier){
    db.update("insert into historical_act_players(act,source_region,player_region,country,player_key,display_name,source_rank,rr,tier,wins,source_updated_at) values('fixture-act',?,?,?,?,?,?,?, ?,0,current_timestamp)",source,region,country,key,key,rank,rr,tier);
  }
}
