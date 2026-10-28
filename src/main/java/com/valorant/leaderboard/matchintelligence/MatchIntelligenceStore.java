package com.valorant.leaderboard.matchintelligence;

import static com.valorant.leaderboard.matchintelligence.CanonicalMatch.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.sql.Savepoint;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Owner-scoped persistence for bounded imports. Full source exports are private and never read by API methods. */
@Service
public class MatchIntelligenceStore {
  private static final String PROVIDER = "tracker.gg";
  private final JdbcTemplate db;
  private final ObjectMapper mapper;
  private final MatchExportParser parser;
  private final TransactionTemplate tx;
  private final int rawRetentionDays;

  public MatchIntelligenceStore(JdbcTemplate db, ObjectMapper mapper, MatchExportParser parser,
      PlatformTransactionManager transactionManager,
      @Value("${match-intelligence.raw-retention-days:90}") int rawRetentionDays) {
    this.db = db; this.mapper = mapper; this.parser = parser;
    this.tx = new TransactionTemplate(transactionManager);
    this.rawRetentionDays = Math.max(1, Math.min(rawRetentionDays, 365));
  }

  public record ImportReceipt(String jobId, String status, int responseCount,
      int acceptedMatches, int partialMatches, int rejectedMatches,
      List<MatchValidationIssue> issues) { }
  public record MatchSummary(String providerMatchId, String quality, String selectedPlayer,
      String selectedTeam, JsonNode summary) { }
  public record MatchPage(int page, int size, long total, List<MatchSummary> matches) { }
  public record PlayerPerspective(String identity, String riotId, String teamId) { }
  public record MatchView(CanonicalMatch match, PlayerPerspective perspective,
      MatchProvenanceService.Provenance provenance, String quality, List<MatchValidationIssue> issues) {
    /** Rehydrate the owner-specific perspective only for calculations that need a selected player. */
    public CanonicalMatch withSelectedPerspective() {
      return new CanonicalMatch(match.provider(), match.providerMatchId(), match.responseSource(),
          match.modeKey(), match.modeName(), match.queueId(), match.mapId(), match.mapName(), match.patch(),
          match.actId(), match.startedAt(), match.durationMillis(), match.ranked(), match.overtime(),
          match.complete(), perspective.identity(), perspective.riotId(), perspective.teamId(),
          match.teams(), match.participants(), match.rounds(), match.playerRounds(), match.damageInteractions(),
          match.events(), match.positions(), match.mapTransform(), match.availability(), match.provenance(),
          match.parserVersion(), match.dataQuality(), match.issues());
    }
  }
  public record RoundView(String providerMatchId, int roundNumber, PlayerPerspective perspective,
      Round round, List<PlayerRound> playerRounds, List<DamageInteraction> damageInteractions,
      List<MatchEvent> events, List<PositionSnapshot> positions) { }
  public record JobView(String jobId, String status, int requestedMatches, int responseCount,
      int acceptedMatches, int partialMatches, int rejectedMatches, String parserVersion,
      Instant createdAt, List<JobMatchView> matches) { }
  public record JobMatchView(int entryNumber, String providerMatchId, String quality,
      String sourceResponseSha256, Instant sourceCapturedAt, List<MatchValidationIssue> issues) { }
  private record MatchDbRow(String providerMatchId, String quality, String selectedIdentity,
      String selectedRiotId, String selectedTeamId, String summaryJson) { }
  private record OwnedMatchDbRow(String quality, String selectedIdentity, String selectedRiotId,
      String selectedTeamId, String sourceResponseSha256, Timestamp sourceCapturedAt,
      String issuesJson, String canonicalJson) { }

