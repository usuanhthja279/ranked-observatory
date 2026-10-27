package com.valorant.leaderboard.matchintelligence;

import static com.valorant.leaderboard.matchintelligence.CanonicalMatch.EventCategory.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.Test;

class MatchExportParserTest {
  private final MatchExportParser parser = new MatchExportParser(MatchIntelligenceFixtures.JSON);

  @Test void parsesSanitizedTrackerShapeAndRetainsTraceableObservedData() throws Exception {
    var report = parse(MatchIntelligenceFixtures.export(2, 2));
    assertEquals(1, report.acceptedMatches(), () -> report.matches().toString());
    var parsed = report.matches().getFirst();
    assertEquals(MatchDataQuality.VALID_WITH_WARNINGS, parsed.quality()); // unknown source segment is retained and disclosed
    var match = parsed.match();
    assertNotNull(match);
    assertEquals(MatchIntelligenceFixtures.MATCH_ID, match.providerMatchId());
    assertEquals("riot:player 0#tag", match.selectedPlayerIdentity());
    assertEquals("Blue", match.selectedPlayerTeamId());
    assertEquals(2, match.rounds().size());
    assertEquals("Elimination", match.rounds().getFirst().rawWinMethod());
    assertEquals(1, parsed.counts().creditedPlayerKills());
    assertEquals(1, parsed.counts().objectiveEvents());
    assertEquals(2, parsed.counts().positionSnapshots());
    assertTrue(match.availability().economy());
    assertFalse(match.availability().roundStartEndEvents());
    assertEquals(0, match.playerRounds().get(1).kills());
    assertEquals(800, match.playerRounds().get(1).loadoutValue());
    assertEquals("attacker", match.playerRounds().get(0).side());
    assertEquals("defender", match.playerRounds().get(10).side());
    assertEquals("TRACKER_WORLD_RAW", match.positions().getFirst().coordinateSpace());
    assertEquals("tracker.gg", match.provenance().provider());
    assertEquals("riot", match.responseSource());
    assertEquals(64, match.provenance().payloadSha256().length());
    assertTrue(match.events().stream().anyMatch(e -> e.category() == CREDITED_PLAYER_KILL && e.sourceRef().contains("player-round-kills")));
    assertTrue(match.events().stream().anyMatch(e -> e.category() == PLAYER_DEATH));
    var unknown = match.events().stream().filter(e -> e.category() == UNKNOWN_SOURCE_EVENT).findFirst().orElseThrow();
    assertEquals("future-event-v9", unknown.rawSourceType());
    assertTrue(unknown.sourceEvidence().path("type").asText().startsWith("future-event"));
  }

  @Test void selfReferentialAbilityBombAndFallEventsAreNotCountedAsEnemyKills() throws Exception {
    ObjectNode export = MatchIntelligenceFixtures.export(2, 2);
    ArrayNode segments = MatchIntelligenceFixtures.segments(export);
    MatchIntelligenceFixtures.addKill(segments, 1, "Player 1#TAG", "Player 1#TAG", "Not Dead Yet", "Ability", 0);
    MatchIntelligenceFixtures.addKill(segments, 2, "Player 2#TAG", "Player 2#TAG", "Bomb", "Bomb", 0);
    MatchIntelligenceFixtures.addKill(segments, 2, "Player 3#TAG", "Player 3#TAG", "Fall", "Fall", 0);
    var parsed = parse(export).matches().getFirst();
    assertEquals(1, parsed.counts().creditedPlayerKills());
    assertEquals(3, parsed.counts().specialSelfEvents());
    assertEquals(1, parsed.match().events().stream().filter(e -> e.category() == CREDITED_PLAYER_KILL).count());
    assertEquals(1, parsed.match().events().stream().filter(e -> e.category() == ABILITY_RELATED_SPECIAL_EVENT).count());
    assertEquals(2, parsed.match().events().stream().filter(e -> e.category() == SELF_OR_ENVIRONMENTAL_EVENT).count());
  }

