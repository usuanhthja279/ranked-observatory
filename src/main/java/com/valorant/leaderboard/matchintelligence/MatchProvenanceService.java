package com.valorant.leaderboard.matchintelligence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;

/** Produces metadata for auditing an imported response without logging its contents. */
public class MatchProvenanceService {
  public record Provenance(String provider, String responseSource, Instant capturedAt,
      String payloadSha256, String parserVersion, String captureMethod) { }

  public static final String PARSER_VERSION = "match-normalizer-1";
  private final ObjectMapper mapper;

  public MatchProvenanceService(ObjectMapper mapper) { this.mapper = mapper; }

  public Provenance from(JsonNode export, JsonNode response) {
    String provider = MatchIdentityResolver.text(export.path("source"));
    String responseSource = MatchIdentityResolver.text(response.path("data").path("metadata").path("source"));
    Instant capturedAt = parseInstant(MatchIdentityResolver.text(export.path("exportedAt")));
    return new Provenance(provider, responseSource, capturedAt, sha256(response), PARSER_VERSION,
        "tracker-browser-network-response-export");
  }

  private Instant parseInstant(String value) {
    if (value.isBlank()) return null;
    try { return Instant.parse(value); }
    catch (DateTimeParseException ignored) { return null; }
  }

  private String sha256(JsonNode response) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(response)));
    } catch (NoSuchAlgorithmException | JsonProcessingException error) {
      throw new IllegalStateException("Unable to fingerprint match response.", error);
    }
  }
}
