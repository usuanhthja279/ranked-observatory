package com.valorant.leaderboard;

import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.*;

/** Cache immutable-looking artwork locally; application HTML/JS and APIs keep their freshness rules. */
@Configuration
public class StaticAssetCaching implements WebMvcConfigurer {
  @Override public void addResourceHandlers(ResourceHandlerRegistry registry){
    for(String path:new String[]{"scenery","flags","ranks"})registry.addResourceHandler("/"+path+"/**")
        .addResourceLocations("classpath:/static/"+path+"/")
        .setCacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic());
  }
}
