package com.valorant.leaderboard;

import java.time.OffsetDateTime;
import java.util.*;

/** Merges compatible observations without inventing a rank or combining conflicting stats. */
final class LeaderboardObservations {
  record Observation(TrackerLeaderboardClient.Player player, OffsetDateTime updated, boolean rankAmbiguous) {}
  private final Map<String,Observation> rows = new LinkedHashMap<>();
  private int rawRows;
  private int duplicates;

  void add(TrackerLeaderboardClient.Page page) {
    int added = 0;
    for (var player : page.players()) {
      rawRows++;
      String key = player.key().toLowerCase(Locale.ROOT);
      var previous = rows.get(key);
      if (previous == null) {
        rows.put(key,new Observation(player,page.updatedAt(),false));
        added++;
      } else {
        duplicates++;
        var old = previous.player();
        if (!old.country().equalsIgnoreCase(player.country()) || old.rr()!=player.rr()
            || !old.tier().equals(player.tier()) || old.wins()!=player.wins())
          throw new IllegalStateException("Conflicting duplicate player statistics; retry next cycle");
        // Keep the first observed row and source time; never synthesize a best/average rank.
        rows.put(key,new Observation(old,previous.updated(),previous.rankAmbiguous() || old.rank()!=player.rank()));
      }
    }
    if (page.players().size()==100 && added==0)
      throw new IllegalStateException("Full page contains no new players; pagination made no progress");
  }
  Collection<Observation> rows() { return rows.values(); }
  int size() { return rows.size(); }
  int rawRows() { return rawRows; }
  int duplicates() { return duplicates; }
  int ambiguousRanks() { return (int)rows.values().stream().filter(Observation::rankAmbiguous).count(); }
}