  @Test void mismatchedIdentityKdaScoreAndRoundCoverageAreReportedWithoutCorrection() throws Exception {
    ObjectNode identity = MatchIntelligenceFixtures.export(2, 2);
    MatchIntelligenceFixtures.matchEntry(identity).put("matchId", "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");
    assertIssue(parse(identity), "MATCH_ID_MISMATCH");

    ObjectNode kda = MatchIntelligenceFixtures.export(2, 2);
    ObjectNode p0 = summary(MatchIntelligenceFixtures.segments(kda), "Player 0#TAG");
    ((ObjectNode) p0.path("stats").path("kills")).put("value", 2);
    assertIssue(parse(kda), "PARTICIPANT_KDA_MISMATCH");

    ObjectNode score = MatchIntelligenceFixtures.export(2, 2);
    ((ObjectNode) team(MatchIntelligenceFixtures.segments(score), "Blue").path("stats").path("roundsWon")).put("value", 1);
    assertIssue(parse(score), "TEAM_SCORE_MISMATCH");

    ObjectNode missing = MatchIntelligenceFixtures.export(2, 2);
    ArrayNode sourceSegments = MatchIntelligenceFixtures.segments(missing);
    for (int i = sourceSegments.size() - 1; i >= 0; i--) {
      if ("player-round".equals(sourceSegments.get(i).path("type").asText())) { sourceSegments.remove(i); break; }
    }
    var partial = parse(missing).matches().getFirst();
    assertEquals(MatchDataQuality.PARTIAL, partial.quality());
    assertTrue(partial.issues().stream().anyMatch(i -> i.code().equals("PARTICIPANT_ROUND_COVERAGE")));
  }

  @Test void missingStatisticsRemainNullWhileObservedZeroRemainsZero() throws Exception {
    ObjectNode export = MatchIntelligenceFixtures.export(2, 2);
    ObjectNode round = (ObjectNode) typed(MatchIntelligenceFixtures.segments(export), "player-round").get(1);
    ((ObjectNode) round.path("stats")).remove("assists");
    var parsed = parse(export).matches().getFirst();
    assertEquals(MatchDataQuality.PARTIAL, parsed.quality());
    assertNull(parsed.match().playerRounds().get(1).assists());
    assertEquals(0, parsed.match().playerRounds().get(2).assists());
  }

  @Test void classifiesOvertimeFromObservedRoundLimitAndPreservesEventOrderingValues() throws Exception {
    var overtime = parse(MatchIntelligenceFixtures.export(28, 15)).matches().getFirst().match();
    assertEquals(Boolean.TRUE, overtime.overtime());
    assertTrue(overtime.rounds().getLast().overtimeByRoundPosition());
    assertEquals(5001L, overtime.events().stream().filter(e -> e.category() == CREDITED_PLAYER_KILL).findFirst().orElseThrow().matchTimeRaw());
    var kill = overtime.events().stream().filter(e -> e.category() == CREDITED_PLAYER_KILL).findFirst().orElseThrow();
    assertEquals(3_000L, kill.roundTimeRaw());
    assertEquals(5_001L, kill.matchTimeRaw());
  }

  @Test void sortsEventsByObservedMatchTimeAndRejectsNegativeSourceTimes() throws Exception {
    ObjectNode export = MatchIntelligenceFixtures.export(2, 2);
    ArrayNode segments = MatchIntelligenceFixtures.segments(export);
    MatchIntelligenceFixtures.addKill(segments, 2, "Player 1#TAG", "Player 6#TAG", "Rifle", "Weapon", 100);
    ArrayNode killRows = typed(segments, "player-round-kills");
    ((ObjectNode) killRows.get(1).path("metadata")).put("gameTime", 4_000L);
    var parsed = parse(export).matches().getFirst();
    var ordered = parsed.match().events().stream().filter(e -> e.category() == CREDITED_PLAYER_KILL).toList();
    assertEquals(List.of(4_000L, 5_001L), ordered.stream().map(CanonicalMatch.MatchEvent::matchTimeRaw).toList());

    ObjectNode invalidTime = MatchIntelligenceFixtures.export(2, 2);
    ObjectNode kill = (ObjectNode) typed(MatchIntelligenceFixtures.segments(invalidTime), "player-round-kills").get(0);
    ((ObjectNode) kill.path("metadata")).put("roundTime", -1L);
    assertIssue(parse(invalidTime), "EVENT_TIME_INVALID");
  }

