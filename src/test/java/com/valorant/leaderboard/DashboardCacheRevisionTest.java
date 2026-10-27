package com.valorant.leaderboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class DashboardCacheRevisionTest {
  @Test void samePageArchiveCorrectionInvalidatesCachedReadsByPublicationRevision() throws Exception {
    var source=new DriverManagerDataSource("jdbc:h2:mem:cache-revision;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
    var db=new JdbcTemplate(source);
    db.execute("create table global_leaderboard_runs(id bigint,act varchar,status varchar)");
    db.execute("create table historical_act_catalog(act varchar primary key,current_act boolean not null)");
    db.execute("create table historical_act_sources(act varchar,pages integer)");
    db.execute("create table leaderboard_publication_revision(scope varchar primary key,revision bigint not null)");
    db.update("insert into global_leaderboard_runs values(1,'current-act','COMPLETE')");
    db.update("insert into historical_act_catalog values('archive-act',false)");
    db.update("insert into historical_act_sources values('archive-act',4)");
    db.update("insert into leaderboard_publication_revision values('current',7),('archive',11)");

    var config=new DashboardCache();
    CacheManager manager=config.dashboardCacheManager(new ObjectMapper());
    ((SimpleCacheManager)manager).afterPropertiesSet();
    KeyGenerator generator=config.snapshotKeyGenerator(db,manager,"current-act");
    Method method=DashboardCacheRevisionTest.class.getDeclaredMethod("cachedRead");
    Object[] args={"archive-act"};
    Object first=generator.generate(this,method,args);
    manager.getCache("dashboardReads").put(first,"old publication");

    // A correction can change published rows without changing the archive's page count.
    db.update("update leaderboard_publication_revision set revision=revision+1 where scope='archive'");
    Object corrected=generator.generate(this,method,args);
    assertNotEquals(first,corrected);
    assertNull(manager.getCache("dashboardReads").get(first));

    manager.getCache("dashboardReads").put(corrected,"new publication");
    db.update("update leaderboard_publication_revision set revision=revision+1 where scope='current'");
    Object currentChanged=generator.generate(this,method,args);
    assertNotEquals(corrected,currentChanged);
    assertNull(manager.getCache("dashboardReads").get(corrected));
  }

  private void cachedRead() {}
}
