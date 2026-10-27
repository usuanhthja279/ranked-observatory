package com.valorant.leaderboard.matchintelligence;

import static com.valorant.leaderboard.matchintelligence.CanonicalMatch.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

/** Converts validated Tracker segments into source-neutral records while preserving unknown evidence. */
public class MatchNormalizer {
  private static final Set<String> KNOWN_TYPES = Set.of("team-summary", "round-summary", "player-round",
      "player-round-damage", "player-summary", "player-loadout", "player-round-kills");
  private final MatchIdentityResolver identities;

  public MatchNormalizer(MatchIdentityResolver identities) { this.identities = identities; }

  public CanonicalMatch normalize(MatchResponseValidator.Assessment assessment,
      MatchProvenanceService.Provenance provenance) {
    JsonNode data = assessment.data(), metadata = data.path("metadata"), segments = data.path("segments");
    List<Team> teams = teams(segments);
    List<Participant> participants = participants(segments);
    List<Round> rounds = rounds(segments, integer(metadata.path("modeMaxRounds"), -1));
    List<PlayerRound> playerRounds = playerRounds(segments);
    List<DamageInteraction> damage = damage(segments);
    List<MatchEvent> events = new ArrayList<>();
    List<PositionSnapshot> positions = new ArrayList<>();
    addKillEvents(assessment.envelopeMatchId(), segments, participants, events, positions);
    addObjectiveEvents(assessment.envelopeMatchId(), segments, participants, events, positions);
    preserveUnknownSegments(assessment.envelopeMatchId(), segments, events);
    events.sort(Comparator.comparing((MatchEvent e) -> e.matchTimeRaw() == null ? Long.MAX_VALUE : e.matchTimeRaw())
        .thenComparing(e -> e.roundTimeRaw() == null ? Long.MAX_VALUE : e.roundTimeRaw())
        .thenComparing(MatchEvent::sourceRef).thenComparing(MatchEvent::eventId));

    JsonNode selectedSummary = assessment.selectedPlayerSummary();
    MatchIdentityResolver.Identity selected = identities.fromAttributes(selectedSummary.path("attributes"));
    String selectedTeam = text(selectedSummary.path("metadata").path("teamId"));
    int expectedRounds = integer(metadata.path("rounds"), 0), modeMaxRounds = integer(metadata.path("modeMaxRounds"), -1);
    Boolean overtime = modeMaxRounds > 0 ? expectedRounds > modeMaxRounds : null;
    boolean complete = assessment.quality() != MatchDataQuality.PARTIAL
        && playerRounds.size() == expectedRounds * participants.size()
        && metadata.path("extendedDataIncluded").asBoolean(false);
    Double xMul = number(metadata.path("mapDetails").path("xMultiplier"));
    Double yMul = number(metadata.path("mapDetails").path("yMultiplier"));
    Double xAdd = number(metadata.path("mapDetails").path("xScalarToAdd"));
    Double yAdd = number(metadata.path("mapDetails").path("yScalarToAdd"));
    MapTransform transform = xMul == null && yMul == null && xAdd == null && yAdd == null
        ? null : new MapTransform(xMul, yMul, xAdd, yAdd);
    FieldAvailability availability = new FieldAvailability(
        !playerRounds.isEmpty(), !damage.isEmpty(), assessment.reconciliation().killRows() > 0,
        playerRounds.stream().anyMatch(p -> p.loadoutValue() != null || p.spentCredits() != null || p.remainingCredits() != null),
        assessment.reconciliation().objectiveEvents() > 0, !positions.isEmpty(), false,
        text(metadata.path("patch")).length() > 0);
    return new CanonicalMatch(
        provenance.provider(), assessment.envelopeMatchId(), provenance.responseSource(),
        text(metadata.path("modeKey")), text(metadata.path("modeName")),
        text(metadata.path("queueId")), text(metadata.path("map")), text(metadata.path("mapName")),
        text(metadata.path("patch")), text(metadata.path("seasonId")), instant(text(metadata.path("dateStarted"))),
        longNumber(metadata.path("duration")), bool(metadata.path("isRanked")), overtime, complete,
        selected.stableKey(), selected.observedRiotId(), selectedTeam, teams, participants, rounds,
        playerRounds, damage, events, positions, transform, availability, provenance,
        MatchProvenanceService.PARSER_VERSION, assessment.quality(), assessment.issues());
  }