  @Test void flagsDuplicateEventsAndMatchesInsteadOfSilentlyDroppingThem() throws Exception {
    ObjectNode duplicateEvent = MatchIntelligenceFixtures.export(2, 2);
    JsonNode original = typed(MatchIntelligenceFixtures.segments(duplicateEvent), "player-round-kills").get(0).deepCopy();
    MatchIntelligenceFixtures.segments(duplicateEvent).add(original);
    var parsed = parse(duplicateEvent).matches().getFirst();
    assertEquals(2, parsed.counts().creditedPlayerKills());
    assertTrue(parsed.issues().stream().anyMatch(i -> i.code().equals("EVENT_DUPLICATE_CANDIDATE")));

    ObjectNode duplicateMatch = MatchIntelligenceFixtures.export(2, 2);
    ObjectNode copy = ((ObjectNode) MatchIntelligenceFixtures.matchEntry(duplicateMatch).deepCopy());
    ((ArrayNode) duplicateMatch.path("matches")).add(copy);
    duplicateMatch.put("requestedMatches", 2).put("responseCount", 2);
    var report = parse(duplicateMatch);
    assertEquals(0, report.acceptedMatches());
    assertEquals(2, report.rejectedMatches());
    assertTrue(report.matches().stream().allMatch(m -> m.issues().stream().anyMatch(i -> i.code().equals("DUPLICATE_MATCH_ID"))));
  }

  @Test void rejectsWrongPlayerSchemaOversizeAndDuplicateJsonKeys() throws Exception {
    ObjectNode wrongPlayer = MatchIntelligenceFixtures.export(2, 2);
    assertThrows(IllegalArgumentException.class, () -> parser.parse(MatchIntelligenceFixtures.JSON.writeValueAsString(wrongPlayer), "Other#TAG"));
    ObjectNode wrongSchema = MatchIntelligenceFixtures.export(2, 2); wrongSchema.put("schemaVersion", 2);
    assertThrows(IllegalArgumentException.class, () -> parse(wrongSchema));
    assertThrows(IllegalArgumentException.class, () -> parser.parse(" ".repeat(MatchExportParser.MAX_EXPORT_BYTES + 1), MatchIntelligenceFixtures.PLAYER));
    String duplicateField = "{\"schemaVersion\":1,\"schemaVersion\":1}";
    assertThrows(IllegalArgumentException.class, () -> parser.parse(duplicateField, MatchIntelligenceFixtures.PLAYER));
  }

  @Test void rejectsAmbiguousSelectedPlayerIdentity() throws Exception {
    ObjectNode export = MatchIntelligenceFixtures.export(2, 2);
    ArrayNode segments = MatchIntelligenceFixtures.segments(export);
    ObjectNode duplicate = ((ObjectNode) summary(segments, "Player 0#TAG").deepCopy());
    ((ObjectNode) duplicate.path("attributes")).put("platformSlug", "another-provider");
    segments.add(duplicate);
    assertIssue(parse(export), "SELECTED_PLAYER_AMBIGUOUS");
  }

  private CanonicalMatch.ExportReport parse(ObjectNode export) throws Exception {
    return parser.parse(MatchIntelligenceFixtures.JSON.writeValueAsString(export), MatchIntelligenceFixtures.PLAYER);
  }
  private static ArrayNode typed(ArrayNode segments, String type) {
    ArrayNode found = MatchIntelligenceFixtures.JSON.createArrayNode();
    segments.forEach(s -> { if (type.equals(s.path("type").asText())) found.add(s); });
    return found;
  }
  private static ObjectNode summary(ArrayNode segments, String player) {
    for (JsonNode segment : segments) if ("player-summary".equals(segment.path("type").asText())
        && player.equals(segment.path("attributes").path("platformUserIdentifier").asText())) return (ObjectNode) segment;
    throw new AssertionError("summary missing");
  }
  private static ObjectNode team(ArrayNode segments, String id) {
    for (JsonNode segment : segments) if ("team-summary".equals(segment.path("type").asText())
        && id.equals(segment.path("attributes").path("teamId").asText())) return (ObjectNode) segment;
    throw new AssertionError("team missing");
  }
  private static void assertIssue(CanonicalMatch.ExportReport report, String code) {
    assertTrue(report.matches().getFirst().quality() == MatchDataQuality.INVALID,
        () -> "Expected invalid data quality, got " + report.matches().getFirst().quality());
    assertTrue(report.matches().getFirst().issues().stream().anyMatch(issue -> issue.code().equals(code)),
        () -> "Expected issue " + code + ": " + report.matches().getFirst().issues());
  }
}
