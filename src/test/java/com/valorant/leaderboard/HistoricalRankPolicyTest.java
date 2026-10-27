package com.valorant.leaderboard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HistoricalRankPolicyTest {
  @Test void preservesTheExplicitEpisodeThreeAndV26Boundary() {
    assertEquals(HistoricalRankPolicy.EPISODE_3_BACKCAST, HistoricalRankPolicy.forArchiveOrder(31));
    assertEquals(HistoricalRankPolicy.V26_ACT_II_PLUS, HistoricalRankPolicy.forArchiveOrder(32));
    assertEquals(HistoricalRankPolicy.V26_ACT_II_PLUS, HistoricalRankPolicy.forArchiveOrder(33));
  }
}