  private List<Team> teams(JsonNode segments) {
    return MatchReconciliationService.byType(segments, "team-summary").stream().map(row ->
        new Team(text(row.path("attributes").path("teamId")), text(row.path("metadata").path("name")),
            stat(row, "roundsWon"), stat(row, "roundsLost"), text(row.path("metadata").path("rank")),
            bool(row.path("metadata").path("hasWon")))).toList();
  }

  private List<Participant> participants(JsonNode segments) {
    return MatchReconciliationService.byType(segments, "player-summary").stream().map(row -> {
      JsonNode meta = row.path("metadata");
      String rank = text(row.path("stats").path("rank").path("value"));
      Double rating = number(row.path("stats").path("currRank").path("value"));
      PlayerTotals totals = new PlayerTotals(stat(row, "kills"), stat(row, "deaths"), stat(row, "assists"),
          stat(row, "damage"), stat(row, "score"), stat(row, "scorePerRound"), number(row.path("stats").path("damagePerRound").path("value")),
          number(row.path("stats").path("hsAccuracy").path("value")), number(row.path("stats").path("kast").path("value")),
          stat(row, "firstKills"), stat(row, "firstDeaths"));
      MatchIdentityResolver.Identity identity = identities.fromAttributes(row.path("attributes"));
      return new Participant(identity, text(meta.path("teamId")), text(meta.path("agentName")), rank,
          rating, totals, identity.hasProviderIdentity() ? "PROVIDER_SCOPED" : "DISPLAY_ONLY");
    }).toList();
  }

  private List<Round> rounds(JsonNode segments, int regulationRounds) {
    return MatchReconciliationService.byType(segments, "round-summary").stream()
        .sorted(Comparator.comparingInt(r -> integer(r.path("attributes").path("round"), 0)))
        .map(row -> {
          JsonNode meta = row.path("metadata"), exploded = meta.path("exploded");
          Long detonation = exploded.isNumber() ? exploded.longValue() : null;
          return new Round(integer(row.path("attributes").path("round"), 0),
              text(row.path("stats").path("winningTeam").path("value")),
              text(row.path("stats").path("roundResult").path("value")),
              copyOrNull(meta.path("plant")), copyOrNull(meta.path("defuse")), detonation,
              regulationRounds > 0 && integer(row.path("attributes").path("round"), 0) > regulationRounds);
        }).toList();
  }

  private List<PlayerRound> playerRounds(JsonNode segments) {
    return MatchReconciliationService.byType(segments, "player-round").stream()
        .sorted(Comparator.comparingInt((JsonNode r) -> integer(r.path("attributes").path("round"), 0))
            .thenComparing(r -> identities.fromAttributes(r.path("attributes")).stableKey()))
        .map(row -> {
          JsonNode meta = row.path("metadata");
          return new PlayerRound(identities.fromAttributes(row.path("attributes")), integer(row.path("attributes").path("round"), 0),
              text(meta.path("teamId")), text(meta.path("teamSide")), stat(row, "kills"), stat(row, "deaths"),
              stat(row, "assists"), stat(row, "damage"), stat(row, "score"), stat(row, "loadoutValue"),
              stat(row, "spentCredits"), stat(row, "remainingCredits"));
        }).toList();
  }

  private List<DamageInteraction> damage(JsonNode segments) {
    List<DamageInteraction> result = new ArrayList<>();
    List<JsonNode> rows = MatchReconciliationService.byType(segments, "player-round-damage");
    for (int i = 0; i < rows.size(); i++) {
      JsonNode row = rows.get(i), attrs = row.path("attributes");
      result.add(new DamageInteraction("data.segments[player-round-damage][" + i + "]",
          integer(attrs.path("round"), 0), identities.fromAttributes(attrs), opponent(attrs), stat(row, "damage"),
          stat(row, "headshots"), stat(row, "bodyshots"), stat(row, "legshots")));
    }
    return result;
  }