  public ImportReceipt importExport(String owner, byte[] payload) {
    if (owner == null || owner.isBlank() || owner.length() > 100) throw new IllegalArgumentException("Authenticated account identity is invalid.");
    if (payload == null || payload.length == 0) throw new IllegalArgumentException("Match export is empty.");
    if (payload.length > MatchExportParser.MAX_EXPORT_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Match export exceeds the configured size limit.");
    final String json = new String(payload, StandardCharsets.UTF_8);
    final JsonNode root;
    try { root = mapper.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).readTree(json); }
    catch (IOException error) { throw new IllegalArgumentException("Match export is not valid UTF-8 JSON."); }
    String selectedPlayer = root == null ? "" : root.path("player").asText("");
    ExportReport report = parser.parse(root, selectedPlayer);
    String jobId = UUID.randomUUID().toString();
    String exportHash = sha256(payload);
    String jobStatus = report.rejectedMatches() == 0 && report.partialMatches() == 0 ? "COMPLETE"
        : report.acceptedMatches() == 0 ? "REJECTED" : "PARTIAL";
    tx.executeWithoutResult(status -> {
      db.update("delete from mi_raw_imports where purge_after < current_timestamp");
      db.update("insert into mi_import_jobs(job_id,owner_username,provider,status,requested_matches,response_count,accepted_matches,partial_matches,rejected_matches,export_sha256,parser_version) values(?,?,?,?,?,?,?,?,?,?,?)",
          jobId, owner, report.provider(), jobStatus, report.requestedMatches(), report.responseCount(),
          report.acceptedMatches() - report.partialMatches(), report.partialMatches(), report.rejectedMatches(),
          exportHash, MatchProvenanceService.PARSER_VERSION);
      db.update("insert into mi_raw_imports(job_id,gzip_payload,original_bytes,payload_sha256,purge_after) values(?,?,?,?,?)",
          jobId, gzip(payload), payload.length, exportHash, Timestamp.from(Instant.now().plus(rawRetentionDays, ChronoUnit.DAYS)));
      for (int i = 0; i < report.matches().size(); i++) {
        ParsedMatch parsed = report.matches().get(i);
        JsonNode entry = root.path("matches").get(i);
        CanonicalMatch match = parsed.match();
        String providerMatchId = match == null ? entry.path("matchId").asText(null) : match.providerMatchId();
        Long versionId = null;
        String identity = null, riotId = null, teamId = null, sourceHash = null;
        Instant capturedAt = null;
        if (match != null && parsed.quality() != MatchDataQuality.INVALID) {
          identity = match.selectedPlayerIdentity(); riotId = match.selectedPlayerRiotId(); teamId = match.selectedPlayerTeamId();
          sourceHash = match.provenance().payloadSha256(); capturedAt = match.provenance().capturedAt();
          versionId = saveCanonicalVersion(match, parsed.quality());
        }
        db.update("insert into mi_import_job_matches(job_id,entry_number,provider,provider_match_id,version_id,selected_player_identity,selected_player_riot_id,selected_player_team_id,source_response_sha256,source_captured_at,quality,issues_json) values(?,?,?,?,?,?,?,?,?,?,?,?)",
            jobId, i + 1, report.provider(), providerMatchId, versionId, identity, riotId, teamId,
            sourceHash, capturedAt == null ? null : Timestamp.from(capturedAt), parsed.quality().name(), write(parsed.issues()));
      }
    });
    return new ImportReceipt(jobId, jobStatus, report.responseCount(), report.acceptedMatches() - report.partialMatches(),
        report.partialMatches(), report.rejectedMatches(), report.issues());
  }

  private Long saveCanonicalVersion(CanonicalMatch match, MatchDataQuality quality) {
    insertIgnoringDuplicate("insert into mi_matches(provider,provider_match_id) values(?,?)", match.provider(), match.providerMatchId());
    try {
      ObjectNode canonical = mapper.valueToTree(match);
      canonical.putNull("selectedPlayerIdentity"); canonical.putNull("selectedPlayerRiotId"); canonical.putNull("selectedPlayerTeamId");
      ObjectNode provenance = (ObjectNode) canonical.path("provenance");
      provenance.putNull("capturedAt"); provenance.putNull("payloadSha256");
      String canonicalJson = write(canonical);
      String canonicalHash = sha256(canonicalJson.getBytes(StandardCharsets.UTF_8));
      String summaryJson = write(summary(match));
      Long existing = db.query("select version_id from mi_match_versions where provider=? and provider_match_id=? and canonical_sha256=?",
          rs -> rs.next() ? rs.getLong(1) : null, match.provider(), match.providerMatchId(), canonicalHash);
      if (existing != null) return existing;
      Instant start = match.startedAt();
      int inserted = insertIgnoringDuplicate("insert into mi_match_versions(provider,provider_match_id,canonical_sha256,source_response_sha256,parser_version,quality,source_started_at,duration_millis,map_name,mode_name,act_id,summary_json,canonical_json) values(?,?,?,?,?,?,?,?,?,?,?,?,?)",
          match.provider(), match.providerMatchId(), canonicalHash, match.provenance().payloadSha256(),
          match.parserVersion(), quality.name(), start == null ? null : Timestamp.from(start), match.durationMillis(),
          match.mapName(), match.modeName(), match.actId(), summaryJson, canonicalJson);
      if (inserted == 0) return db.queryForObject("select version_id from mi_match_versions where provider=? and provider_match_id=? and canonical_sha256=?",
          Long.class, match.provider(), match.providerMatchId(), canonicalHash);
      Long createdVersion = db.queryForObject("select version_id from mi_match_versions where provider=? and provider_match_id=? and canonical_sha256=?",
          Long.class, match.provider(), match.providerMatchId(), canonicalHash);
      persistCanonicalRows(createdVersion, match);
      return createdVersion;
    } catch (DuplicateKeyException concurrentDuplicate) {
      return db.queryForObject("select version_id from mi_match_versions where provider=? and provider_match_id=? and canonical_sha256=?",
          Long.class, match.provider(), match.providerMatchId(), sha256(write(writeShared(match)).getBytes(StandardCharsets.UTF_8)));
    }
  }

