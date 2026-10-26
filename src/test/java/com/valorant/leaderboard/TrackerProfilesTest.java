package com.valorant.leaderboard;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class TrackerProfilesTest {
 private static final String ACT="d816f426-48ea-f052-117f-9697a155b319";
 private JsonNode fixture()throws Exception{return new ObjectMapper().readTree(getClass().getResourceAsStream("/tracker-profile-public-fixture.json"));}
 @Test void parsesActRatingAndSeparateAllTimePeakWithoutConfusingScopes()throws Exception{
  var result=TrackerProfiles.parse(fixture(),"venka#111",ACT);
  @SuppressWarnings("unchecked") var current=(Map<String,Object>)result.get("current");
  @SuppressWarnings("unchecked") var peak=(Map<String,Object>)result.get("peak");
  assertEquals(518,current.get("rr"));assertEquals(126,current.get("position"));assertEquals(827,peak.get("rr"));assertEquals("EPISODE 7: ACT II",peak.get("actName"));assertFalse(result.containsKey("userInfo"));
  @SuppressWarnings("unchecked") var wrongAct=(Map<String,Object>)TrackerProfiles.parse(fixture(),"venka#111","other-act").get("current");assertTrue(wrongAct.isEmpty());
 }
 @Test void rejectsWrongIdentityConsoleAndMissingStats()throws Exception{
  assertThrows(IllegalStateException.class,()->TrackerProfiles.parse(fixture(),"someone#other",ACT));
  var f=fixture();((ObjectNode)f.path("data").path("metadata")).put("defaultPlatform","console");assertThrows(IllegalStateException.class,()->TrackerProfiles.parse(f,"venka#111",ACT));
  assertThrows(IllegalStateException.class,()->TrackerProfiles.parse(new ObjectMapper().readTree("{}"),"venka#111",ACT));
 }
 @Test void cachesSuccessAndUnavailableResponses()throws Exception{
  var client=mock(TrackerLeaderboardClient.class);when(client.fetchProfile("venka#111",ACT)).thenReturn(fixture());var profiles=new TrackerProfiles(client,ACT);
  assertEquals(true,profiles.get("venka#111").get("available"));assertEquals(true,profiles.get("VENKA#111").get("available"));verify(client,times(1)).fetchProfile("venka#111",ACT);
  when(client.fetchProfile("other#test",ACT)).thenThrow(new IllegalStateException("HTTP 403"));assertEquals(false,profiles.get("other#test").get("available"));assertEquals(false,profiles.get("other#test").get("available"));verify(client,times(1)).fetchProfile("other#test",ACT);
 }
 @Test void exposesOnlyValidatedReportedCountryAndPersistsSuccessfulLookup()throws Exception{
  var f=fixture();((ObjectNode)f.path("data")).putObject("userInfo").put("countryCode","IN");
  assertEquals("in",TrackerProfiles.parse(f,"venka#111",ACT).get("country"));
  ((ObjectNode)f.path("data").path("userInfo")).put("countryCode","ZZ");assertFalse(TrackerProfiles.parse(f,"venka#111",ACT).containsKey("country"));
  ((ObjectNode)f.path("data").path("userInfo")).put("countryCode","IN");
  var client=mock(TrackerLeaderboardClient.class);var db=mock(org.springframework.jdbc.core.JdbcTemplate.class);when(client.fetchProfile("venka#111",ACT)).thenReturn(f);
  new TrackerProfiles(client,ACT,db).get("venka#111");verify(db).update(anyString(),eq("venka#111"),eq("in"));
 }
}
