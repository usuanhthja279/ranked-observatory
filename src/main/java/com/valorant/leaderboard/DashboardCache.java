package com.valorant.leaderboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Public snapshot reads only. Collection, authentication and status never enter this cache. */
@Configuration
@EnableCaching
public class DashboardCache {
  record ReadKey(String act,Long snapshot,Long currentRevision,Long archiveRevision,String method,List<Object> arguments,String archiveAct,Long archivePages) {}

  @Bean KeyGenerator snapshotKeyGenerator(JdbcTemplate db,CacheManager manager,
      @Value("${leaderboard.act:d816f426-48ea-f052-117f-9697a155b319}")String act) {
    Object guard=new Object();Long[] observed={null,null};
    return (target,method,args)->{
      Long snapshot=db.queryForObject("select max(id) from global_leaderboard_runs where act=? and status='COMPLETE'",Long.class,act);
      Long currentRevision=db.queryForObject("select revision from leaderboard_publication_revision where scope='current'",Long.class);
      Long archiveRevision=db.queryForObject("select revision from leaderboard_publication_revision where scope='archive'",Long.class);
      synchronized(guard){
        if(!Objects.equals(currentRevision,observed[0])||!Objects.equals(archiveRevision,observed[1])){
          manager.getCache("dashboardReads").clear();observed[0]=currentRevision;observed[1]=archiveRevision;
        }
      }
      String archiveAct=Arrays.stream(args).filter(String.class::isInstance).map(String.class::cast)
          .filter(value->value.matches("[a-fA-F0-9-]{36}")&&!value.equals(act))
          .filter(value->Boolean.FALSE.equals(db.queryForObject("select coalesce((select current_act from historical_act_catalog where act=?),true)",Boolean.class,value)))
          .findFirst().orElse(null);
      Long archivePages=archiveAct==null?null:db.queryForObject("select coalesce(sum(pages),0) from historical_act_sources where act=?",Long.class,archiveAct);
      return new ReadKey(act,snapshot,currentRevision,archiveRevision,method.getDeclaringClass().getName()+"."+method.getName(),Arrays.asList(args.clone()),archiveAct,archivePages);
    };
  }

  @Bean CacheManager dashboardCacheManager(ObjectMapper mapper) {
    var nativeCache=Caffeine.newBuilder().maximumWeight(16L*1024*1024)
        .recordStats()
        .weigher((Object key,Object value)->{
          // A conservative accounting budget, not a measurement of retained JVM heap.
          try{return (int)Math.min(Integer.MAX_VALUE,512L+key.toString().length()*2L+mapper.writeValueAsBytes(value).length*3L);}
          catch(Exception error){return 16*1024*1024;}
        }).build();
    var manager=new SimpleCacheManager();
    manager.setCaches(List.of(new CaffeineCache("dashboardReads",nativeCache)));
    return manager;
  }
}