  private void addKillEvents(String matchId, JsonNode segments, List<Participant> participants,
      List<MatchEvent> events, List<PositionSnapshot> positions) {
    List<JsonNode> rows = MatchReconciliationService.byType(segments, "player-round-kills");
    for (int i = 0; i < rows.size(); i++) {
      JsonNode row = rows.get(i), attrs = row.path("attributes"), meta = row.path("metadata");
      String sourceRef = "data.segments[player-round-kills][" + i + "]";
      MatchIdentityResolver.Identity actor = identities.fromAttributes(attrs), target = opponent(attrs);
      boolean self = actor.hasProviderIdentity() && actor.stableKey().equals(target.stableKey());
      String damageType = text(meta.path("finishingDamage").path("damageType"));
      String weaponCategory = text(meta.path("weaponCategory"));
      EventCategory category = self ? specialCategory(damageType, weaponCategory)
          : actor.hasProviderIdentity() && target.hasProviderIdentity() ? EventCategory.CREDITED_PLAYER_KILL : EventCategory.UNKNOWN_SOURCE_EVENT;
      List<MatchIdentityResolver.Identity> assistants = identities(meta.path("assistants"));
      MatchEvent event = event(matchId + ":segment:" + i + ":source", sourceRef, "player-round-kills", category,
          integer(attrs.path("round"), 0), longNumber(meta.path("roundTime")), longNumber(meta.path("gameTime")),
          actor, target, assistants, text(meta.path("weaponName")), weaponCategory, stat(row, "damage"),
          category == EventCategory.UNKNOWN_SOURCE_EVENT ? row : null);
      events.add(event);
      if (category == EventCategory.CREDITED_PLAYER_KILL) {
        events.add(event(matchId + ":segment:" + i + ":death", sourceRef, "player-round-kills",
            EventCategory.PLAYER_DEATH, event.roundNumber(), event.roundTimeRaw(), event.matchTimeRaw(),
            target, null, assistants, event.weaponName(), weaponCategory, event.damage(), null));
      }
      addPlayerPositions(sourceRef, event.roundNumber(), meta.path("playerLocations"), participants, positions);
      addPosition(sourceRef, "OPPONENT_AT_EVENT", event.roundNumber(), target, meta.path("opponentLocation"), null, positions);
    }
  }

  private void addObjectiveEvents(String matchId, JsonNode segments, List<Participant> participants,
      List<MatchEvent> events, List<PositionSnapshot> positions) {
    List<JsonNode> rows = MatchReconciliationService.byType(segments, "round-summary");
    for (int i = 0; i < rows.size(); i++) {
      JsonNode row = rows.get(i), meta = row.path("metadata");
      int round = integer(row.path("attributes").path("round"), 0);
      addObjective(matchId, "plant", "PLANT", "plant", row, meta, i, round, participants, events, positions);
      addObjective(matchId, "defuse", "DEFUSE", "defuse", row, meta, i, round, participants, events, positions);
      JsonNode exploded = meta.path("exploded");
      if (exploded.isNumber() || (exploded.isBoolean() && exploded.booleanValue())) {
        Long rawTime = exploded.isNumber() ? exploded.longValue() : null;
        events.add(event(matchId + ":round:" + round + ":detonation", "data.segments[round-summary][" + i + "].metadata.exploded",
            "round-summary", EventCategory.DETONATION, round, rawTime, null, null, null, List.of(), "", "", null,
            copyOrNull(exploded)));
      }
    }
  }

  private void addObjective(String matchId, String field, String eventIdSuffix, String sourceSuffix,
      JsonNode row, JsonNode meta, int index, int round, List<Participant> participants,
      List<MatchEvent> events, List<PositionSnapshot> positions) {
    JsonNode value = meta.path(field);
    if (!value.isObject()) return;
      MatchIdentityResolver.Identity actor = identityForIdentifier(text(value.path("platformUserIdentifier")), participants);
    JsonNode location = value.path("location");
    Long time = longNumber(value.path("roundTime"));
    events.add(event(matchId + ":round:" + round + ":" + eventIdSuffix,
        "data.segments[round-summary][" + index + "].metadata." + sourceSuffix, "round-summary",
        field.equals("plant") ? EventCategory.PLANT : EventCategory.DEFUSE, round, time, null,
        actor, null, List.of(), "", "", null, copyOrNull(value)));
    addPosition("data.segments[round-summary][" + index + "].metadata." + field,
        field.equals("plant") ? "PLANT_SITE_EVENT" : "DEFUSE_SITE_EVENT", round, actor, location, null, positions);
  }

  private void preserveUnknownSegments(String matchId, JsonNode segments, List<MatchEvent> events) {
    if (!segments.isArray()) return;
    for (int i = 0; i < segments.size(); i++) {
      JsonNode segment = segments.get(i);
      String type = text(segment.path("type"));
      if (KNOWN_TYPES.contains(type)) continue;
      events.add(event(matchId + ":segment:" + i + ":unknown", "data.segments[" + i + "]", type,
          EventCategory.UNKNOWN_SOURCE_EVENT, integer(segment.path("attributes").path("round"), 0),
          longNumber(segment.path("metadata").path("roundTime")), longNumber(segment.path("metadata").path("gameTime")),
          null, null, List.of(), "", "", null, segment.deepCopy()));
    }
  }

