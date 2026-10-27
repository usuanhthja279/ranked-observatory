package com.valorant.leaderboard.matchintelligence;

/** Quality describes evidence completeness; it is not a gameplay rating. */
public enum MatchDataQuality {
  VALID,
  VALID_WITH_WARNINGS,
  PARTIAL,
  INVALID
}
