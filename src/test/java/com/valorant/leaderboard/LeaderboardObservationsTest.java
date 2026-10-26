package com.valorant.leaderboard;

import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LeaderboardObservationsTest {
  private final OffsetDateTime time=OffsetDateTime.parse("2026-10-20T12:00:00Z");
  private TrackerLeaderboardClient.Player player(String key,int rank,int rr) {
    return new TrackerLeaderboardClient.Player(key,"in",rank,rr,"Immortal 3",10);
  }
  private TrackerLeaderboardClient.Page page(TrackerLeaderboardClient.Player... players) {
    return new TrackerLeaderboardClient.Page(List.of(players),time);
  }
  @Test void mergesIdenticalRowsWithinAndAcrossPagesUsingCaseInsensitiveIdentity() {
    var rows=new LeaderboardObservations();
    rows.add(page(player("Player#Tag",81,400),player("player#tag",81,400)));
    rows.add(page(player("PLAYER#TAG",81,400)));
    assertEquals(1,rows.size());assertEquals(3,rows.rawRows());assertEquals(2,rows.duplicates());assertEquals(0,rows.ambiguousRanks());
  }
  @Test void conflictingRanksKeepFirstObservationAndAreFlagged() {
    var rows=new LeaderboardObservations();rows.add(page(player("a#b",81,400)));rows.add(page(player("a#b",80,400)));
    assertEquals(81,rows.rows().iterator().next().player().rank());assertEquals(1,rows.ambiguousRanks());
    assertEquals(time,rows.rows().iterator().next().updated());
  }
  @Test void conflictingRatingWinsTierOrCountryAreNotMerged() {
    for(var conflicting:List.of(player("a#b",81,401),
        new TrackerLeaderboardClient.Player("a#b","in",81,400,"Radiant",10),
        new TrackerLeaderboardClient.Player("a#b","in",81,400,"Immortal 3",11),
        new TrackerLeaderboardClient.Player("a#b","ph",81,400,"Immortal 3",10))) {
      var rows=new LeaderboardObservations();rows.add(page(player("a#b",81,400)));
      assertThrows(IllegalStateException.class,()->rows.add(page(conflicting)));
    }
  }
  @Test void repeatedFullPageFailsButTerminalOverlapIsAccepted() {
    var full=new ArrayList<TrackerLeaderboardClient.Player>();for(int i=0;i<100;i++)full.add(player("player"+i+"#id",i+1,400));
    var source=new TrackerLeaderboardClient.Page(full,time);
    var rows=new LeaderboardObservations();rows.add(source);
    assertThrows(IllegalStateException.class,()->rows.add(source));
    var terminal=new LeaderboardObservations();terminal.add(source);terminal.add(page(full.getLast()));
    assertEquals(100,terminal.size());assertEquals(1,terminal.duplicates());
  }
}
