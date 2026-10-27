package com.valorant.leaderboard.matchintelligence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;

/** Anonymized, source-shaped fixture; values are synthetic and contain no raw player records. */
public final class MatchIntelligenceFixtures {
  public static final ObjectMapper JSON = new ObjectMapper();
  public static final String MATCH_ID = "11111111-2222-4333-8444-555555555555";
  public static final String PLAYER = "Player 0#TAG";

  private MatchIntelligenceFixtures() { }

  public static ObjectNode export(int roundCount, int blueWins) {
    ObjectNode root = JSON.createObjectNode();
    root.put("schemaVersion", 1); root.put("source", "tracker.gg"); root.put("player", PLAYER);
    root.put("exportedAt", "2026-10-27T14:52:55.450Z"); root.put("requestedMatches", 1);
    root.put("responseCount", 1); root.put("complete", true); root.putArray("failures");
    ArrayNode matches = root.putArray("matches");
    ObjectNode entry = matches.addObject(); entry.put("matchId", MATCH_ID); entry.put("validation", "passed");
    ObjectNode data = entry.putObject("response").putObject("data");
    data.putObject("attributes").put("id", MATCH_ID).put("modeKey", "bomb");
    ObjectNode meta = data.putObject("metadata");
    meta.put("source", "riot").put("modeKey", "bomb").put("modeName", "Competitive")
        .put("queueId", "competitive").put("seasonId", "synthetic-act").put("map", "synthetic-map")
        .put("mapName", "Synthetic Map").put("rounds", roundCount).put("modeMaxRounds", 25)
        .put("duration", roundCount * 60_000L).put("dateStarted", "2026-10-27T14:00:00Z")
        .put("isRanked", true).put("isTeamBased", true).put("isRoundBased", true)
        .put("extendedDataIncluded", true).put("extendedDataAvailable", true);
    meta.putObject("mapDetails").put("xMultiplier", 0.000081).put("yMultiplier", -0.000081)
        .put("xScalarToAdd", 0.5).put("yScalarToAdd", 0.5);
    ArrayNode segments = data.putArray("segments");
    int redWins = roundCount - blueWins;
    addTeam(segments, "Blue", blueWins, redWins, 0, 200);
    addTeam(segments, "Red", redWins, blueWins, 1, 0);
    for (int r = 1; r <= roundCount; r++) {
      ObjectNode round = segment(segments, "round-summary");
      round.putObject("attributes").put("round", r);
      String winner = r <= blueWins ? "Blue" : "Red";
      ObjectNode roundStats = round.putObject("stats");
      roundStats.putObject("winningTeam").put("value", winner);
      roundStats.putObject("roundResult").put("value", "Elimination");
      ObjectNode roundMeta = round.putObject("metadata");
      if (r == 1) roundMeta.putObject("plant").put("platformUserIdentifier", "Player 1#TAG")
          .putObject("location").put("x", 1200).put("y", -500);
    }
    for (int i = 0; i < 10; i++) addParticipant(segments, i);
    for (int r = 1; r <= roundCount; r++) {
      for (int i = 0; i < 10; i++) {
        ObjectNode row = segment(segments, "player-round");
        playerAttrs(row, i, r);
        String team = i < 5 ? "Blue" : "Red";
        row.putObject("metadata").put("teamId", team).put("teamSide", (r % 2 == 1) == (i < 5) ? "attacker" : "defender");
        ObjectNode stats = row.putObject("stats");
        int kills = i == 0 && r == 1 ? 1 : 0, deaths = i == 5 && r == 1 ? 1 : 0;
        stats.putObject("kills").put("value", kills); stats.putObject("deaths").put("value", deaths);
        stats.putObject("assists").put("value", 0); stats.putObject("damage").put("value", kills == 1 ? 200 : 0);
        stats.putObject("score").put("value", kills == 1 ? 300 : 0); stats.putObject("loadoutValue").put("value", 800);
        stats.putObject("spentCredits").put("value", 800); stats.putObject("remainingCredits").put("value", 200);
      }
    }
    addDamage(segments);
    addKill(segments, 1, "Player 0#TAG", "Player 5#TAG", "Rifle", "Weapon", 200);
    addUnknownSegment(segments);
    return root;
  }

