package com.valorant.leaderboard.matchintelligence;

import static com.valorant.leaderboard.matchintelligence.CanonicalMatch.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;

/** Parses the bounded browser collector export. It performs no network, database, or file writes. */
@Service
public class MatchExportParser {
  public static final int SUPPORTED_SCHEMA_VERSION = 1;
  public static final int MAX_MATCHES = 20;
  public static final int MAX_EXPORT_BYTES = 25 * 1024 * 1024;

  private final ObjectMapper mapper;
  private final MatchResponseValidator validator;
  private final MatchNormalizer normalizer;
  private final MatchProvenanceService provenance;

  public MatchExportParser(ObjectMapper mapper) {
    this(mapper, new MatchIdentityResolver());
  }

  MatchExportParser(ObjectMapper mapper, MatchIdentityResolver identities) {
    this.mapper = mapper;
    this.validator = new MatchResponseValidator(identities, new MatchReconciliationService(identities));
    this.normalizer = new MatchNormalizer(identities);
    this.provenance = new MatchProvenanceService(mapper);
  }

  public ExportReport parse(String json, String expectedSelectedPlayer) {
    if (json == null || json.isBlank()) throw new IllegalArgumentException("Match export is empty.");
    if (json.getBytes(StandardCharsets.UTF_8).length > MAX_EXPORT_BYTES) throw new IllegalArgumentException("Match export exceeds the configured size limit.");
    try {
      JsonNode root = mapper.reader().with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).readTree(json);
      return parse(root, expectedSelectedPlayer);
    } catch (JsonProcessingException error) {
      throw new IllegalArgumentException("Match export is not valid JSON.", error);
    }
  }

  public ExportReport parse(JsonNode root, String expectedSelectedPlayer) {
    if (root == null || !root.isObject()) throw new IllegalArgumentException("Match export must be a JSON object.");
    if (expectedSelectedPlayer == null || expectedSelectedPlayer.isBlank() || expectedSelectedPlayer.length() > 300) {
      throw new IllegalArgumentException("An expected selected-player identifier is required.");
    }
    if (integer(root.path("schemaVersion"), -1) != SUPPORTED_SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported Tracker match export schema version.");
    String provider = text(root.path("source"));
    if (!provider.equalsIgnoreCase("tracker.gg")) throw new IllegalArgumentException("Unsupported match export provider.");
    String exportPlayer = text(root.path("player"));
    if (!MatchIdentityResolver.keyPart(exportPlayer).equals(MatchIdentityResolver.keyPart(expectedSelectedPlayer))) {
      throw new IllegalArgumentException("Export player does not match the requested player.");
    }
    JsonNode entries = root.path("matches");
    if (!entries.isArray() || entries.isEmpty() || entries.size() > MAX_MATCHES) throw new IllegalArgumentException("Match export must contain between one and twenty matches.");
    int requested = integer(root.path("requestedMatches"), entries.size());
    if (requested < 1 || requested > MAX_MATCHES) throw new IllegalArgumentException("Requested match count is outside the supported range.");
    List<MatchValidationIssue> exportIssues = new ArrayList<>();
    int declaredResponses = integer(root.path("responseCount"), -1);
    if (declaredResponses != entries.size()) issue(exportIssues, MatchValidationIssue.Severity.WARNING,
        "EXPORT_RESPONSE_COUNT_MISMATCH", "responseCount", "Export response count differs from the number of captured entries.");
    boolean markedComplete = root.path("complete").isBoolean() && root.path("complete").booleanValue();
    if (!markedComplete) issue(exportIssues, MatchValidationIssue.Severity.WARNING,
        "EXPORT_MARKED_INCOMPLETE", "complete", "Collector marked this export incomplete; captured matches are still validated independently.");

    Map<String, Integer> occurrences = new HashMap<>();
    for (JsonNode entry : entries) {
      String id = text(entry.path("matchId")).toLowerCase(Locale.ROOT);
      if (!id.isBlank()) occurrences.merge(id, 1, Integer::sum);
    }
    List<ParsedMatch> results = new ArrayList<>();
    int accepted = 0, partial = 0, rejected = 0;
    for (int i = 0; i < entries.size(); i++) {
      JsonNode entry = entries.get(i);
      String id = text(entry.path("matchId"));
      if (!id.isBlank() && occurrences.getOrDefault(id.toLowerCase(Locale.ROOT), 0) > 1) {
        List<MatchValidationIssue> issues = List.of(new MatchValidationIssue(MatchValidationIssue.Severity.ERROR,
            "DUPLICATE_MATCH_ID", "matches[" + i + "].matchId", "Duplicate provider match identifiers were not silently deduplicated."));
        results.add(new ParsedMatch(null, MatchDataQuality.INVALID, emptyCounts(), issues));
        rejected++;
        continue;
      }
      MatchResponseValidator.Assessment assessment = validator.validate(entry, expectedSelectedPlayer);
      CanonicalMatch match = null;
      if (assessment.data() != null && assessment.data().isObject() && assessment.selectedPlayerSummary() != null
          && assessment.data().path("segments").isArray()) {
        match = normalizer.normalize(assessment, provenance.from(root, assessment.response()));
      }
      MatchDataQuality quality = assessment.quality();
      var r = assessment.reconciliation();
      results.add(new ParsedMatch(match, quality, new Counts(r.rounds(), r.participants(), r.playerRoundRows(),
          r.damageRows(), r.killRows(), r.creditedKills(), r.specialEvents(), r.objectiveEvents(),
          r.positionSnapshots()), assessment.issues()));
      if (quality == MatchDataQuality.INVALID) rejected++;
      else if (quality == MatchDataQuality.PARTIAL) { accepted++; partial++; }
      else accepted++;
    }
    return new ExportReport(provider, exportPlayer, requested, entries.size(), markedComplete,
        accepted, partial, rejected, results, exportIssues);
  }

  private static Counts emptyCounts() { return new Counts(0, 0, 0, 0, 0, 0, 0, 0, 0); }
  private static int integer(JsonNode node, int fallback) { return MatchReconciliationService.integer(node, fallback); }
  private static String text(JsonNode node) { return MatchReconciliationService.text(node); }
  private static void issue(List<MatchValidationIssue> issues, MatchValidationIssue.Severity severity,
      String code, String path, String message) { issues.add(new MatchValidationIssue(severity, code, path, message)); }
}