  /** Rolls back a duplicate-key statement to a savepoint so PostgreSQL can continue the enclosing import transaction. */
  private int insertIgnoringDuplicate(String sql, Object... args) {
    Savepoint savepoint = db.execute((ConnectionCallback<Savepoint>) connection -> connection.setSavepoint());
    try {
      int inserted = db.update(sql, args);
      db.execute((ConnectionCallback<Void>) connection -> { connection.releaseSavepoint(savepoint); return null; });
      return inserted;
    } catch (DuplicateKeyException duplicate) {
      db.execute((ConnectionCallback<Void>) connection -> { connection.rollback(savepoint); connection.releaseSavepoint(savepoint); return null; });
      return 0;
    }
  }

  private ObjectNode writeShared(CanonicalMatch match) {
    ObjectNode canonical = mapper.valueToTree(match);
    canonical.putNull("selectedPlayerIdentity"); canonical.putNull("selectedPlayerRiotId"); canonical.putNull("selectedPlayerTeamId");
    ObjectNode provenance = (ObjectNode) canonical.path("provenance"); provenance.putNull("capturedAt"); provenance.putNull("payloadSha256");
    return canonical;
  }

  private ObjectNode summary(CanonicalMatch match) {
    ObjectNode summary = mapper.createObjectNode();
    summary.put("mode", Objects.toString(match.modeName(), "")); summary.put("map", Objects.toString(match.mapName(), ""));
    summary.put("startedAt", match.startedAt() == null ? null : match.startedAt().toString());
    if (match.durationMillis() != null) summary.put("durationMillis", match.durationMillis()); else summary.putNull("durationMillis");
    summary.put("actId", Objects.toString(match.actId(), ""));
    ObjectNode scores = summary.putObject("teamScores");
    for (Team team : match.teams()) if (team.teamId() != null && team.roundsWon() != null) scores.put(team.teamId(), team.roundsWon());
    if (match.overtime() == null) summary.putNull("overtime"); else summary.put("overtime", match.overtime());
    return summary;
  }

