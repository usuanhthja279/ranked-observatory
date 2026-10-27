package com.valorant.leaderboard;

/** Named, persisted interpretations for source ladders that report combined Immortal tiers. */
enum HistoricalRankPolicy {
  EPISODE_3_BACKCAST,
  V26_ACT_II_PLUS;

  static HistoricalRankPolicy forArchiveOrder(int order) {
    return order >= 32 ? V26_ACT_II_PLUS : EPISODE_3_BACKCAST;
  }
}