  static ObjectNode matchEntry(ObjectNode export) { return (ObjectNode) export.path("matches").get(0); }
  static ArrayNode segments(ObjectNode export) { return (ArrayNode) matchEntry(export).path("response").path("data").path("segments"); }

  static void addKill(ArrayNode segments, int round, String actor, String target,
      String weapon, String damageType, int damage) {
    ObjectNode row = segment(segments, "player-round-kills");
    ObjectNode attrs = row.putObject("attributes");
    attrs.put("platformSlug", "riot").put("platformUserIdentifier", actor)
        .put("opponentPlatformSlug", "riot").put("opponentPlatformUserIdentifier", target).put("round", round);
    ObjectNode meta = row.putObject("metadata");
    meta.put("gameTime", 5000L + round).put("roundTime", 3000L).put("weaponName", weapon)
        .put("weaponCategory", damageType).putArray("assistants");
    meta.putObject("finishingDamage").put("damageType", damageType).put("damageItem", weapon);
    meta.putObject("opponentLocation").put("x", 400).put("y", -900);
    meta.putArray("playerLocations").addObject().put("platformUserIdentifier", actor)
        .putObject("location").put("x", 250).put("y", -750);
    row.putObject("stats").putObject("damage").put("value", damage);
  }

  static void addUnknownSegment(ArrayNode segments) {
    segment(segments, "future-event-v9").putObject("attributes").put("round", 1);
  }

  private static void addTeam(ArrayNode segments, String id, int won, int lost, int side, int damage) {
    ObjectNode team = segment(segments, "team-summary"); team.putObject("attributes").put("teamId", id);
    team.putObject("metadata").put("name", id).put("hasWon", won > lost);
    ObjectNode stats = team.putObject("stats");
    int kills = side == 0 ? 1 : 0, deaths = side == 1 ? 1 : 0;
    stats.putObject("roundsWon").put("value", won); stats.putObject("roundsLost").put("value", lost);
    stats.putObject("kills").put("value", kills); stats.putObject("deaths").put("value", deaths);
    stats.putObject("assists").put("value", 0); stats.putObject("damage").put("value", damage);
  }

  private static void addParticipant(ArrayNode segments, int index) {
    ObjectNode summary = segment(segments, "player-summary"); playerAttrs(summary, index, null);
    ((ObjectNode) summary.path("attributes")).remove("round");
    summary.putObject("metadata").put("agentName", "Agent " + index).put("teamId", index < 5 ? "Blue" : "Red");
    ObjectNode stats = summary.putObject("stats");
    stats.putObject("kills").put("value", index == 0 ? 1 : 0); stats.putObject("deaths").put("value", index == 5 ? 1 : 0);
    stats.putObject("assists").put("value", 0); stats.putObject("damage").put("value", index == 0 ? 200 : 0);
    stats.putObject("score").put("value", index == 0 ? 300 : 0); stats.putObject("scorePerRound").put("value", index == 0 ? 150.0 : 0.0);
    stats.putObject("damagePerRound").put("value", index == 0 ? 100.0 : 0.0); stats.putObject("hsAccuracy").put("value", 50.0);
    stats.putObject("kast").put("value", 50.0); stats.putObject("firstKills").put("value", index == 0 ? 1 : 0);
    stats.putObject("firstDeaths").put("value", 0); stats.putObject("currRank").put("value", 25);
  }

  private static void addDamage(ArrayNode segments) {
    ObjectNode row = segment(segments, "player-round-damage");
    row.putObject("attributes").put("platformSlug", "riot").put("platformUserIdentifier", "Player 0#TAG")
        .put("opponentPlatformSlug", "riot").put("opponentPlatformUserIdentifier", "Player 5#TAG").put("round", 1);
    ObjectNode stats = row.putObject("stats"); stats.putObject("damage").put("value", 200);
    stats.putObject("headshots").put("value", 1); stats.putObject("bodyshots").put("value", 0); stats.putObject("legshots").put("value", 0);
  }

  private static ObjectNode segment(ArrayNode segments, String type) { return segments.addObject().put("type", type); }
  private static void playerAttrs(ObjectNode row, int index, Integer round) {
    ObjectNode attrs = row.putObject("attributes"); attrs.put("platformSlug", "riot").put("platformUserIdentifier", "Player " + index + "#TAG");
    if (round != null) attrs.put("round", round);
  }
}
