package com.valorant.leaderboard.matchintelligence;

import com.fasterxml.jackson.databind.JsonNode;
import java.text.Normalizer;
import java.util.*;

/** Resolves identities using Tracker's provider-scoped identifier, never a display name alone. */
public class MatchIdentityResolver {
  public record Identity(String provider, String providerUserId, String observedRiotId) {
    public Identity {
      provider = clean(provider);
      providerUserId = clean(providerUserId);
      observedRiotId = clean(observedRiotId);
    }
    public String stableKey() { return keyPart(provider) + ":" + keyPart(providerUserId); }
    public boolean hasProviderIdentity() { return !provider.isEmpty() && !providerUserId.isEmpty(); }
  }

  public Identity fromAttributes(JsonNode attributes) {
    if (attributes == null || !attributes.isObject()) return new Identity("", "", "");
    return new Identity(text(attributes.path("platformSlug")),
        text(attributes.path("platformUserIdentifier")),
        text(attributes.path("platformUserIdentifier")));
  }

  public Optional<JsonNode> resolveSummary(List<JsonNode> summaries, String expectedRiotId) {
    String expected = keyPart(expectedRiotId);
    List<JsonNode> found = summaries.stream()
        .filter(s -> keyPart(text(s.path("attributes").path("platformUserIdentifier"))).equals(expected))
        .toList();
    if (found.size() > 1) throw new IllegalArgumentException("Selected player identity is ambiguous.");
    return found.stream().findFirst();
  }

  public static String keyPart(String value) {
    return Normalizer.normalize(clean(value), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
  }

  public static String text(JsonNode node) {
    return node != null && node.isTextual() ? node.textValue().trim() : "";
  }

  private static String clean(String value) { return value == null ? "" : value.trim(); }
}
