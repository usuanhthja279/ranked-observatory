package com.valorant.leaderboard;

import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;

/** One-shot bootstrap from the act-validated, checksummed HTTP verification captures. Never a polling cache. */
final class VerifiedCapture {
  private final Path base;
  private final ObjectMapper mapper;
  private final JsonNode manifest;
  VerifiedCapture(String directory,String act,ObjectMapper mapper)throws Exception {
    this.base=Path.of(directory).toRealPath();this.mapper=mapper;
    manifest=mapper.readTree(Files.readString(base.resolve("manifest.json")));
    if(!act.equals(manifest.path("act").asText()) || manifest.path("regions").size()!=6)
      throw new IllegalStateException("Verification capture does not match the configured act/regions");
  }
  OffsetDateTime started(){return OffsetDateTime.parse(manifest.path("observedFrom").asText());}
  String through(){return manifest.path("observedThrough").asText();}
  TrackerLeaderboardClient.Page page(String source,int page)throws Exception {
    String name=source+"-"+page+".json";String expected=manifest.path("files").path(name).asText();
    if(expected.isBlank())throw new IllegalStateException("Missing verified page: "+name);
    byte[] bytes=Files.readAllBytes(base.resolve(name));
    if(!expected.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))))
      throw new IllegalStateException("Verification checksum failed: "+name);
    var data=mapper.readTree(bytes);
    if(!source.equals(data.path("region").asText())||page!=data.path("page").asInt()||!data.path("rows").isArray()||data.path("rows").size()>100)
      throw new IllegalStateException("Invalid verified page metadata");
    var players=new ArrayList<TrackerLeaderboardClient.Player>();OffsetDateTime updated=OffsetDateTime.parse(data.path("checkedAt").asText());
    for(var row:data.path("rows")){
      String region=row.path("region").asText(),country=row.path("country").asText("");if(country.isBlank())country=null;
      String player=row.path("player").asText(),tier=row.path("tier").asText();
      if(!TrackerLeaderboardClient.REGIONS.contains(region)||(!source.equals("global")&&!source.equals(region))
          ||player.isBlank()||player.length()>300||tier.isBlank()||(country!=null&&!country.matches("[a-z]{2}"))
          ||!row.path("rank").isIntegralNumber()||row.path("rank").asInt()<1||!row.path("rr").isIntegralNumber()||row.path("rr").asInt()<0
          ||!row.path("wins").isIntegralNumber()||row.path("wins").asInt()<0)throw new IllegalStateException("Invalid verified player");
      OffsetDateTime rowTime=OffsetDateTime.parse(row.path("sourceUpdated").asText());
      if(!players.isEmpty()&&!rowTime.equals(updated))throw new IllegalStateException("Mixed source times within verified page");
      updated=rowTime;players.add(new TrackerLeaderboardClient.Player(player,country,row.path("rank").asInt(),row.path("rr").asInt(),tier,row.path("wins").asInt(),region));
    }
    return new TrackerLeaderboardClient.Page(List.copyOf(players),updated);
  }
}
