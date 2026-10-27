package com.valorant.leaderboard.matchintelligence;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Checks internal source arithmetic without changing or imputing any Tracker values. */
public class MatchReconciliationService {
  public record Result(List<MatchValidationIssue> issues, boolean partial,
      int rounds, int participants, int playerRoundRows, int damageRows,
      int killRows, int creditedKills, int specialEvents, int objectiveEvents,
      int positionSnapshots) {
    public Result { issues = List.copyOf(issues); }
  }

  private static final int MAX_ROUNDS = 60;
  private final MatchIdentityResolver identities;

  public MatchReconciliationService(MatchIdentityResolver identities) { this.identities = identities; }

  public Result inspect(JsonNode data) {
    List<MatchValidationIssue> issues = new ArrayList<>();
    JsonNode metadata = data.path("metadata");
    int roundCount = integer(metadata.path("rounds"), -1);
    if (roundCount < 1 || roundCount > MAX_ROUNDS) {
      error(issues, "ROUND_COUNT_INVALID", "data.metadata.rounds", "Round count is missing or outside the supported range.");
      return new Result(issues, true, Math.max(roundCount, 0), 0, 0, 0, 0, 0, 0, 0, 0);
    }
    JsonNode segments = data.path("segments");
    List<JsonNode> roundResults = byType(segments, "round-summary");
    List<JsonNode> playerRounds = byType(segments, "player-round");
    List<JsonNode> participantSummaries = byType(segments, "player-summary");
    List<JsonNode> teamSummaries = byType(segments, "team-summary");
    List<JsonNode> damageRows = byType(segments, "player-round-damage");
    List<JsonNode> killRows = byType(segments, "player-round-kills");

    boolean partial = false;
    if (roundResults.size() != roundCount) {
      error(issues, "ROUND_RESULT_COVERAGE", "data.segments[round-summary]", "Round result rows do not cover the declared rounds.");
    }
    Map<Integer, JsonNode> roundByNumber = new TreeMap<>();
    Map<String, Integer> winnerCounts = new HashMap<>();
    for (int i = 0; i < roundResults.size(); i++) {
      JsonNode row = roundResults.get(i);
      int round = integer(row.path("attributes").path("round"), -1);
      if (round < 1 || round > roundCount) {
        error(issues, "ROUND_NUMBER_INVALID", "data.segments[round-summary][" + i + "]", "Round result has an invalid round number.");
        continue;
      }
      if (roundByNumber.putIfAbsent(round, row) != null) {
        error(issues, "ROUND_DUPLICATE", "data.segments[round-summary][" + i + "]", "Round result number is duplicated.");
        continue;
      }
      String winner = text(row.path("stats").path("winningTeam").path("value"));
      if (winner.isBlank()) {
        partial = true;
        warning(issues, "ROUND_WINNER_MISSING", "data.segments[round-summary][" + i + "]", "Round winner is unavailable.");
      } else winnerCounts.merge(winner, 1, Integer::sum);
    }
    for (int round = 1; round <= roundCount; round++) {
      if (!roundByNumber.containsKey(round)) error(issues, "ROUND_MISSING", "data.segments[round-summary]", "A declared round has no result row.");
    }

    Map<String, JsonNode> summariesByIdentity = new HashMap<>();
    for (int i = 0; i < participantSummaries.size(); i++) {
      JsonNode summary = participantSummaries.get(i);
      MatchIdentityResolver.Identity id = identities.fromAttributes(summary.path("attributes"));
      if (!id.hasProviderIdentity()) {
        partial = true;
        warning(issues, "PARTICIPANT_IDENTITY_MISSING", "data.segments[player-summary][" + i + "]", "Provider identity is unavailable; this participant cannot be joined reliably.");
        continue;
      }
      if (summariesByIdentity.putIfAbsent(id.stableKey(), summary) != null) {
        error(issues, "PARTICIPANT_IDENTITY_DUPLICATE", "data.segments[player-summary][" + i + "]", "Participant identity is duplicated in match summaries.");
      }
    }
    if (participantSummaries.size() < 2) error(issues, "PARTICIPANT_SUMMARIES_MISSING", "data.segments[player-summary]", "Both teams require participant summary evidence.");
    if ("bomb".equalsIgnoreCase(text(metadata.path("modeKey"))) && metadata.path("isTeamBased").asBoolean(false)
        && participantSummaries.size() != 10) {
      partial = true;
      warning(issues, "PARTICIPANT_ROSTER_PARTIAL", "data.segments[player-summary]", "Expected ten roster summaries for the observed competitive team mode; available participants remain usable with partial quality.");
    }

    Map<Integer, Set<String>> roundParticipants = new HashMap<>();
    Map<String, int[]> roundKda = new HashMap<>();
    Map<String, Set<Integer>> participantRoundNumbers = new HashMap<>();
    for (int i = 0; i < playerRounds.size(); i++) {
      JsonNode row = playerRounds.get(i);
      int round = integer(row.path("attributes").path("round"), -1);
      if (round < 1 || round > roundCount) {
        error(issues, "PLAYER_ROUND_NUMBER_INVALID", "data.segments[player-round][" + i + "]", "Player round has an invalid round number.");
        continue;
      }
      MatchIdentityResolver.Identity id = identities.fromAttributes(row.path("attributes"));
      if (!id.hasProviderIdentity()) {
        partial = true;
        warning(issues, "PLAYER_ROUND_IDENTITY_MISSING", "data.segments[player-round][" + i + "]", "Player-round identity is unavailable.");
        continue;
      }
      String key = id.stableKey();
      if (!roundParticipants.computeIfAbsent(round, ignored -> new HashSet<>()).add(key)) {
        error(issues, "PLAYER_ROUND_DUPLICATE", "data.segments[player-round][" + i + "]", "A participant has more than one row for the same round.");
      }
      participantRoundNumbers.computeIfAbsent(key, ignored -> new HashSet<>()).add(round);
      int[] total = roundKda.computeIfAbsent(key, ignored -> new int[3]);
      String[] fields = {"kills", "deaths", "assists"};
      for (int f = 0; f < fields.length; f++) {
        Integer value = statInteger(row, fields[f]);
        if (value == null) {
          partial = true;
          warning(issues, "PLAYER_ROUND_STAT_MISSING", "data.segments[player-round][" + i + "].stats." + fields[f], "A round statistic is unavailable; it was not treated as zero.");
        } else if (value < 0) {
          error(issues, "PLAYER_ROUND_STAT_INVALID", "data.segments[player-round][" + i + "].stats." + fields[f], "Round statistics cannot be negative.");
        } else total[f] += value;
      }
    }

    if (participantSummaries.size() == 10 && playerRounds.size() != roundCount * 10) {
      partial = true;
      warning(issues, "PLAYER_ROUND_COVERAGE_PARTIAL", "data.segments[player-round]", "Not every roster slot has an observed row for every round.");
    }
    for (Map.Entry<String, JsonNode> entry : summariesByIdentity.entrySet()) {
      Set<Integer> observedRounds = participantRoundNumbers.getOrDefault(entry.getKey(), Set.of());
      if (observedRounds.size() != roundCount) {
        partial = true;
        warning(issues, "PARTICIPANT_ROUND_COVERAGE", "data.segments[player-round]", "A participant has incomplete round coverage.");
      }
      int[] sums = roundKda.getOrDefault(entry.getKey(), new int[3]);
      String[] fields = {"kills", "deaths", "assists"};
      for (int f = 0; f < fields.length; f++) {
        Integer aggregate = statInteger(entry.getValue(), fields[f]);
        if (aggregate == null) {
          partial = true;
          warning(issues, "PARTICIPANT_TOTAL_MISSING", "data.segments[player-summary].stats." + fields[f], "A participant aggregate is unavailable.");
        } else if (aggregate != sums[f]) {
          error(issues, "PARTICIPANT_KDA_MISMATCH", "data.segments[player-summary].stats." + fields[f], "Participant round totals do not reconcile with the match summary.");
        }
      }
    }

    Map<String, JsonNode> teamsById = new HashMap<>();
    for (int i = 0; i < teamSummaries.size(); i++) {
      JsonNode team = teamSummaries.get(i);
      String teamId = text(team.path("attributes").path("teamId"));
      if (teamId.isBlank() || teamsById.putIfAbsent(teamId, team) != null) {
        error(issues, "TEAM_ID_INVALID", "data.segments[team-summary][" + i + "]", "Team summary identity is missing or duplicated.");
      }
    }
    if (teamsById.size() != 2) error(issues, "TEAM_SUMMARY_COVERAGE", "data.segments[team-summary]", "Exactly two team summaries are required for a competitive match.");
    for (String winner : winnerCounts.keySet()) {
      if (!teamsById.containsKey(winner)) error(issues, "ROUND_WINNER_TEAM_UNKNOWN", "data.segments[round-summary].stats.winningTeam", "Round winner does not match a known team.");
    }
    for (JsonNode summary : participantSummaries) {
      String teamId = text(summary.path("metadata").path("teamId"));
      if (teamId.isBlank() || !teamsById.containsKey(teamId)) error(issues, "PARTICIPANT_TEAM_UNKNOWN", "data.segments[player-summary].metadata.teamId", "Participant is not assigned to a summarized team.");
    }
    if (!teamsById.isEmpty()) {
      for (Map.Entry<String, JsonNode> entry : teamsById.entrySet()) {
        int observedWins = winnerCounts.getOrDefault(entry.getKey(), 0);
        Integer reportedWins = statInteger(entry.getValue(), "roundsWon");
        if (reportedWins == null) {
          partial = true;
          warning(issues, "TEAM_SCORE_MISSING", "data.segments[team-summary].stats.roundsWon", "Team score is unavailable.");
        } else if (reportedWins != observedWins) {
          error(issues, "TEAM_SCORE_MISMATCH", "data.segments[team-summary].stats.roundsWon", "Team score does not match the round winners.");
        }
        Integer reportedLosses = statInteger(entry.getValue(), "roundsLost");
        if (reportedLosses != null && reportedLosses != roundCount - observedWins) {
          error(issues, "TEAM_ROUNDS_LOST_MISMATCH", "data.segments[team-summary].stats.roundsLost", "Team rounds-lost count does not match the round winners.");
        }
        List<JsonNode> members = participantSummaries.stream()
            .filter(p -> entry.getKey().equals(text(p.path("metadata").path("teamId")))).toList();
        for (String field : List.of("kills", "deaths", "assists", "damage")) {
          Integer reported = statInteger(entry.getValue(), field);
          List<Integer> memberValues = members.stream().map(p -> statInteger(p, field)).toList();
          if (reported == null || memberValues.stream().anyMatch(Objects::isNull)) {
            partial = true;
            warning(issues, "TEAM_TOTAL_MISSING", "data.segments[team-summary].stats." + field, "Team and participant totals are not both available for this reconciliation.");
          } else if (reported != memberValues.stream().mapToInt(Integer::intValue).sum()) {
            error(issues, "TEAM_TOTAL_MISMATCH", "data.segments[team-summary].stats." + field, "Team total does not match participant aggregates.");
          }
        }
      }
    }
    for (int i = 0; i < playerRounds.size(); i++) {
      JsonNode row = playerRounds.get(i);
      MatchIdentityResolver.Identity id = identities.fromAttributes(row.path("attributes"));
      if (id.hasProviderIdentity() && !summariesByIdentity.containsKey(id.stableKey())) {
        error(issues, "ORPHAN_PLAYER_ROUND", "data.segments[player-round][" + i + "]", "Player-round identity has no matching participant summary.");
      }
      String teamId = text(row.path("metadata").path("teamId"));
      if (!teamId.isBlank() && !teamsById.containsKey(teamId)) {
        error(issues, "PLAYER_ROUND_TEAM_UNKNOWN", "data.segments[player-round][" + i + "].metadata.teamId", "Player-round team is not present in the match summaries.");
      }
      String side = text(row.path("metadata").path("teamSide"));
      if (!side.isBlank() && !Set.of("attacker", "defender").contains(side.toLowerCase(Locale.ROOT))) {
        partial = true;
        warning(issues, "PLAYER_ROUND_SIDE_UNKNOWN", "data.segments[player-round][" + i + "].metadata.teamSide", "Unknown side label was preserved.");
      }
    }

    int creditedKills = 0, specialEvents = 0, objectiveEvents = 0, positionSnapshots = 0;
    Set<String> eventSignatures = new HashSet<>();
    for (int i = 0; i < killRows.size(); i++) {
      JsonNode row = killRows.get(i);
      int round = integer(row.path("attributes").path("round"), -1);
      if (round < 1 || round > roundCount) {
        error(issues, "EVENT_ROUND_INVALID", "data.segments[player-round-kills][" + i + "]", "Kill event has an invalid round number.");
        continue;
      }
      JsonNode attrs = row.path("attributes"), eventMeta = row.path("metadata");
      MatchIdentityResolver.Identity actor = identities.fromAttributes(attrs);
      MatchIdentityResolver.Identity target = new MatchIdentityResolver.Identity(
          text(attrs.path("opponentPlatformSlug")), text(attrs.path("opponentPlatformUserIdentifier")),
          text(attrs.path("opponentPlatformUserIdentifier")));
      if (!actor.hasProviderIdentity() || !target.hasProviderIdentity()) {
        partial = true;
        warning(issues, "EVENT_IDENTITY_MISSING", "data.segments[player-round-kills][" + i + "]", "Event actor or target identity is unavailable.");
      } else if (actor.stableKey().equals(target.stableKey())) specialEvents++;
      else creditedKills++;
      for (String timeField : List.of("gameTime", "roundTime")) {
        JsonNode time = eventMeta.path(timeField);
        if (!time.isMissingNode() && !time.isNull() && (!time.isNumber() || time.longValue() < 0)) {
          error(issues, "EVENT_TIME_INVALID", "data.segments[player-round-kills][" + i + "].metadata." + timeField, "Event time must be a non-negative numeric source value.");
        }
      }
      JsonNode locations = eventMeta.path("playerLocations");
      if (locations.isArray()) {
        for (JsonNode location : locations) {
          if (validPosition(location.path("location"))) positionSnapshots++;
          else warning(issues, "POSITION_INVALID", "data.segments[player-round-kills][" + i + "].metadata.playerLocations", "A position snapshot is malformed and was not normalized.");
        }
      }
      if (validPosition(eventMeta.path("opponentLocation"))) positionSnapshots++;
      String signature = round + "|" + actor.stableKey() + "|" + target.stableKey() + "|" + eventMeta.path("gameTime") + "|" + eventMeta.path("roundTime") + "|" + eventMeta.path("weaponName");
      if (!eventSignatures.add(signature)) warning(issues, "EVENT_DUPLICATE_CANDIDATE", "data.segments[player-round-kills][" + i + "]", "An identical source signature was retained and flagged; no event was silently dropped.");
    }
    for (JsonNode row : roundResults) {
      JsonNode meta = row.path("metadata");
      if (meta.hasNonNull("plant")) objectiveEvents++;
      if (meta.hasNonNull("defuse")) objectiveEvents++;
      JsonNode exploded = meta.path("exploded");
      if (exploded.isNumber() || (exploded.isBoolean() && exploded.booleanValue())) objectiveEvents++;
    }
    boolean anyPositions = killRows.stream().anyMatch(k -> k.path("metadata").path("opponentLocation").isObject()
        || k.path("metadata").path("playerLocations").isArray());
    if (metadata.path("extendedDataIncluded").asBoolean(false) && killRows.isEmpty()) {
      partial = true;
      warning(issues, "EXTENDED_EVENTS_MISSING", "data.segments[player-round-kills]", "Extended data is marked included but no kill-event rows were supplied.");
    }
    int aggregateKills = participantSummaries.stream().mapToInt(s -> Optional.ofNullable(statInteger(s, "kills")).orElse(0)).sum();
    if (!killRows.isEmpty() && aggregateKills != creditedKills) {
      error(issues, "CREDITED_KILL_RECONCILIATION", "data.segments[player-round-kills]", "Credited enemy-kill events do not match participant kill totals.");
    }
    int aggregateDeaths = participantSummaries.stream().mapToInt(s -> Optional.ofNullable(statInteger(s, "deaths")).orElse(0)).sum();
    if (!killRows.isEmpty() && aggregateDeaths != creditedKills) {
      warning(issues, "DEATH_EVENT_COVERAGE_DIFFERENCE", "data.segments[player-round-kills]", "Participant death totals differ from credited enemy-kill events; the difference is preserved for review.");
    }
    int unknownSegments = 0;
    Set<String> knownTypes = Set.of("team-summary", "round-summary", "player-round", "player-round-damage", "player-summary", "player-loadout", "player-round-kills");
    for (JsonNode segment : segments) {
      String type = text(segment.path("type"));
      if (!knownTypes.contains(type)) {
        unknownSegments++;
        warning(issues, "UNKNOWN_SOURCE_SEGMENT", "data.segments", "An unrecognized source segment was preserved without assigning meaning.");
      }
    }
    if (!roundByNumber.isEmpty() && roundParticipants.size() < roundCount) {
      partial = true;
      warning(issues, "ROUND_PARTICIPANT_COVERAGE", "data.segments[player-round]", "One or more rounds have no participant rows.");
    }
    return new Result(issues, partial, roundCount, participantSummaries.size(), playerRounds.size(),
        damageRows.size(), killRows.size(), creditedKills, specialEvents, objectiveEvents,
        positionSnapshots);
  }

