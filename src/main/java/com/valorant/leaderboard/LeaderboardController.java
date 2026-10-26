package com.valorant.leaderboard;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.web.csrf.CsrfToken;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {
  private final GlobalLeaderboardService service;private final TrackerProfiles profiles;private final CountryModelEvidence countryModel;private final LeaderboardConfidence confidence;private final HistoricalActArchiveService archive;private final HistoricalTrendsQueryService historicalTrends;
  public LeaderboardController(GlobalLeaderboardService service,TrackerProfiles profiles,CountryModelEvidence countryModel,LeaderboardConfidence confidence,HistoricalActArchiveService archive,HistoricalTrendsQueryService historicalTrends){this.service=service;this.profiles=profiles;this.countryModel=countryModel;this.confidence=confidence;this.archive=archive;this.historicalTrends=historicalTrends;}
  private String selectedAct(String act){if(act==null||act.isBlank()||!act.matches("[a-fA-F0-9-]{36}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown act");if(!archive.isKnown(act))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Act is not in the leaderboard archive catalogue");return act;}
  @GetMapping("/acts") public List<Map<String,Object>> acts(){return archive.acts();}
  /** Catalog intentionally isn't cached: a newly completed historical act should appear immediately. */
  @GetMapping("/trends/catalog") public List<Map<String,Object>> historicalTrendCatalog(){return historicalTrends.catalog();}
  @org.springframework.cache.annotation.Cacheable(cacheNames="dashboardReads",keyGenerator="snapshotKeyGenerator",sync=true)
  @GetMapping("/player-journey") public Map<String,Object> playerJourney(@RequestParam String player,@RequestParam(defaultValue="all")String region){
    if(player.isBlank()||player.length()>300)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Player is required");
    return historicalTrends.playerJourney(player,region(region));
  }
  /** Read-only source tier coverage so unavailable Immortal subdivisions are named explicitly. */
  @org.springframework.cache.annotation.Cacheable(cacheNames="dashboardReads",key="'historical-tier-capabilities:' + @historicalTrendsQueryService.catalog().size()",sync=true)
  @GetMapping("/trends/capabilities") public Map<String,Object> historicalTrendCapabilities(){return historicalTrends.capabilities();}
  @org.springframework.cache.annotation.Cacheable(cacheNames="dashboardReads",keyGenerator="snapshotKeyGenerator",sync=true)
  @GetMapping("/trends/series") public Map<String,Object> historicalTrendSeries(@RequestParam String fromAct,@RequestParam String toAct,@RequestParam(defaultValue="all")String region,@RequestParam(defaultValue="all")String country,@RequestParam(defaultValue="all")String cohort){
    try{return historicalTrends.series(fromAct,toAct,region(region),country(country),cohort);}
    catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());}
  }
  @org.springframework.cache.annotation.Cacheable(cacheNames="dashboardReads",keyGenerator="snapshotKeyGenerator",sync=true)
  @GetMapping("/trends/players") public Map<String,Object> historicalTrendPlayers(@RequestParam String act,@RequestParam(defaultValue="all")String region,@RequestParam(defaultValue="all")String country,@RequestParam(defaultValue="all")String cohort,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="10")int size){
    if(page<1||page>100000||size<1||size>50)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid player pagination");
    try{return historicalTrends.players(selectedAct(act),region(region),country(country),cohort,page,size);}
    catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());}
  }
  @org.springframework.cache.annotation.Cacheable(cacheNames="dashboardReads",keyGenerator="snapshotKeyGenerator",sync=true)
  @org.springframework.transaction.annotation.Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  @GetMapping("/country-page") public Map<String,Object> countryPage(@RequestParam String region,@RequestParam String country,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="")String search,@RequestParam(required=false)String act){
    if(region.equals("all")||country.equals("all")||page<1||page>100000||search.length()>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Country page scope required");
    region(region);country(country);
    if(act!=null&&!archive.isCurrent(selectedAct(act)))return archive.countryPage(act,region,country,page,search);
    var coverage=confidence.confidence(region,country,act);
    var result=new LinkedHashMap<String,Object>();result.put("snapshotId",coverage.get("snapshot_id"));result.put("countries",service.comparison(region,"all"));result.put("players",service.players(region,country,search,page,10));result.put("confidence",coverage);return result;
  }
  @GetMapping("/country-model") public Map<String,Object> countryModel(@RequestParam(required=false)String act){if(act!=null&&!archive.isCurrent(selectedAct(act)))return Map.of("archiveOnly",true,"snapshotId",act,"policyVersion",0,"counts",List.of());return countryModel.get();}
  private String region(String value){if(!value.equals("all")&&!GlobalLeaderboardService.REGIONS.containsKey(value))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown region");return value;}
  private String country(String value){if(!value.equals("all")&&!value.equals("unknown")&&!value.matches("[a-z]{2}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown country filter");return value;}
  @GetMapping("/status") public Map<String,Object> status(@RequestParam(defaultValue="false")boolean compact,@RequestParam(required=false)String act){if(act!=null&&!archive.isCurrent(selectedAct(act)))return archive.status(act);return service.status(compact);}
  @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token){return Map.of("header",token.getHeaderName(),"token",token.getToken());}
  @org.springframework.cache.annotation.Cacheable(cacheNames="dashboardReads",keyGenerator="snapshotKeyGenerator",sync=true)
  @GetMapping("/countries") public List<Map<String,Object>> countries(@RequestParam(defaultValue="all")String region,@RequestParam(defaultValue="all")String country,@RequestParam(required=false)String act){region(region);country(country);if(act!=null&&!archive.isCurrent(selectedAct(act)))return archive.countries(act,region,country);return service.comparison(region,country);}
  @org.springframework.cache.annotation.Cacheable(cacheNames="dashboardReads",keyGenerator="snapshotKeyGenerator",sync=true)
  @GetMapping("/regions") public List<Map<String,Object>> regions(@RequestParam(defaultValue="all")String country,@RequestParam(required=false)String act){country(country);if(act!=null&&!archive.isCurrent(selectedAct(act)))return archive.regions(act,country);return service.regions(country);}
  @org.springframework.cache.annotation.Cacheable(cacheNames="dashboardReads",keyGenerator="snapshotKeyGenerator",sync=true)
  @GetMapping("/insights") public Map<String,Object> insights(@RequestParam(defaultValue="all")String region,@RequestParam(defaultValue="all")String country,@RequestParam(required=false)String act){region(region);country(country);if(act!=null&&!archive.isCurrent(selectedAct(act)))return archive.unavailable(act,"Act movement insights");return service.insights(region,country);}
  @org.springframework.cache.annotation.Cacheable(cacheNames="dashboardReads",keyGenerator="snapshotKeyGenerator",sync=true)
  @GetMapping("/trends") public Map<String,Object> trends(@RequestParam(defaultValue="all")String region,@RequestParam(defaultValue="all")String country,@RequestParam(defaultValue="60")int minutes,@RequestParam(defaultValue="overall")String direction,@RequestParam(required=false)String act){
    if(!Set.of(15,30,60,360,720,1440).contains(minutes)||!Set.of("overall","increment","decrement").contains(direction))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid trend window/direction");
    if(act!=null&&!archive.isCurrent(selectedAct(act)))return Map.of("archiveOnly",true,"points",List.of(),"regionPoints",List.of(),"movers",List.of(),"snapshotCount",0,"message","Historical acts contain one archived leaderboard snapshot, not a time series.");
    return service.trends(region(region),country(country),minutes,direction);
  }
  @org.springframework.cache.annotation.Cacheable(cacheNames="dashboardReads",keyGenerator="snapshotKeyGenerator",sync=true)
  @GetMapping("/players") public Map<String,Object> players(@RequestParam(defaultValue="all")String region,@RequestParam(defaultValue="all")String country,@RequestParam(defaultValue="")String search,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="100")int size,@RequestParam(required=false)String act){
    if(page<1||page>100000||size<1||size>250||search.length()>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid pagination/search");
    if(act!=null&&!archive.isCurrent(selectedAct(act)))return archive.players(act,region(region),country(country),search,page,size);
    return service.players(region(region),country(country),search,page,size);
  }
  @org.springframework.cache.annotation.Cacheable(cacheNames="dashboardReads",keyGenerator="snapshotKeyGenerator",sync=true)
  @GetMapping("/history") public List<Map<String,Object>> history(@RequestParam String player,@RequestParam String region,@RequestParam(required=false)String act){
    boolean archived=act!=null&&!archive.isCurrent(selectedAct(act));
    if(player.isBlank()||player.length()>300||(region.equals("all")&&!archived))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Player and individual region required");
    if(archived)return archive.history(act,player,region(region));
    return service.history(player,region(region));
  }
  @GetMapping("/profile") public Map<String,Object> profile(@RequestParam String player){
    if(player.isBlank()||player.length()>300||!player.contains("#"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Valid Riot ID required");
    return profiles.get(player);
  }
  @org.springframework.cache.annotation.Cacheable(cacheNames="dashboardReads",keyGenerator="snapshotKeyGenerator",sync=true)
  @GetMapping("/player-summary") public Map<String,Object> playerSummary(@RequestParam String player,@RequestParam String region,@RequestParam(required=false)String act){
    boolean archived=act!=null&&!archive.isCurrent(selectedAct(act));
    if(player.isBlank()||player.length()>300||(region.equals("all")&&!archived))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Player and individual region required");
    if(archived)return archive.playerSummary(act,player,region(region));
    return service.playerSummary(player,region(region));
  }
  @org.springframework.cache.annotation.Cacheable(cacheNames="dashboardReads",keyGenerator="snapshotKeyGenerator",sync=true)
  @GetMapping("/country-history") public List<Map<String,Object>> countryHistory(@RequestParam(defaultValue="all")String region,@RequestParam(defaultValue="all")String country,@RequestParam(required=false)String act){if(act!=null&&!archive.isCurrent(selectedAct(act)))return List.of();return service.countryHistory(region(region),country(country));}
  @PostMapping("/collect") public ResponseEntity<Map<String,Object>> collect(@RequestParam(required=false)List<String> country){
    if(country!=null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Collection now captures the whole world; country filters only affect viewing");
    boolean accepted=service.collect();return ResponseEntity.status(accepted?202:409).body(Map.of("accepted",accepted));
  }
}
