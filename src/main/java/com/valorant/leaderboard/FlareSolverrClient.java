package com.valorant.leaderboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/** Local-only transport. Browser cookies stay inside FlareSolverr and are never logged. */
final class FlareSolverrClient implements AutoCloseable {
  private static final int MAX_RESPONSE_BYTES=15_000_000;
  private final ObjectMapper mapper;
  private final HttpClient http;
  private final URI endpoint;
  private final String session="observatory-"+UUID.randomUUID();
  private boolean sessionCreated;

  FlareSolverrClient(ObjectMapper mapper,HttpClient http,String baseUrl){
    this.mapper=mapper;this.http=http;
    URI base=URI.create(baseUrl);
    String host=base.getHost();
    if(!"http".equalsIgnoreCase(base.getScheme())||host==null
        ||!(host.equalsIgnoreCase("localhost")||host.equals("127.0.0.1")||host.equals("[::1]")||host.equals("::1"))
        ||base.getUserInfo()!=null||base.getQuery()!=null||base.getFragment()!=null
        ||!(base.getPath().isEmpty()||base.getPath().equals("/")))
      throw new IllegalArgumentException("FlareSolverr must use a local HTTP endpoint");
    endpoint=base.resolve("/v1");
  }

  synchronized String fetch(String target) throws Exception {
    URI requested=URI.create(target);
    if(!"https".equalsIgnoreCase(requested.getScheme())||!"tracker.gg".equalsIgnoreCase(requested.getHost())
        ||!"/valorant/leaderboards/ranked/all/default".equals(requested.getPath())||requested.getUserInfo()!=null)
      throw new IllegalArgumentException("Invalid leaderboard source URL");
    if(!sessionCreated){
      JsonNode created=post(Map.of("cmd","sessions.create","session",session),Duration.ofSeconds(70));
      if(!"ok".equals(created.path("status").asText())||!session.equals(created.path("session").asText()))
        throw new IllegalStateException("FlareSolverr could not create the local browser session");
      sessionCreated=true;
    }
    JsonNode result=post(Map.of("cmd","request.get","url",target,"session",session,"maxTimeout",60000),Duration.ofSeconds(75));
    if(!"ok".equals(result.path("status").asText()))
      throw new TrackerLeaderboardClient.SourceAccessDenied("FlareSolverr could not obtain the leaderboard page; browser verification or the local service did not complete.",true);
    JsonNode solution=result.path("solution");int status=solution.path("status").asInt(0);
    if(status!=200)throw new TrackerLeaderboardClient.SourceAccessDenied("FlareSolverr received source HTTP "+status+"; collection stopped.",status==403);
    URI returned=URI.create(solution.path("url").asText(""));
    if(!"https".equalsIgnoreCase(returned.getScheme())||!"tracker.gg".equalsIgnoreCase(returned.getHost())
        ||!requested.getPath().equals(returned.getPath())||returned.getUserInfo()!=null)
      throw new IllegalStateException("FlareSolverr returned an unexpected source URL");
    JsonNode html=solution.path("response");
    if(!html.isTextual()||html.asText().length()>MAX_RESPONSE_BYTES)throw new IllegalStateException("Invalid FlareSolverr page response");
    return html.asText();
  }

  private JsonNode post(Map<String,Object> payload,Duration timeout)throws Exception {
    byte[] json=mapper.writeValueAsBytes(payload);
    var request=HttpRequest.newBuilder(endpoint).timeout(timeout).header("Content-Type","application/json")
        .POST(HttpRequest.BodyPublishers.ofByteArray(json)).build();
    var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
    try(var body=response.body()){
      if(response.statusCode()!=200)throw new IllegalStateException("Local FlareSolverr returned HTTP "+response.statusCode());
      byte[] bytes=body.readNBytes(MAX_RESPONSE_BYTES+1);
      if(bytes.length>MAX_RESPONSE_BYTES)throw new IllegalStateException("Local FlareSolverr response exceeded the size limit");
      return mapper.readTree(bytes);
    }
  }

  @Override public synchronized void close(){
    if(!sessionCreated)return;
    try{post(Map.of("cmd","sessions.destroy","session",session),Duration.ofSeconds(10));}
    catch(Exception ignored){/* The local service may already have stopped. */}
    sessionCreated=false;
  }
}
