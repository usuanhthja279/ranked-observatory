package com.valorant.leaderboard;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@Service
public class TrackerProfiles {
 private final TrackerLeaderboardClient client;private final String act;
 private record Entry(Map<String,Object> value,Instant expires){}
 private final Map<String,Entry> cache=new LinkedHashMap<>(256,.75f,true);
 private final Semaphore permits=new Semaphore(2);
 private final JdbcTemplate db;
 public TrackerProfiles(TrackerLeaderboardClient client,String act){this(client,act,null);}
 @Autowired public TrackerProfiles(TrackerLeaderboardClient client,@Value("${leaderboard.act:d816f426-48ea-f052-117f-9697a155b319}")String act,JdbcTemplate db){this.client=client;this.act=act;this.db=db;}
 public Map<String,Object> get(String player){
  String key=player.toLowerCase(Locale.ROOT);synchronized(cache){var entry=cache.get(key);if(entry!=null&&entry.expires.isAfter(Instant.now()))return entry.value;}
  if(!permits.tryAcquire())return Map.of("available",false,"message","Profile lookup busy. Snapshot statistics remain available.");
  Map<String,Object> result;
  try{result=parse(client.fetchProfile(player,act),player,act);
   if(db!=null&&result.containsKey("country"))db.update("insert into reported_profile_countries(player_key,country,fetched_at) values(lower(?),?,current_timestamp) on conflict(player_key) do update set country=excluded.country,fetched_at=excluded.fetched_at",player,result.get("country"));
  }
  catch(InterruptedException e){Thread.currentThread().interrupt();result=Map.of("available",false,"message","Profile lookup interrupted.");}
  catch(Exception e){result=Map.of("available",false,"message","Tracker profile is unavailable or private. Snapshot statistics remain available.");}
  finally{permits.release();}
  int minutes=Boolean.TRUE.equals(result.get("available"))?15:5;
  synchronized(cache){cache.put(key,new Entry(result,Instant.now().plusSeconds(minutes*60L)));while(cache.size()>256)cache.remove(cache.keySet().iterator().next());}
  return result;
 }
 static Map<String,Object> parse(JsonNode root,String player,String act){
  JsonNode data=root.path("data");
  if(!data.path("platformInfo").path("platformUserIdentifier").asText().equalsIgnoreCase(player)||!data.path("metadata").path("defaultPlatform").asText().equals("pc"))throw new IllegalStateException("Profile identity/platform mismatch");
  Map<String,Object> current=Map.of(),peak=Map.of();
  for(JsonNode segment:data.path("segments")){
   if(!segment.path("attributes").path("playlist").asText().equals("competitive"))continue;
   if(segment.path("type").asText().equals("season")&&segment.path("attributes").path("seasonId").asText().equals(act))current=rating(segment.path("stats").path("rank"));
   if(segment.path("type").asText().equals("peak-rating"))peak=rating(segment.path("stats").path("peakRating"));
  }
  String country=data.path("userInfo").path("countryCode").asText("").toUpperCase(Locale.ROOT);
  boolean validCountry=Arrays.asList(Locale.getISOCountries()).contains(country);
  if(current.isEmpty()&&peak.isEmpty()&&!validCountry)throw new IllegalStateException("Profile ratings and country absent");
  var result=new LinkedHashMap<String,Object>();result.putAll(Map.of("available",true,"current",current,"peak",peak,"act",act,"shard",data.path("metadata").path("activeShard").asText(),"fetchedAt",Instant.now().toString(),"providerCacheExpiresAt",data.path("expiryDate").asText()));
  String avatar=data.path("platformInfo").path("avatarUrl").asText("");
  if(avatar.isBlank())avatar=data.path("userInfo").path("avatarUrl").asText("");
  try{var uri=java.net.URI.create(avatar);if("https".equalsIgnoreCase(uri.getScheme())&&uri.getHost()!=null)result.put("avatarUrl",uri.toString());}catch(IllegalArgumentException ignored){}
  if(validCountry){result.put("country",country.toLowerCase(Locale.ROOT));result.put("countrySource","Tracker profile · reported country");}
  return Collections.unmodifiableMap(result);
 }
 private static Map<String,Object> rating(JsonNode stat){
  if(!stat.path("value").isNumber()||stat.path("value").asInt()<0||stat.path("metadata").path("tierName").asText().isBlank())return Map.of();
  var result=new LinkedHashMap<String,Object>();result.put("rr",stat.path("value").asInt());result.put("tier",stat.path("metadata").path("tierName").asText());
  if(stat.path("rank").isIntegralNumber()&&stat.path("rank").asInt()>0)result.put("position",stat.path("rank").asInt());
  if(!stat.path("metadata").path("actId").asText().isBlank())result.put("actId",stat.path("metadata").path("actId").asText());
  if(!stat.path("metadata").path("actName").asText().isBlank())result.put("actName",stat.path("metadata").path("actName").asText());
  return Collections.unmodifiableMap(result);
 }
}