  private void addPlayerPositions(String sourceRef, int round, JsonNode list, List<Participant> participants,
      List<PositionSnapshot> positions) {
    if (!list.isArray()) return;
    for (JsonNode row : list) {
      String userId = text(row.path("platformUserIdentifier"));
      MatchIdentityResolver.Identity id = identityForIdentifier(userId, participants);
      addPosition(sourceRef, "PLAYER_AT_EVENT", round, id, row.path("location"), number(row.path("viewRadians")), positions);
    }
  }

  private void addPosition(String sourceRef, String type, int round, MatchIdentityResolver.Identity id, JsonNode location,
      Double viewRadians, List<PositionSnapshot> positions) {
    if (!location.isObject()) return;
    Double x = number(location.path("x")), y = number(location.path("y"));
    if (x == null || y == null) return;
    positions.add(new PositionSnapshot(sourceRef, type, round, id, x, y,
        viewRadians == null ? number(location.path("viewRadians")) : viewRadians, "TRACKER_WORLD_RAW"));
  }

  private MatchIdentityResolver.Identity identityForIdentifier(String providerUserId, List<Participant> participants) {
    if (providerUserId.isBlank()) return null;
    List<MatchIdentityResolver.Identity> found = participants.stream().map(Participant::identity)
        .filter(i -> MatchIdentityResolver.keyPart(i.providerUserId()).equals(MatchIdentityResolver.keyPart(providerUserId)))
        .toList();
    return found.size() == 1 ? found.getFirst() : null;
  }

  private static MatchIdentityResolver.Identity opponent(JsonNode attributes) {
    return new MatchIdentityResolver.Identity(text(attributes.path("opponentPlatformSlug")),
        text(attributes.path("opponentPlatformUserIdentifier")),
        text(attributes.path("opponentPlatformUserIdentifier")));
  }

  private static List<MatchIdentityResolver.Identity> identities(JsonNode values) {
    if (!values.isArray()) return List.of();
    List<MatchIdentityResolver.Identity> result = new ArrayList<>();
    for (JsonNode row : values) result.add(new MatchIdentityResolver.Identity(text(row.path("platformSlug")),
        text(row.path("platformUserIdentifier")), text(row.path("platformUserHandle"))));
    return result;
  }

  private static EventCategory specialCategory(String damageType, String weaponCategory) {
    String type = damageType.toLowerCase(Locale.ROOT), category = weaponCategory.toLowerCase(Locale.ROOT);
    if (type.equals("ability") || category.equals("ability")) return EventCategory.ABILITY_RELATED_SPECIAL_EVENT;
    if (Set.of("bomb", "fall", "environment", "self").contains(type)
        || Set.of("bomb", "fall", "environment", "self").contains(category)) return EventCategory.SELF_OR_ENVIRONMENTAL_EVENT;
    return EventCategory.UNKNOWN_SOURCE_EVENT;
  }

  private static MatchEvent event(String id, String sourceRef, String sourceType, EventCategory category,
      int round, Long roundTime, Long gameTime, MatchIdentityResolver.Identity actor, MatchIdentityResolver.Identity target,
      List<MatchIdentityResolver.Identity> assistants,
      String weapon, String weaponCategory, Integer damage, JsonNode evidence) {
    return new MatchEvent(id, sourceRef, sourceType, category, round, roundTime, gameTime,
        actor, target, assistants, weapon, weaponCategory, damage, evidence);
  }

  private static Integer stat(JsonNode row, String name) { return MatchReconciliationService.statInteger(row, name); }
  private static String text(JsonNode node) { return MatchReconciliationService.text(node); }
  private static int integer(JsonNode node, int fallback) { return MatchReconciliationService.integer(node, fallback); }
  private static Long longNumber(JsonNode node) { return node != null && node.isNumber() ? node.longValue() : null; }
  private static Double number(JsonNode node) { return node != null && node.isNumber() && Double.isFinite(node.doubleValue()) ? node.doubleValue() : null; }
  private static Boolean bool(JsonNode node) { return node != null && node.isBoolean() ? node.booleanValue() : null; }
  private static JsonNode copyOrNull(JsonNode node) { return node == null || node.isMissingNode() || node.isNull() ? null : node.deepCopy(); }
  private static Instant instant(String raw) {
    if (raw.isBlank()) return null;
    try { return Instant.parse(raw); } catch (DateTimeParseException ignored) { return null; }
  }
}