  private static boolean validPosition(JsonNode node) {
    return node != null && node.isObject() && finite(node.path("x")) && finite(node.path("y"));
  }
  private static boolean finite(JsonNode node) { return node != null && node.isNumber() && Double.isFinite(node.doubleValue()); }
  static List<JsonNode> byType(JsonNode segments, String type) {
    List<JsonNode> result = new ArrayList<>();
    if (segments != null && segments.isArray()) for (JsonNode node : segments) if (type.equals(text(node.path("type")))) result.add(node);
    return result;
  }
  static Integer statInteger(JsonNode segment, String name) {
    JsonNode value = segment.path("stats").path(name).path("value");
    return value.isIntegralNumber() && value.canConvertToInt() ? value.intValue() : null;
  }
  static int integer(JsonNode node, int fallback) { return node != null && node.canConvertToInt() ? node.intValue() : fallback; }
  static String text(JsonNode node) { return MatchIdentityResolver.text(node); }
  private static void error(List<MatchValidationIssue> issues, String code, String path, String message) { issues.add(new MatchValidationIssue(MatchValidationIssue.Severity.ERROR, code, path, message)); }
  private static void warning(List<MatchValidationIssue> issues, String code, String path, String message) { issues.add(new MatchValidationIssue(MatchValidationIssue.Severity.WARNING, code, path, message)); }
}
