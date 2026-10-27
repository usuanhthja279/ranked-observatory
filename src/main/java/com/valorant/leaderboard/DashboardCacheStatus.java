package com.valorant.leaderboard;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.web.bind.annotation.*;

@RestController
public class DashboardCacheStatus {
  private final CacheManager manager;
  private final JdbcTemplate db;
  public DashboardCacheStatus(CacheManager manager,JdbcTemplate db){this.manager=manager;this.db=db;}
  @GetMapping("/api/leaderboard/cache-status")
  public Map<String,Object> status(){
    var cache=((CaffeineCache)manager.getCache("dashboardReads")).getNativeCache();
    var stats=cache.stats();
    var result=new java.util.LinkedHashMap<String,Object>();
    result.put("entries",cache.estimatedSize());result.put("hits",stats.hitCount());result.put("misses",stats.missCount());
    result.put("loads",stats.loadCount());result.put("loadFailures",stats.loadFailureCount());result.put("evictions",stats.evictionCount());
    result.put("expiryPolicy","publication-revision-or-next-successful-snapshot");result.put("accountingBudgetBytes",16L*1024*1024);
    result.put("snapshotAware",true);result.put("publicationRevisionAware",true);
    result.put("currentPublicationRevision",db.queryForObject("select revision from leaderboard_publication_revision where scope='current'",Long.class));
    result.put("archivePublicationRevision",db.queryForObject("select revision from leaderboard_publication_revision where scope='archive'",Long.class));
    return result;
  }
}
