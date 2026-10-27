package com.valorant.leaderboard.matchintelligence;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Source-normalized, non-persistent match model. Null means unavailable; zero remains observed zero. */
public record CanonicalMatch(
    String provider,
    String providerMatchId,
    String responseSource,
    String modeKey,
    String modeName,
    String queueId,
    String mapId,
    String mapName,
    String patch,
    String actId,
    Instant startedAt,
    Long durationMillis,
    Boolean ranked,
    Boolean overtime,
    boolean complete,
    String selectedPlayerIdentity,
    String selectedPlayerRiotId,
    String selectedPlayerTeamId,
    List<Team> teams,
    List<Participant> participants,
    List<Round> rounds,
    List<PlayerRound> playerRounds,
    List<DamageInteraction> damageInteractions,
    List<MatchEvent> events,
    List<PositionSnapshot> positions,
    MapTransform mapTransform,
    FieldAvailability availability,
    MatchProvenanceService.Provenance provenance,
    String parserVersion,
    MatchDataQuality dataQuality,
    List<MatchValidationIssue> issues) {

  public CanonicalMatch {
    teams = List.copyOf(teams);
    participants = List.copyOf(participants);
    rounds = List.copyOf(rounds);
    playerRounds = List.copyOf(playerRounds);
    damageInteractions = List.copyOf(damageInteractions);
    events = List.copyOf(events);
    positions = List.copyOf(positions);
    issues = List.copyOf(issues);
  }

  public record Team(String teamId, String observedName, Integer roundsWon, Integer roundsLost,
      String observedRank, Boolean hasWon) { }

  public record PlayerTotals(Integer kills, Integer deaths, Integer assists, Integer damage,
      Integer score, Integer acs, Double adr, Double headshotPercent, Double kastPercent,
      Integer firstKills, Integer firstDeaths) { }

  public record Participant(MatchIdentityResolver.Identity identity, String teamId, String agent,
      String observedRank, Double rankRating, PlayerTotals totals, String identityConfidence) { }

  public record Round(int roundNumber, String winningTeamId, String rawWinMethod,
      JsonNode plantEvidence, JsonNode defuseEvidence, Long detonationTimeRaw,
      boolean overtimeByRoundPosition) { }

  public record PlayerRound(MatchIdentityResolver.Identity identity, int roundNumber,
      String teamId, String side, Integer kills, Integer deaths, Integer assists,
      Integer damage, Integer score, Integer loadoutValue, Integer spentCredits,
      Integer remainingCredits) { }

  public record DamageInteraction(String sourceRef, int roundNumber,
      MatchIdentityResolver.Identity attacker, MatchIdentityResolver.Identity victim,
      Integer damage, Integer headshots, Integer bodyshots, Integer legshots) { }

  public enum EventCategory {
    CREDITED_PLAYER_KILL,
    PLAYER_DEATH,
    SELF_OR_ENVIRONMENTAL_EVENT,
    ABILITY_RELATED_SPECIAL_EVENT,
    PLANT,
    DEFUSE,
    DETONATION,
    ROUND_START,
    ROUND_END,
    UNKNOWN_SOURCE_EVENT
  }

  /** Event times retain the raw Tracker number; its unit has not been independently verified. */
  public record MatchEvent(String eventId, String sourceRef, String rawSourceType,
      EventCategory category, int roundNumber, Long roundTimeRaw, Long matchTimeRaw,
      MatchIdentityResolver.Identity actor, MatchIdentityResolver.Identity target,
      List<MatchIdentityResolver.Identity> assistants, String weaponName, String weaponCategory,
      Integer damage, JsonNode sourceEvidence) {
    public MatchEvent { assistants = List.copyOf(assistants); }
  }

  public record PositionSnapshot(String sourceRef, String snapshotType, int roundNumber,
      MatchIdentityResolver.Identity identity, Double x, Double y, Double viewRadians,
      String coordinateSpace) { }

  public record MapTransform(Double xMultiplier, Double yMultiplier,
      Double xScalarToAdd, Double yScalarToAdd) { }

  public record FieldAvailability(boolean participantRounds, boolean damageInteractions,
      boolean killEvents, boolean economy, boolean objectiveEvents,
      boolean eventPositions, boolean roundStartEndEvents, boolean patch) { }

  public record Counts(int rounds, int participants, int playerRoundRows,
      int damageInteractions, int killEventRows, int creditedPlayerKills,
      int specialSelfEvents, int objectiveEvents, int positionSnapshots) { }

  public record ParsedMatch(CanonicalMatch match, MatchDataQuality quality,
      Counts counts, List<MatchValidationIssue> issues) {
    public ParsedMatch { issues = List.copyOf(issues); }
  }

  public record ExportReport(String provider, String selectedPlayer,
      int requestedMatches, int responseCount, boolean sourceMarkedComplete,
      int acceptedMatches, int partialMatches, int rejectedMatches,
      List<ParsedMatch> matches, List<MatchValidationIssue> issues) {
    public ExportReport { matches = List.copyOf(matches); issues = List.copyOf(issues); }
  }
}
