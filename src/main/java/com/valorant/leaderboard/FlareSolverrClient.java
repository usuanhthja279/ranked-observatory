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
  private String session=newSessionId();
  private boolean sessionCreated;
  private static String newSessionId(){return "observatory-"+UUID.randomUUID();}
  private static final class InvalidBrowserSession extends IllegalStateException {
    InvalidBrowserSession(){super("FlareSolverr browser session is no longer valid");}
  }

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
    JsonNode result=null;
    for(int attempt=0;attempt<2;attempt++){
      ensureSession();
      try{
        result=post(Map.of("cmd","request.get","url",target,"session",session,"maxTimeout",60000),Duration.ofSeconds(75));
        if("error".equals(result.path("status").asText())&&isInvalidSession(result))throw new InvalidBrowserSession();
        break;
      }catch(InvalidBrowserSession error){
        destroySession();
        session=newSessionId();
        if(attempt==1)throw new IllegalStateException("FlareSolverr browser session remains invalid after one recovery",error);
      }
    }
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

  private void ensureSession()throws Exception {
    if(sessionCreated)return;
    JsonNode created=post(Map.of("cmd","sessions.create","session",session),Duration.ofSeconds(70));
    if(!"ok".equals(created.path("status").asText())||!session.equals(created.path("session").asText()))
      throw new IllegalStateException("FlareSolverr could not create the local browser session");
    sessionCreated=true;
  }
  private static boolean isInvalidSession(JsonNode response){
    return response.path("message").asText("").toLowerCase(java.util.Locale.ROOT).contains("invalid session id");
  }

  private JsonNode post(Map<String,Object> payload,Duration timeout)throws Exception {
    byte[] json=mapper.writeValueAsBytes(payload);
    var request=HttpRequest.newBuilder(endpoint).timeout(timeout).header("Content-Type","application/json")
        .POST(HttpRequest.BodyPublishers.ofByteArray(json)).build();
    var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
    try(var body=response.body()){
      byte[] bytes=body.readNBytes(MAX_RESPONSE_BYTES+1);
      if(bytes.length>MAX_RESPONSE_BYTES)throw new IllegalStateException("Local FlareSolverr response exceeded the size limit");
      if(response.statusCode()!=200){
        if(response.statusCode()==500){
          try{JsonNode error=mapper.readTree(bytes);if(error!=null&&"error".equals(error.path("status").asText())&&isInvalidSession(error))throw new InvalidBrowserSession();}
          catch(com.fasterxml.jackson.core.JsonProcessingException ignored){/* Do not expose the response body. */}
        }
        throw new IllegalStateException("Local FlareSolverr returned HTTP "+response.statusCode());
      }
      return mapper.readTree(bytes);
    }
  }

  private void destroySession(){
    if(!sessionCreated)return;
    try{post(Map.of("cmd","sessions.destroy","session",session),Duration.ofSeconds(10));}
    catch(Exception ignored){/* The local service may already have stopped. */}
    sessionCreated=false;
  }
  @Override public synchronized void close(){destroySession();}
}
