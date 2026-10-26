package com.valorant.leaderboard;

import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GlobalObservationsTest {
  private TrackerLeaderboardClient.Player p(String region,String country,int rank,int rr){return new TrackerLeaderboardClient.Player("same#id",country,rank,rr,"Radiant",10,region);}
  private TrackerLeaderboardClient.Page page(TrackerLeaderboardClient.Player... rows){return new TrackerLeaderboardClient.Page(List.of(rows),OffsetDateTime.parse("2026-10-20T12:00:00Z"));}
  @Test void keepsUnknownCountriesAndSeparatesIdenticalNamesInDifferentRegions(){
    var data=new GlobalObservations();data.add(page(p("ap",null,1,500),p("eu","kr",2,500)),"global");
    assertEquals(2,data.size());assertNull(data.rows().iterator().next().player.country());assertEquals(2,data.regionCounts().size());
  }
  @Test void separatesGlobalAndRegionalRankAndFlagsStatsConflicts(){
    var data=new GlobalObservations();data.add(page(p("ap",null,50,500)),"global");data.add(page(p("ap",null,9,500)),"ap");
    var row=data.rows().iterator().next();assertEquals(50,row.globalRank);assertEquals(9,row.regionalRank);assertFalse(row.rankAmbiguous);assertFalse(row.statsAmbiguous);
    data.add(page(p("ap",null,8,510)),"ap");assertTrue(row.statsAmbiguous);assertTrue(row.rankAmbiguous);assertEquals(500,row.player.rr());assertEquals(1,data.size());
  }
}
