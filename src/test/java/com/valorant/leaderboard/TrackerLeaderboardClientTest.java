package com.valorant.leaderboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrackerLeaderboardClientTest {
  private final TrackerLeaderboardClient client=new TrackerLeaderboardClient(new ObjectMapper());
  private static final String ACT="d816f426-48ea-f052-117f-9697a155b319";
  private String html(String country,int offset,String items) throws Exception {
    String key="valorant|"+new ObjectMapper().writeValueAsString(java.util.Map.of("act",ACT,"country",country,"region","ap","platform","pc"))+"|"+offset+"|100";
    return "<script>window.__INITIAL_STATE__ = {\"stats\":{\"standardLeaderboards\":[{\"_key\":"+new ObjectMapper().writeValueAsString(key)+",\"lastUpdated\":\"2026-10-20T12:00:00Z\",\"items\":["+items+"]}]}};</script>";
  }
  private String player() { return """
      {"rank":81,"value":537,"owner":{"metadata":{"platformUserIdentifier":"a#b","countryCode":"IN"},"stats":[{"metadata":{"key":"Tier"},"value":"Radiant"},{"metadata":{"key":"Wins"},"value":35}]}}
      """; }
  @Test void readsEmbeddedJsonWithTrailingScript() throws Exception {
    var result=client.parse(html("in",0,player()),ACT,"in",1);
    assertEquals(537,result.players().getFirst().rr());assertEquals("a#b",result.players().getFirst().key());assertEquals(35,result.players().getFirst().wins());
  }
  @Test void acceptsVerifiedEmptyPage() throws Exception { assertTrue(client.parse(html("in",100,""),ACT,"in",2).players().isEmpty()); }
  @Test void rejectsChallenge() { assertThrows(IllegalStateException.class,()->client.parse("<title>Just a moment</title>",ACT,"in",1)); }
  @Test void rejectsWrongCountryAndOffset() throws Exception {
    assertThrows(IllegalStateException.class,()->client.parse(html("jp",0,""),ACT,"in",1));
    assertThrows(IllegalStateException.class,()->client.parse(html("in",0,""),ACT,"in",2));
  }
  @Test void preservesRawDuplicateRowsForSafePaginationAndRejectsInvalidStats() throws Exception {
    assertEquals(2,client.parse(html("in",0,player()+","+player()),ACT,"in",1).players().size());
    assertThrows(IllegalStateException.class,()->client.parse(html("in",0,player().replace("537","null")),ACT,"in",1));
  }
  @Test
  void readsGlobalRegionAndNullableCountryAndRejectsMissingRegion()throws Exception {
    String raw=html("in",0,player());
    var marker=java.util.regex.Pattern.compile("window\\.__INITIAL_STATE__\\s*=\\s*").matcher(raw);assertTrue(marker.find());
    var mapper=new ObjectMapper();com.fasterxml.jackson.databind.node.ObjectNode state;
    try(var parser=mapper.getFactory().createParser(raw.substring(marker.end()))){state=mapper.readTree(parser);}
    var board=(com.fasterxml.jackson.databind.node.ObjectNode)state.path("stats").path("standardLeaderboards").get(0);
    board.put("_key","valorant|"+mapper.writeValueAsString(java.util.Map.of("act",ACT,"region","global","platform","pc"))+"|0|100");
    var meta=(com.fasterxml.jackson.databind.node.ObjectNode)board.path("items").get(0).path("owner").path("metadata");meta.putNull("countryCode");meta.put("platformUserRegion","eu");
    var result=client.parseSource("window.__INITIAL_STATE__ = "+state+";",ACT,"global",null,1);
    assertNull(result.players().getFirst().country());assertEquals("eu",result.players().getFirst().region());
    meta.putNull("platformUserIdentifier");((com.fasterxml.jackson.databind.node.ObjectNode)board.path("items").get(0)).put("id","public-id#tag");
    assertEquals("public-id#tag",client.parseSource("window.__INITIAL_STATE__ = "+state+";",ACT,"global",null,1).players().getFirst().key());
    meta.remove("platformUserRegion");assertThrows(IllegalStateException.class,()->client.parseSource("window.__INITIAL_STATE__ = "+state+";",ACT,"global",null,1));
  }
  @Test void unavailableDataIsNotTreatedAsAnEmptyTerminalPage()throws Exception {
    var mapper=new ObjectMapper();String key="valorant|"+mapper.writeValueAsString(java.util.Map.of("act",ACT,"region","global","platform","pc"))+"|0|100";
    String raw="window.__INITIAL_STATE__ = {\"stats\":{\"standardLeaderboards\":[{\"status\":404,\"_key\":"+mapper.writeValueAsString(key)+"}]}};";
    assertThrows(TrackerLeaderboardClient.SourceUnavailable.class,()->client.parseSource(raw,ACT,"global",null,1));
  }
  @Test void acceptsDuplicatedValidBoardAlongsideCachedErrorButRejectsConflictingSameTime()throws Exception {
    String raw=html("in",0,player());var mapper=new ObjectMapper();
    int start=raw.indexOf("{\"stats\"");var state=mapper.readTree(raw.substring(start,raw.indexOf(";</script>")));
    var boards=(com.fasterxml.jackson.databind.node.ArrayNode)state.path("stats").path("standardLeaderboards");
    var valid=boards.get(0).deepCopy();var error=valid.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)error).remove("items");((com.fasterxml.jackson.databind.node.ObjectNode)error).put("status",404);
    boards.removeAll();boards.add(error);boards.add(valid);boards.add(valid.deepCopy());
    assertEquals(1,client.parse("window.__INITIAL_STATE__ = "+state+";",ACT,"in",1).players().size());
    ((com.fasterxml.jackson.databind.node.ObjectNode)boards.get(2).path("items").get(0)).put("value",538);
    assertThrows(IllegalStateException.class,()->client.parse("window.__INITIAL_STATE__ = "+state+";",ACT,"in",1));
  }
  @Test void retriesMissingBoardButDoesNotTreatItAsAnEmptyPage()throws Exception {
    assertThrows(TrackerLeaderboardClient.SourceUnavailable.class,()->client.parseSource("window.__INITIAL_STATE__ = {\"stats\":{\"standardLeaderboards\":[]}};",ACT,"global",null,1));
  }
  @SuppressWarnings("unchecked")
  private java.net.http.HttpResponse<byte[]> response(int code,String body,String retry){
    var response=(java.net.http.HttpResponse<byte[]>)org.mockito.Mockito.mock(java.net.http.HttpResponse.class);
    org.mockito.Mockito.when(response.statusCode()).thenReturn(code);
    org.mockito.Mockito.when(response.body()).thenReturn(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    org.mockito.Mockito.when(response.headers()).thenReturn(java.net.http.HttpHeaders.of(retry==null?java.util.Map.of():java.util.Map.of("Retry-After",java.util.List.of(retry)),(a,b)->true));
    return response;
  }
  @SuppressWarnings("unchecked")
  private java.net.http.HttpResponse<java.io.InputStream> solverResponse(int code,String body){
    var response=(java.net.http.HttpResponse<java.io.InputStream>)org.mockito.Mockito.mock(java.net.http.HttpResponse.class);
    byte[] bytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    org.mockito.Mockito.when(response.statusCode()).thenReturn(code);
    org.mockito.Mockito.when(response.body()).thenAnswer(ignored->new java.io.ByteArrayInputStream(bytes));
    return response;
  }
  @Test void honorsSharedRetryAfterAndStopsImmediatelyOn403()throws Exception {
    var http=org.mockito.Mockito.mock(java.net.http.HttpClient.class);var clock=new java.util.concurrent.atomic.AtomicLong(1000);
    var gated=new TrackerLeaderboardClient(new ObjectMapper(),http,new RequestPacer(500,clock::get,clock::addAndGet));
    var limited=response(429,"","60");var success=response(200,html("in",0,player()),null);
    org.mockito.Mockito.when(http.send(org.mockito.ArgumentMatchers.any(java.net.http.HttpRequest.class),org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<byte[]>>any())).thenReturn(limited,success);
    assertEquals(1,gated.fetch(ACT,"in",1).players().size());assertEquals(61000,clock.get());
    org.mockito.Mockito.reset(http);
    var blocked=response(403,"",null);
    org.mockito.Mockito.when(http.send(org.mockito.ArgumentMatchers.any(java.net.http.HttpRequest.class),org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<byte[]>>any())).thenReturn(blocked);
    assertThrows(IllegalStateException.class,()->gated.fetch(ACT,"in",1));
    org.mockito.Mockito.verify(http,org.mockito.Mockito.times(1)).send(org.mockito.ArgumentMatchers.any(java.net.http.HttpRequest.class),org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<byte[]>>any());
  }
  @Test void identifiesCloudflareJavascriptChallengeWithoutRetainingItsHtml()throws Exception {
    var http=org.mockito.Mockito.mock(java.net.http.HttpClient.class);
    @SuppressWarnings("unchecked") var blocked=(java.net.http.HttpResponse<byte[]>)org.mockito.Mockito.mock(java.net.http.HttpResponse.class);
    var compressed=new java.io.ByteArrayOutputStream();try(var gzip=new java.util.zip.GZIPOutputStream(compressed)){gzip.write("<title>Just a moment...</title>Enable JavaScript and cookies".getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    org.mockito.Mockito.when(blocked.statusCode()).thenReturn(403);
    org.mockito.Mockito.when(blocked.body()).thenReturn(compressed.toByteArray());
    org.mockito.Mockito.when(blocked.headers()).thenReturn(java.net.http.HttpHeaders.of(java.util.Map.of("Server",java.util.List.of("cloudflare"),"Content-Type",java.util.List.of("text/html; charset=UTF-8"),"Content-Encoding",java.util.List.of("gzip"),"CF-Ray",java.util.List.of("test-ray-id")),(a,b)->true));
    org.mockito.Mockito.when(http.send(org.mockito.ArgumentMatchers.any(java.net.http.HttpRequest.class),org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<byte[]>>any())).thenReturn(blocked);
    var gated=new TrackerLeaderboardClient(new ObjectMapper(),http,new RequestPacer(500,()->1000L,ignored->{}));
    var error=assertThrows(TrackerLeaderboardClient.SourceAccessDenied.class,()->gated.fetchRegion(ACT,"global",1));
    assertTrue(error.browserChallenge());assertTrue(error.getMessage().contains("Cloudflare browser verification challenge"));assertTrue(error.getMessage().contains("test-ray-id"));
    assertFalse(error.getMessage().contains("Enable JavaScript"));
    org.mockito.Mockito.verify(http,org.mockito.Mockito.times(1)).send(org.mockito.ArgumentMatchers.any(java.net.http.HttpRequest.class),org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<byte[]>>any());
  }
  @Test void flareSolverrTransportUsesLocalSessionAndValidatesTheReturnedPage()throws Exception {
    var http=org.mockito.Mockito.mock(java.net.http.HttpClient.class);var mapper=new ObjectMapper();
    var solver=new FlareSolverrClient(mapper,http,"http://127.0.0.1:8191");
    String session=(String)org.springframework.test.util.ReflectionTestUtils.getField(solver,"session");
    String target="https://tracker.gg/valorant/leaderboards/ranked/all/default?platform=pc&region=ap&act="+ACT+"&country=in&page=1";
    String created=mapper.writeValueAsString(java.util.Map.of("status","ok","session",session));
    String solved=mapper.writeValueAsString(java.util.Map.of("status","ok","solution",java.util.Map.of("status",200,"url",target,"response",html("in",0,player()),"cookies",java.util.List.of(java.util.Map.of("value","private-cookie")))));
    var createdResponse=solverResponse(200,created);var solvedResponse=solverResponse(200,solved);var destroyedResponse=solverResponse(200,"{\"status\":\"ok\"}");
    org.mockito.Mockito.when(http.send(org.mockito.ArgumentMatchers.any(java.net.http.HttpRequest.class),org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<java.io.InputStream>>any())).thenReturn(createdResponse,solvedResponse,solvedResponse,destroyedResponse);
    var clock=new java.util.concurrent.atomic.AtomicLong(1000);
    var client=new TrackerLeaderboardClient(mapper,http,new RequestPacer(500,clock::get,clock::addAndGet));
    org.springframework.test.util.ReflectionTestUtils.setField(client,"sourceTransport","flaresolverr");
    org.springframework.test.util.ReflectionTestUtils.setField(client,"flareSolverr",solver);
    assertEquals(537,client.fetch(ACT,"in",1).players().getFirst().rr());
    assertEquals(537,client.fetch(ACT,"in",1).players().getFirst().rr());
    client.closeTransport();
    var requests=org.mockito.ArgumentCaptor.forClass(java.net.http.HttpRequest.class);
    org.mockito.Mockito.verify(http,org.mockito.Mockito.times(4)).send(requests.capture(),org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<java.io.InputStream>>any());
    assertTrue(requests.getAllValues().stream().allMatch(request->request.uri().toString().equals("http://127.0.0.1:8191/v1")));
  }
  @Test void flareSolverrRejectsNonLocalServiceAndStopsOnSourceDenial()throws Exception {
    var http=org.mockito.Mockito.mock(java.net.http.HttpClient.class);var mapper=new ObjectMapper();
    assertThrows(IllegalArgumentException.class,()->new FlareSolverrClient(mapper,http,"https://example.com"));
    var solver=new FlareSolverrClient(mapper,http,"http://localhost:8191");
    String session=(String)org.springframework.test.util.ReflectionTestUtils.getField(solver,"session");
    String created=mapper.writeValueAsString(java.util.Map.of("status","ok","session",session));
    String denied=mapper.writeValueAsString(java.util.Map.of("status","ok","solution",java.util.Map.of("status",403)));
    var createdResponse=solverResponse(200,created);var deniedResponse=solverResponse(200,denied);
    org.mockito.Mockito.when(http.send(org.mockito.ArgumentMatchers.any(java.net.http.HttpRequest.class),org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<java.io.InputStream>>any())).thenReturn(createdResponse,deniedResponse);
    var error=assertThrows(TrackerLeaderboardClient.SourceAccessDenied.class,()->solver.fetch("https://tracker.gg/valorant/leaderboards/ranked/all/default?platform=pc&region=ap&act="+ACT+"&page=1"));
    assertTrue(error.getMessage().contains("403"));
    org.mockito.Mockito.verify(http,org.mockito.Mockito.times(2)).send(org.mockito.ArgumentMatchers.any(java.net.http.HttpRequest.class),org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<java.io.InputStream>>any());
    assertThrows(IllegalArgumentException.class,()->solver.fetch("https://example.com/valorant/leaderboards/ranked/all/default"));
  }
  @Test void flareSolverrRecreatesAnInvalidBrowserSessionOnce()throws Exception {
    var http=org.mockito.Mockito.mock(java.net.http.HttpClient.class);var mapper=new ObjectMapper();
    var solver=new FlareSolverrClient(mapper,http,"http://127.0.0.1:8191");
    String original=(String)org.springframework.test.util.ReflectionTestUtils.getField(solver,"session");
    String target="https://tracker.gg/valorant/leaderboards/ranked/all/default?platform=pc&region=ap&act="+ACT+"&country=in&page=1";
    String expectedHtml=html("in",0,player());var calls=new java.util.concurrent.atomic.AtomicInteger();
    org.mockito.Mockito.when(http.send(org.mockito.ArgumentMatchers.any(java.net.http.HttpRequest.class),org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<java.io.InputStream>>any())).thenAnswer(ignored->{
      int call=calls.incrementAndGet();
      if(call==1||call==4){String current=(String)org.springframework.test.util.ReflectionTestUtils.getField(solver,"session");return solverResponse(200,mapper.writeValueAsString(java.util.Map.of("status","ok","session",current)));}
      if(call==2||call==3)return solverResponse(500,"{\"status\":\"error\",\"message\":\"invalid session id\"}");
      if(call==5)return solverResponse(200,mapper.writeValueAsString(java.util.Map.of("status","ok","solution",java.util.Map.of("status",200,"url",target,"response",expectedHtml))));
      throw new AssertionError("Unexpected local service request");
    });
    assertEquals(expectedHtml,solver.fetch(target));assertEquals(5,calls.get());
    assertNotEquals(original,org.springframework.test.util.ReflectionTestUtils.getField(solver,"session"));
  }
  @Test void flareSolverrDoesNotRetryOtherHttp500Errors()throws Exception {
    var http=org.mockito.Mockito.mock(java.net.http.HttpClient.class);var mapper=new ObjectMapper();
    var solver=new FlareSolverrClient(mapper,http,"http://127.0.0.1:8191");
    String session=(String)org.springframework.test.util.ReflectionTestUtils.getField(solver,"session");
    var created=solverResponse(200,mapper.writeValueAsString(java.util.Map.of("status","ok","session",session)));
    var error=solverResponse(500,"{\"status\":\"error\",\"message\":\"unexpected server failure\"}");
    org.mockito.Mockito.when(http.send(org.mockito.ArgumentMatchers.any(java.net.http.HttpRequest.class),org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<java.io.InputStream>>any())).thenReturn(created,error);
    var failure=assertThrows(IllegalStateException.class,()->solver.fetch("https://tracker.gg/valorant/leaderboards/ranked/all/default?platform=pc&region=ap&act="+ACT+"&page=1"));
    assertEquals("Local FlareSolverr returned HTTP 500",failure.getMessage());
    org.mockito.Mockito.verify(http,org.mockito.Mockito.times(2)).send(org.mockito.ArgumentMatchers.any(java.net.http.HttpRequest.class),org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<java.io.InputStream>>any());
  }
  @Test void flareSolverrStopsAfterOneInvalidSessionRecovery()throws Exception {
    var http=org.mockito.Mockito.mock(java.net.http.HttpClient.class);var mapper=new ObjectMapper();
    var solver=new FlareSolverrClient(mapper,http,"http://127.0.0.1:8191");var calls=new java.util.concurrent.atomic.AtomicInteger();
    org.mockito.Mockito.when(http.send(org.mockito.ArgumentMatchers.any(java.net.http.HttpRequest.class),org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<java.io.InputStream>>any())).thenAnswer(ignored->{
      int call=calls.incrementAndGet();
      if(call==1||call==4){String current=(String)org.springframework.test.util.ReflectionTestUtils.getField(solver,"session");return solverResponse(200,mapper.writeValueAsString(java.util.Map.of("status","ok","session",current)));}
      if(call<=6)return solverResponse(500,"{\"status\":\"error\",\"message\":\"invalid session id\"}");
      throw new AssertionError("Unexpected retry");
    });
    var failure=assertThrows(IllegalStateException.class,()->solver.fetch("https://tracker.gg/valorant/leaderboards/ranked/all/default?platform=pc&region=ap&act="+ACT+"&page=1"));
    assertEquals("FlareSolverr browser session remains invalid after one recovery",failure.getMessage());assertEquals(6,calls.get());
  }
  @Test
  @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="LEADERBOARD_LIVE_TEST", matches="true")
  void fetchesRealTrackerPageUsingJavaHttpClient() throws Exception {
    assertFalse(client.fetch(ACT,"in",1).players().isEmpty());
  }
}
