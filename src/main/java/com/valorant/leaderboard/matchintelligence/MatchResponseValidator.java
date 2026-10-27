package com.valorant.leaderboard.matchintelligence;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Validates the observed Tracker response shape and reconciles authoritative source totals. */
public class MatchResponseValidator {
  public record Assessment(JsonNode response, JsonNode data, JsonNode selectedPlayerSummary,
      String envelopeMatchId, MatchDataQuality quality,
      MatchReconciliationService.Result reconciliation, List<MatchValidationIssue> issues) {
    public Assessment { issues = List.copyOf(issues); }
  }

  private static final int MAX_SEGMENTS = 20_000;
  private final MatchIdentityResolver identities;
  private final MatchReconciliationService reconciliation;

  public MatchResponseValidator(MatchIdentityResolver identities, MatchReconciliationService reconciliation) {
    this.identities = identities;
    this.reconciliation = reconciliation;
  }

  public Assessment validate(JsonNode envelope, String expectedSelectedPlayer) {
    List<MatchValidationIssue> issues = new ArrayList<>();
    if (expectedSelectedPlayer == null || expectedSelectedPlayer.isBlank() || expectedSelectedPlayer.length() > 300) {
      issue(issues, MatchValidationIssue.Severity.ERROR, "SELECTED_PLAYER_INVALID", "selectedPlayer", "An expected selected-player identifier is required.");
    }
    if (envelope == null || !envelope.isObject()) {
      issue(issues, MatchValidationIssue.Severity.ERROR, "MATCH_ENVELOPE_INVALID", "match", "Match entry must be an object.");
      return new Assessment(null, null, null, "", MatchDataQuality.INVALID, emptyReconciliation(), issues);
    }
    String envelopeId = MatchReconciliationService.text(envelope.path("matchId"));
    JsonNode response = envelope.path("response");
    JsonNode data = response.path("data");
    if (envelopeId.isBlank() || envelopeId.length() > 100) {
      issue(issues, MatchValidationIssue.Severity.ERROR, "MATCH_ID_MISSING", "match.matchId", "Provider match identifier is missing or too long.");
    }
    if (!response.isObject() || !data.isObject()) {
      issue(issues, MatchValidationIssue.Severity.ERROR, "RESPONSE_DATA_MISSING", "match.response.data", "Tracker response data is missing.");
      return new Assessment(response, data, null, envelopeId, MatchDataQuality.INVALID, emptyReconciliation(), issues);
    }
    String responseId = MatchReconciliationService.text(data.path("attributes").path("id"));
    if (responseId.isBlank() || !responseId.equalsIgnoreCase(envelopeId)) {
      issue(issues, MatchValidationIssue.Severity.ERROR, "MATCH_ID_MISMATCH", "match.response.data.attributes.id", "Envelope and response match identifiers do not agree.");
    }
    JsonNode segments = data.path("segments");
    if (!segments.isArray() || segments.size() > MAX_SEGMENTS) {
      issue(issues, MatchValidationIssue.Severity.ERROR, "SEGMENTS_INVALID", "match.response.data.segments", "Segments must be an array within the configured bound.");
      return new Assessment(response, data, null, envelopeId, MatchDataQuality.INVALID, emptyReconciliation(), issues);
    }
    List<JsonNode> summaries = MatchReconciliationService.byType(segments, "player-summary");
    JsonNode selected = null;
    try {
      selected = identities.resolveSummary(summaries, expectedSelectedPlayer).orElse(null);
    } catch (IllegalArgumentException ambiguous) {
      issue(issues, MatchValidationIssue.Severity.ERROR, "SELECTED_PLAYER_AMBIGUOUS", "match.response.data.segments[player-summary]", ambiguous.getMessage());
    }
    if (selected == null && issues.stream().noneMatch(i -> i.code().equals("SELECTED_PLAYER_AMBIGUOUS"))) {
      issue(issues, MatchValidationIssue.Severity.ERROR, "SELECTED_PLAYER_NOT_FOUND", "match.response.data.segments[player-summary]", "The expected player is not uniquely present in the match.");
    }
    MatchReconciliationService.Result checks = reconciliation.inspect(data);
    issues.addAll(checks.issues());
    JsonNode metadata = data.path("metadata");
    if (!metadata.isObject()) issue(issues, MatchValidationIssue.Severity.ERROR, "MATCH_METADATA_MISSING", "match.response.data.metadata", "Match metadata is missing.");
    String mapName = MatchReconciliationService.text(metadata.path("mapName"));
    if (mapName.isBlank()) issue(issues, MatchValidationIssue.Severity.WARNING, "MAP_UNAVAILABLE", "match.response.data.metadata.mapName", "Map name is unavailable.");
    if (checks.killRows() == 0 || checks.damageRows() == 0) {
      issue(issues, MatchValidationIssue.Severity.WARNING, "EXTENDED_EVIDENCE_UNAVAILABLE", "match.response.data.segments", "Some extended event or damage evidence is unavailable.");
    }
    boolean invalid = issues.stream().anyMatch(i -> i.severity() == MatchValidationIssue.Severity.ERROR);
    boolean partial = checks.partial() || issues.stream().anyMatch(i -> i.code().contains("UNAVAILABLE"));
    boolean warning = issues.stream().anyMatch(i -> i.severity() == MatchValidationIssue.Severity.WARNING);
    MatchDataQuality quality = invalid ? MatchDataQuality.INVALID : partial ? MatchDataQuality.PARTIAL
        : warning ? MatchDataQuality.VALID_WITH_WARNINGS : MatchDataQuality.VALID;
    return new Assessment(response, data, selected, envelopeId, quality, checks, issues);
  }

  private static MatchReconciliationService.Result emptyReconciliation() {
    return new MatchReconciliationService.Result(List.of(), true, 0, 0, 0, 0, 0, 0, 0, 0, 0);
  }

  private static void issue(List<MatchValidationIssue> issues, MatchValidationIssue.Severity severity,
      String code, String path, String message) {
    issues.add(new MatchValidationIssue(severity, code, path, message));
  }
}
