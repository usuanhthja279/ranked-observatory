package com.valorant.leaderboard;

import java.util.Map;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.web.bind.annotation.*;

@RestController
public class DashboardCacheStatus {
  private final CacheManager manager;
  public DashboardCacheStatus(CacheManager manager){this.manager=manager;}
  @GetMapping("/api/leaderboard/cache-status")
  public Map<String,Object> status(){
    var cache=((CaffeineCache)manager.getCache("dashboardReads")).getNativeCache();
    var stats=cache.stats();
    return Map.of("entries",cache.estimatedSize(),"hits",stats.hitCount(),"misses",stats.missCount(),
        "loads",stats.loadCount(),"loadFailures",stats.loadFailureCount(),"evictions",stats.evictionCount(),
        "expiryPolicy","next-successful-snapshot","accountingBudgetBytes",16L*1024*1024,"snapshotAware",true);
  }
}