  private void persistCanonicalRows(long versionId, CanonicalMatch match) {
    db.batchUpdate("insert into mi_match_teams(version_id,team_id,observed_name,rounds_won,rounds_lost,observed_rank,has_won) values(?,?,?,?,?,?,?)",
        match.teams(), 100, (ps, row) -> {
          ps.setLong(1, versionId); ps.setString(2, Objects.toString(row.teamId(), "unknown"));
          ps.setString(3, row.observedName()); ps.setObject(4, row.roundsWon()); ps.setObject(5, row.roundsLost());
          ps.setString(6, row.observedRank()); ps.setObject(7, row.hasWon());
        });
    db.batchUpdate("insert into mi_match_participants(version_id,participant_key,identity_provider,provider_user_id,observed_riot_id,team_id,agent,identity_confidence,totals_json) values(?,?,?,?,?,?,?,?,?)",
        match.participants(), 200, (ps, row) -> {
          var id = row.identity(); ps.setLong(1, versionId); ps.setString(2, id.stableKey());
          ps.setString(3, id.provider()); ps.setString(4, id.providerUserId()); ps.setString(5, id.observedRiotId());
          ps.setString(6, row.teamId()); ps.setString(7, row.agent()); ps.setString(8, row.identityConfidence()); ps.setString(9, write(row.totals()));
        });
    db.batchUpdate("insert into mi_match_rounds(version_id,round_number,winning_team_id,raw_win_method,plant_evidence_json,defuse_evidence_json,detonation_time_raw,overtime_by_position) values(?,?,?,?,?,?,?,?)",
        match.rounds(), 200, (ps, row) -> {
          ps.setLong(1, versionId); ps.setInt(2, row.roundNumber()); ps.setString(3, row.winningTeamId());
          ps.setString(4, row.rawWinMethod()); ps.setString(5, jsonText(row.plantEvidence())); ps.setString(6, jsonText(row.defuseEvidence()));
          ps.setObject(7, row.detonationTimeRaw()); ps.setBoolean(8, row.overtimeByRoundPosition());
        });
    db.batchUpdate("insert into mi_player_round_stats(version_id,round_number,participant_key,team_id,side,kills,deaths,assists,damage,score,loadout_value,spent_credits,remaining_credits) values(?,?,?,?,?,?,?,?,?,?,?,?,?)",
        match.playerRounds(), 500, (ps, row) -> {
          ps.setLong(1, versionId); ps.setInt(2, row.roundNumber()); ps.setString(3, row.identity().stableKey());
          ps.setString(4, row.teamId()); ps.setString(5, row.side()); ps.setObject(6, row.kills()); ps.setObject(7, row.deaths());
          ps.setObject(8, row.assists()); ps.setObject(9, row.damage()); ps.setObject(10, row.score());
          ps.setObject(11, row.loadoutValue()); ps.setObject(12, row.spentCredits()); ps.setObject(13, row.remainingCredits());
        });
    db.batchUpdate("insert into mi_damage_interactions(version_id,source_ref,round_number,attacker_key,victim_key,damage,headshots,bodyshots,legshots) values(?,?,?,?,?,?,?,?,?)",
        match.damageInteractions(), 500, (ps, row) -> {
          ps.setLong(1, versionId); ps.setString(2, row.sourceRef()); ps.setInt(3, row.roundNumber());
          ps.setString(4, identityKey(row.attacker())); ps.setString(5, identityKey(row.victim()));
          ps.setObject(6, row.damage()); ps.setObject(7, row.headshots()); ps.setObject(8, row.bodyshots()); ps.setObject(9, row.legshots());
        });
    db.batchUpdate("insert into mi_match_events(version_id,event_id,source_ref,raw_source_type,category,round_number,round_time_raw,match_time_raw,actor_key,target_key,assistants_json,weapon_name,weapon_category,damage,source_evidence_json) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        match.events(), 500, (ps, row) -> {
          ps.setLong(1, versionId); ps.setString(2, row.eventId()); ps.setString(3, row.sourceRef()); ps.setString(4, row.rawSourceType());
          ps.setString(5, row.category().name()); ps.setInt(6, row.roundNumber()); ps.setObject(7, row.roundTimeRaw()); ps.setObject(8, row.matchTimeRaw());
          ps.setString(9, identityKey(row.actor())); ps.setString(10, identityKey(row.target())); ps.setString(11, write(row.assistants()));
          ps.setString(12, row.weaponName()); ps.setString(13, row.weaponCategory()); ps.setObject(14, row.damage());
          ps.setString(15, jsonText(row.sourceEvidence()));
        });
    List<PositionSnapshot> positions = match.positions();
    for (int start = 0; start < positions.size(); start += 500) {
      List<PositionSnapshot> batch = positions.subList(start, Math.min(start + 500, positions.size()));
      AtomicInteger positionIndex = new AtomicInteger(start);
      db.batchUpdate("insert into mi_event_positions(version_id,position_number,source_ref,snapshot_type,round_number,participant_key,x,y,view_radians,coordinate_space) values(?,?,?,?,?,?,?,?,?,?)",
          batch, 500, (ps, row) -> {
            ps.setLong(1, versionId); ps.setInt(2, positionIndex.getAndIncrement()); ps.setString(3, row.sourceRef());
            ps.setString(4, row.snapshotType()); ps.setInt(5, row.roundNumber()); ps.setString(6, identityKey(row.identity()));
            ps.setObject(7, row.x()); ps.setObject(8, row.y()); ps.setObject(9, row.viewRadians()); ps.setString(10, row.coordinateSpace());
          });
    }
  }

  private static String identityKey(MatchIdentityResolver.Identity identity) {
    return identity == null || !identity.hasProviderIdentity() ? null : identity.stableKey();
  }
  private String jsonText(JsonNode value) { return value == null || value.isNull() ? null : write(value); }

  public JobView getJob(String owner, String jobId) {
    List<JobView> jobs = db.query("select job_id,status,requested_matches,response_count,accepted_matches,partial_matches,rejected_matches,parser_version,created_at from mi_import_jobs where owner_username=? and job_id=?",
        (rs, n) -> new JobView(rs.getString("job_id"), rs.getString("status"), rs.getInt("requested_matches"),
            rs.getInt("response_count"), rs.getInt("accepted_matches"), rs.getInt("partial_matches"),
            rs.getInt("rejected_matches"), rs.getString("parser_version"), rs.getTimestamp("created_at").toInstant(),
            getJobMatches(owner, rs.getString("job_id"))), owner, jobId);
    if (jobs.isEmpty()) throw notFound();
    return jobs.getFirst();
  }

  private List<JobMatchView> getJobMatches(String owner, String jobId) {
    return db.query("select jm.entry_number,jm.provider_match_id,jm.quality,jm.source_response_sha256,jm.source_captured_at,jm.issues_json from mi_import_job_matches jm join mi_import_jobs j on j.job_id=jm.job_id where j.owner_username=? and jm.job_id=? order by jm.entry_number",
        (rs, n) -> new JobMatchView(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
            rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant(), readIssues(rs.getString(6))), owner, jobId);
  }

  public MatchPage listMatches(String owner, String playerFilter, int page, int size) {
    if (page < 0 || page > 10_000 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid match page or page size.");
    String filter = playerFilter == null || playerFilter.isBlank() ? null : playerFilter.trim();
    String where = "j.owner_username=? and jm.version_id is not null";
    List<Object> parameters = new ArrayList<>();
    parameters.add(owner);
    if (filter != null) {
      where += " and lower(jm.selected_player_riot_id)=lower(?)";
      parameters.add(filter);
    }
    long total = db.queryForObject("select count(*) from (select jm.provider,jm.provider_match_id from mi_import_job_matches jm join mi_import_jobs j on j.job_id=jm.job_id where " + where + " group by jm.provider,jm.provider_match_id) owned",
        Long.class, parameters.toArray());
    String sql = "select provider_match_id,quality,selected_player_identity,selected_player_riot_id,selected_player_team_id,summary_json from (" +
        "select jm.provider_match_id,jm.quality,jm.selected_player_identity,jm.selected_player_riot_id,jm.selected_player_team_id,v.summary_json,"+
        "row_number() over(partition by jm.provider,jm.provider_match_id order by j.created_at desc,jm.entry_number desc) as rn,"+
        "v.source_started_at from mi_import_job_matches jm join mi_import_jobs j on j.job_id=jm.job_id join mi_match_versions v on v.version_id=jm.version_id where " + where +
        ") owned where rn=1 order by source_started_at desc nulls last,provider_match_id limit ? offset ?";
    parameters.add(size);
    parameters.add(page * size);
    List<MatchSummary> rows = db.query(sql, summaryRowMapper(), parameters.toArray());
    return new MatchPage(page, size, total, rows);
  }

  private RowMapper<MatchSummary> summaryRowMapper() {
    return (rs, n) -> {
      try { return new MatchSummary(rs.getString("provider_match_id"), rs.getString("quality"),
          rs.getString("selected_player_riot_id"), rs.getString("selected_player_team_id"), mapper.readTree(rs.getString("summary_json"))); }
      catch (IOException error) { throw new IllegalStateException("Stored match summary could not be read.", error); }
    };
  }

  public MatchView getMatch(String owner, String providerMatchId) {
    return getMatch(owner, providerMatchId, null);
  }

  /** Owner-scoped lookup with an optional selected-player filter for multi-perspective imports. */
  public MatchView getMatch(String owner, String providerMatchId, String selectedPlayerId) {
    validateMatchId(providerMatchId);
    String playerFilter = selectedPlayerId == null ? "" : " and (lower(jm.selected_player_riot_id)=lower(?) or lower(jm.selected_player_identity)=lower(?))";
    Object[] parameters = selectedPlayerId == null
        ? new Object[]{owner, PROVIDER, providerMatchId}
        : new Object[]{owner, PROVIDER, providerMatchId, selectedPlayerId, selectedPlayerId};
    List<OwnedMatchDbRow> rows = db.query("select jm.quality,jm.selected_player_identity,jm.selected_player_riot_id,jm.selected_player_team_id,jm.source_response_sha256,jm.source_captured_at,jm.issues_json,v.canonical_json from mi_import_job_matches jm join mi_import_jobs j on j.job_id=jm.job_id join mi_match_versions v on v.version_id=jm.version_id where j.owner_username=? and jm.provider=? and jm.provider_match_id=? and jm.version_id is not null" + playerFilter + " order by j.created_at desc,jm.entry_number desc limit 1",
        (rs, n) -> new OwnedMatchDbRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
            rs.getString(5), rs.getTimestamp(6), rs.getString(7), rs.getString(8)),
        parameters);
    if (rows.isEmpty()) throw notFound();
    OwnedMatchDbRow row = rows.getFirst();
    try {
      CanonicalMatch match = mapper.readValue(row.canonicalJson(), CanonicalMatch.class);
      var storedProvenance = new MatchProvenanceService.Provenance(match.provenance().provider(),
          match.provenance().responseSource(), row.sourceCapturedAt() == null ? null : row.sourceCapturedAt().toInstant(),
          row.sourceResponseSha256(), match.provenance().parserVersion(), match.provenance().captureMethod());
      return new MatchView(match, new PlayerPerspective(row.selectedIdentity(), row.selectedRiotId(), row.selectedTeamId()),
          storedProvenance, row.quality(), readIssues(row.issuesJson()));
    }
    catch (IOException error) { throw new IllegalStateException("Stored match version could not be read.", error); }
  }

  public List<Round> getRounds(String owner, String matchId) { return getMatch(owner, matchId).match().rounds(); }

  public RoundView getRound(String owner, String matchId, int roundNumber) {
    if (roundNumber < 1) throw new IllegalArgumentException("Round number must be positive.");
    MatchView view = getMatch(owner, matchId);
    CanonicalMatch match = view.match();
    Round round = match.rounds().stream().filter(item -> item.roundNumber() == roundNumber).findFirst().orElseThrow(MatchIntelligenceStore::notFound);
    return new RoundView(match.providerMatchId(), roundNumber, view.perspective(), round,
        match.playerRounds().stream().filter(item -> item.roundNumber() == roundNumber).toList(),
        match.damageInteractions().stream().filter(item -> item.roundNumber() == roundNumber).toList(),
        match.events().stream().filter(item -> item.roundNumber() == roundNumber).toList(),
        match.positions().stream().filter(item -> item.roundNumber() == roundNumber).toList());
  }

  public boolean deleteRawImport(String owner, String jobId) {
    if (db.queryForObject("select count(*) from mi_import_jobs where owner_username=? and job_id=?", Integer.class, owner, jobId) == 0) throw notFound();
    return db.update("delete from mi_raw_imports where job_id=?", jobId) > 0;
  }

  public void deleteImport(String owner, String jobId) {
    tx.executeWithoutResult(status -> {
      if (db.queryForObject("select count(*) from mi_import_jobs where owner_username=? and job_id=?", Integer.class, owner, jobId) == 0) throw notFound();
      db.update("delete from mi_import_jobs where owner_username=? and job_id=?", owner, jobId);
      db.update("delete from mi_match_versions v where not exists (select 1 from mi_import_job_matches jm where jm.version_id=v.version_id)");
      db.update("delete from mi_matches m where not exists (select 1 from mi_match_versions v where v.provider=m.provider and v.provider_match_id=m.provider_match_id)");
    });
  }

  @Scheduled(fixedDelayString="${match-intelligence.raw-cleanup-delay-ms:21600000}",
      initialDelayString="${match-intelligence.raw-cleanup-delay-ms:21600000}")
  public void deleteExpiredRawImports() {
    db.update("delete from mi_raw_imports where purge_after < current_timestamp");
  }

  private String write(Object value) {
    try { return mapper.writeValueAsString(value); } catch (IOException error) { throw new IllegalStateException("Unable to serialize validated match records.", error); }
  }
  private List<MatchValidationIssue> readIssues(String json) {
    try { return Arrays.asList(mapper.readValue(json, MatchValidationIssue[].class)); } catch (IOException error) { throw new IllegalStateException("Stored validation summary could not be read.", error); }
  }
  private byte[] gzip(byte[] bytes) {
    try (ByteArrayOutputStream buffer = new ByteArrayOutputStream(); GZIPOutputStream gzip = new GZIPOutputStream(buffer)) {
      gzip.write(bytes); gzip.finish(); return buffer.toByteArray();
    } catch (IOException error) { throw new IllegalStateException("Unable to compress private source export.", error); }
  }
  private static String sha256(byte[] bytes) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    catch (Exception error) { throw new IllegalStateException("Unable to fingerprint import.", error); }
  }
  private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Match or import was not found for this account."); }
  private static void validateMatchId(String id) {
    if (id == null || id.length() > 160 || !id.matches("[A-Za-z0-9._:-]+")) throw new IllegalArgumentException("Invalid provider match identifier.");
  }
}
