package com.valorant.leaderboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class TrackerLeaderboardClient {
  private final ObjectMapper mapper;
  private volatile RequestPacer pacer;
  private final java.util.concurrent.atomic.AtomicLong throttles=new java.util.concurrent.atomic.AtomicLong(),retries=new java.util.concurrent.atomic.AtomicLong();
  public Map<String,Object> requestMetrics(){var result=new LinkedHashMap<String,Object>(pacer().metrics());result.put("http429Responses",throttles.get());result.put("retries",retries.get());return result;}
  @org.springframework.beans.factory.annotation.Value("${leaderboard.global.request-delay-ms:500}") private long requestInterval=500;
  @org.springframework.beans.factory.annotation.Value("${leaderboard.tracker.source-transport:direct}") private String sourceTransport="direct";
  @org.springframework.beans.factory.annotation.Value("${leaderboard.tracker.flaresolverr-url:http://127.0.0.1:8191}") private String flareSolverrUrl="http://127.0.0.1:8191";
  private volatile FlareSolverrClient flareSolverr;
  private RequestPacer pacer(){if(pacer==null)synchronized(this){if(pacer==null)pacer=new RequestPacer(requestInterval);}return pacer;}
  public long requestIntervalMillis(){return Math.max(500,requestInterval);}
  private FlareSolverrClient flareSolverr(){if(flareSolverr==null)synchronized(this){if(flareSolverr==null)flareSolverr=new FlareSolverrClient(mapper,http,flareSolverrUrl);}return flareSolverr;}
  @jakarta.annotation.PreDestroy public void closeTransport(){if(flareSolverr!=null)flareSolverr.close();}

  private final HttpClient http;
  @org.springframework.beans.factory.annotation.Autowired
  public TrackerLeaderboardClient(ObjectMapper mapper) { this(mapper,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),null); }
  TrackerLeaderboardClient(ObjectMapper mapper,HttpClient http,RequestPacer pacer) {this.mapper=mapper;this.http=http;this.pacer=pacer;}
  public static final Set<String> REGIONS=Set.of("ap","kr","eu","na","latam","br");
  public record Player(String key, String country, int rank, int rr, String tier, int wins, String region) {
    public Player(String key,String country,int rank,int rr,String tier,int wins) { this(key,country,rank,rr,tier,wins,"ap"); }
  }
  public record Page(List<Player> players, OffsetDateTime updatedAt) {}
  static final class SourceUnavailable extends IllegalStateException { SourceUnavailable(){super("Tracker leaderboard data is unavailable; not an empty page");} }
  public static final class SourceAccessDenied extends IllegalStateException {
    private final boolean browserChallenge;
    public SourceAccessDenied(String message,boolean browserChallenge){super(message);this.browserChallenge=browserChallenge;}
    public boolean browserChallenge(){return browserChallenge;}
  }
  private static String diagnosticResponseText(HttpResponse<byte[]> response){
    try{
      byte[] bytes=response.body();
      if(response.headers().firstValue("Content-Encoding").orElse("").equalsIgnoreCase("gzip"))
        try(var stream=new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(bytes))){bytes=stream.readNBytes(8192);}
      return new String(bytes,0,Math.min(bytes.length,8192),java.nio.charset.StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
    }catch(Exception ignored){return "";}
  }

  @org.springframework.beans.factory.annotation.Value("${LEADERBOARD_PROFILE_COOKIE:}") private String profileCookie="";
  public JsonNode fetchProfile(String player,String act)throws Exception {
    if(player.isBlank()||player.length()>300||!player.contains("#")||!act.matches("[a-fA-F0-9-]{36}"))throw new IllegalArgumentException("Invalid profile request");
    String encoded=java.net.URLEncoder.encode(player,java.nio.charset.StandardCharsets.UTF_8).replace("+","%20");
    String page="https://tracker.gg/valorant/profile/riot/"+encoded+"/overview?platform=pc&playlist=competitive&season="+act;
    var request=HttpRequest.newBuilder(URI.create("https://api.tracker.gg/api/v2/valorant/standard/profile/riot/"+encoded+"?"))
      .timeout(Duration.ofSeconds(25)).header("accept","application/json").header("accept-language","en-US,en;q=0.7")
      .header("origin","https://tracker.gg").header("referer",page).header("Accept-Encoding","gzip")
      .header("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36")
      .header("sec-ch-ua","\"Brave\";v=\"155\", \"Chromium\";v=\"155\", \"Not(A:Brand\";v=\"24\"")
      .header("sec-ch-ua-mobile","?0").header("sec-ch-ua-platform","\"Windows\"")
      .header("sec-fetch-dest","empty").header("sec-fetch-mode","cors").header("sec-fetch-site","same-site");
    if(profileCookie!=null&&!profileCookie.isBlank())request.header("Cookie",profileCookie.trim());
    pacer().acquireLive();var response=http.send(request.GET().build(),HttpResponse.BodyHandlers.ofByteArray());
    if(response.statusCode()==429||response.statusCode()>=500){if(response.statusCode()==429)throttles.incrementAndGet();pacer().defer(Math.max(5000,RequestPacer.retryAfterMillis(response.headers().firstValue("Retry-After").orElse(""),java.time.Instant.now())));}
    if(response.statusCode()!=200)throw new IllegalStateException("Profile HTTP "+response.statusCode());
    byte[] bytes=response.body();if(response.headers().firstValue("Content-Encoding").orElse("").equalsIgnoreCase("gzip"))try(var stream=new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(bytes))){bytes=stream.readAllBytes();}
    if(bytes.length>5_000_000)throw new IllegalStateException("Profile payload too large");return mapper.readTree(bytes);
  }

  public Page fetch(String act, String country, int page) throws Exception {
    return fetchSource(act,"ap",country,page);
  }
  public Page fetchRegion(String act,String region,int page) throws Exception { return fetchSource(act,region,null,page,false); }
  public Page fetchHistoricalRegion(String act,String region,int page) throws Exception { return fetchSource(act,region,null,page,true); }
  private Page fetchSource(String act,String region,String country,int page) throws Exception {
    return fetchSource(act,region,country,page,false);
  }
  private Page fetchSource(String act,String region,String country,int page,boolean background) throws Exception {
    if ((!REGIONS.contains(region)&&!"global".equals(region)) || !act.matches("[a-fA-F0-9-]{36}") || page<1
        || (country!=null&&!country.matches("[a-z]{2}"))) throw new IllegalArgumentException("Invalid source request");
    String url = "https://tracker.gg/valorant/leaderboards/ranked/all/default?platform=pc&region="+region+"&act="
        + act + (country==null?"":"&country="+country) + "&page=" + page;
    if(!"direct".equalsIgnoreCase(sourceTransport)&&!"flaresolverr".equalsIgnoreCase(sourceTransport))
      throw new IllegalStateException("Unknown leaderboard source transport");
    final int maxAttempts=5;
    for(int attempt=0;attempt<maxAttempts;attempt++){
    // Unavailable SSR data is not an empty page. Retry with bounded backoff and a fresh URL.
    String requestUrl=attempt==0?url:url+"&_refresh="+System.currentTimeMillis();
    if("flaresolverr".equalsIgnoreCase(sourceTransport)){
      var solver=flareSolverr();String html;
      synchronized(solver){if(background)pacer().acquireBackground();else pacer().acquireLive();html=solver.fetch(requestUrl);}
      try{return parseSource(html,act,region,country,page);}
      catch(SourceUnavailable error){
        if(attempt==maxAttempts-1)throw error;
        retries.incrementAndGet();Thread.sleep(new long[]{5000,15000,30000,60000}[attempt]);continue;
      }
    }
    var request = HttpRequest.newBuilder(URI.create(requestUrl)).timeout(Duration.ofSeconds(30))
        .header("Accept-Encoding", "gzip")
        .header("Upgrade-Insecure-Requests", "1")
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36")
        .header("sec-ch-ua", "\"Brave\";v=\"155\", \"Chromium\";v=\"155\", \"Not(A:Brand\";v=\"24\"")
        .header("sec-ch-ua-mobile", "?0").header("sec-ch-ua-platform", "\"Windows\"").GET().build();
    if(background)pacer().acquireBackground();else pacer().acquireLive();
    HttpResponse<byte[]> response;
    try{response=http.send(request,HttpResponse.BodyHandlers.ofByteArray());}
    catch(java.io.IOException error){if(attempt==maxAttempts-1)throw error;retries.incrementAndGet();pacer().defer(2000L<<attempt);continue;}
    if(response.statusCode()==403){
      String body=diagnosticResponseText(response);
      String server=response.headers().firstValue("Server").orElse("");
      String type=response.headers().firstValue("Content-Type").orElse("");
      String requestId=response.headers().firstValue("CF-Ray").orElse("");
      boolean browserChallenge=server.equalsIgnoreCase("cloudflare")
          &&(body.contains("just a moment")||body.contains("enable javascript and cookies"));
      String reason=browserChallenge?"Tracker returned HTTP 403 with a Cloudflare browser verification challenge"
          :"Tracker returned HTTP 403 and denied the direct collection request";
      throw new SourceAccessDenied(reason+" (content-type="+type+(requestId.isBlank()?"":", request-id="+requestId)+").",browserChallenge);
    }
    if(response.statusCode()==429||response.statusCode()>=500){
      if(response.statusCode()==429)throttles.incrementAndGet();
      long wait=Math.max(5000L<<attempt,RequestPacer.retryAfterMillis(response.headers().firstValue("Retry-After").orElse(""),java.time.Instant.now()));
      pacer().defer(wait);
      if(attempt==maxAttempts-1)throw new IllegalStateException("Tracker HTTP "+response.statusCode()+" after bounded retries");
      retries.incrementAndGet();continue;
    }
    if (response.statusCode() != 200) throw new IllegalStateException("Tracker HTTP " + response.statusCode());
    byte[] bytes=response.body();String encoding=response.headers().firstValue("Content-Encoding").orElse("");
    if(encoding.equalsIgnoreCase("gzip")){try(var stream=new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(bytes))){bytes=stream.readAllBytes();}}
    else if(!encoding.isBlank()&&!encoding.equalsIgnoreCase("identity"))throw new IllegalStateException("Unsupported source content encoding");
    try{return parseSource(new String(bytes,java.nio.charset.StandardCharsets.UTF_8),act,region,country,page);}
    catch(SourceUnavailable error){
      if(attempt==maxAttempts-1){org.slf4j.LoggerFactory.getLogger(TrackerLeaderboardClient.class).warn("Tracker data unavailable: source={} page={} attempts={} (not cached, not pagination end)",region,page,maxAttempts);throw error;}
      long wait=new long[]{5000,15000,30000,60000}[attempt];
      org.slf4j.LoggerFactory.getLogger(TrackerLeaderboardClient.class).warn("Tracker data unavailable: source={} page={} attempt={}/{}; fresh retry after {} ms",region,page,attempt+1,maxAttempts,wait);
      retries.incrementAndGet();Thread.sleep(wait);
    }
    }
    throw new SourceUnavailable();
  }

  public Page parse(String html, String act, String country, int page) throws Exception {
    return parseSource(html,act,"ap",country,page);
  }
  public Page parseSource(String html,String act,String region,String country,int page) throws Exception {
    var marker = java.util.regex.Pattern.compile("window\\.__INITIAL_STATE__\\s*=\\s*").matcher(html);
    if (!marker.find()) throw new IllegalStateException("Missing initial state: challenge or changed HTML");
    // Jackson reads the first JSON value without executing the page's JavaScript.
    JsonNode state;
    try (var parser = mapper.getFactory().createParser(html.substring(marker.end()))) {
      state = mapper.readTree(parser);
    }
    JsonNode boards = state.path("stats").path("standardLeaderboards");
    if (boards.isMissingNode() || boards.isNull() || (boards.isArray() && boards.isEmpty())) throw new SourceUnavailable();
    if (!boards.isArray()) throw new IllegalStateException("Invalid leaderboard payload");
    JsonNode board=null;OffsetDateTime newest=null;boolean matched=false;
    for(JsonNode candidate:boards){
      String candidateKey=candidate.path("_key").asText();int left=candidateKey.indexOf('{'),right=candidateKey.lastIndexOf('}');
      if(left<0||right<left)continue;
      JsonNode f=mapper.readTree(candidateKey.substring(left,right+1));
      if(!act.equals(f.path("act").asText())||!region.equals(f.path("region").asText())||!"pc".equals(f.path("platform").asText())
          ||!(country==null?f.path("country").asText("").isEmpty():country.equals(f.path("country").asText()))
          ||!candidateKey.substring(right+1).equals("|"+((page-1)*100)+"|100"))continue;
      matched=true;
      if(!candidate.path("items").isArray())continue;
      OffsetDateTime updated=OffsetDateTime.parse(candidate.path("lastUpdated").asText());
      if(newest!=null&&updated.isEqual(newest)&&!candidate.path("items").equals(board.path("items")))throw new IllegalStateException("Conflicting leaderboard payloads at identical source time");
      if(newest==null||updated.isAfter(newest)){board=candidate;newest=updated;}
    }
    if(board==null){if(matched)throw new SourceUnavailable();throw new IllegalStateException("Response filter/offset mismatch");}
    String key = board.path("_key").asText();
    int start = key.indexOf('{'), end = key.lastIndexOf('}');
    if (start < 0 || end < start) throw new IllegalStateException("Missing filter metadata");
    JsonNode filters = mapper.readTree(key.substring(start, end + 1));
    if (!act.equals(filters.path("act").asText()) || !(country==null?filters.path("country").asText("").isEmpty():country.equals(filters.path("country").asText()))
        || !region.equals(filters.path("region").asText()) || !"pc".equals(filters.path("platform").asText())
        || !key.substring(end + 1).equals("|" + ((page - 1) * 100) + "|100"))
      throw new IllegalStateException("Response filter/offset mismatch");
    JsonNode items = board.path("items");
    if(!items.isArray() && board.path("status").asInt()==404)throw new SourceUnavailable();
    if (!items.isArray() || items.size() > 100) throw new IllegalStateException("Invalid page size");
    List<Player> players = new ArrayList<>();
    for (JsonNode item : items) {
      JsonNode owner = item.path("owner");
      String id = owner.path("metadata").path("platformUserIdentifier").asText("");
      if(id.isBlank())id=item.path("id").asText("");
      String code = owner.path("metadata").path("countryCode").asText("").toLowerCase(Locale.ROOT);
      if (code.isBlank()) code=null;
      String playerRegion=owner.path("metadata").path("platformUserRegion").asText(country==null?"":"ap");
      if (id.isBlank() || (country!=null&&!country.equalsIgnoreCase(code)) || (code!=null&&!code.matches("[a-z]{2}"))
          || !REGIONS.contains(playerRegion) || (!"global".equals(region)&&!region.equals(playerRegion))
          || !item.path("rank").isIntegralNumber() || !item.path("value").isIntegralNumber())
        throw new IllegalStateException("Invalid player identity, country or numeric fields");
      String tier = null; Integer wins = null;
      for (JsonNode stat : owner.path("stats")) {
        if ("Tier".equals(stat.path("metadata").path("key").asText())) tier = stat.path("value").asText();
        if ("Wins".equals(stat.path("metadata").path("key").asText()) && stat.path("value").isIntegralNumber()) wins = stat.path("value").intValue();
      }
      if (tier == null || tier.isBlank() || wins == null || wins < 0 || item.get("rank").intValue() < 1 || item.get("value").intValue() < 0)
        throw new IllegalStateException("Invalid player stats");
      players.add(new Player(id, code, item.get("rank").intValue(), item.get("value").intValue(), tier, wins,playerRegion));
    }
    return new Page(List.copyOf(players), OffsetDateTime.parse(board.path("lastUpdated").asText()));
  }
}
