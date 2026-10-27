package com.valorant.leaderboard.matchintelligence;

import static com.valorant.leaderboard.matchintelligence.CanonicalMatch.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Owner-private reference cohorts. Membership and rank are owner-entered; imports are derived from owned data. */
@Service
public class MatchBenchmarkService {
  public static final int REQUIRED_PLAYERS = 10;
  public static final int REQUIRED_UNIQUE_MATCHES = 20;
  private static final Set<String> REGIONS = Set.of("ap", "eu", "na", "br", "kr", "latam");
  private final JdbcTemplate db;
  private final ObjectMapper mapper;
  private final DeterministicMatchAnalyzer analyzer;

  public MatchBenchmarkService(JdbcTemplate db, ObjectMapper mapper, DeterministicMatchAnalyzer analyzer) {
    this.db = db; this.mapper = mapper; this.analyzer = analyzer;
  }

  public record MemberInput(int selectionRank, String riotId, String displayName, Integer matchesRequested) { }
  public record CreateInput(String name, String region, String actId, LocalDate leaderboardSnapshotDate,
      String dataAccessStatus, List<MemberInput> members) { }
  public record MemberCoverage(int selectionRank, String riotId, String displayName,
      int matchesRequested, int matchesSuccessfullyImported, int uniqueMatches,
      boolean identityAvailable, Map<String, Integer> sourceCoverage, List<String> rankAtMatch) { }
  public record Cohort(String cohortId, String name, String region, String actId,
      LocalDate leaderboardSnapshotDate, String dataAccessStatus,
      String dataAccessStatusMeaning, int requestedMatches, int successfullyImportedMatches,
      int uniqueMatches, int uniquePlayersWithData, List<MemberCoverage> members) { }
  public record MetricComparison(String key, String label, Double subjectValue, Double referenceMedian,
      Double referenceP25, Double referenceP75, Double difference, String unit,
      int playersWithMetric, int uniqueReferenceMatches, int subjectMatches, String availability) { }
  public record Comparison(String status, String subjectRiotId, String cohortId, String scenario,
      int requiredPlayers, int eligibleReferencePlayers, int uniqueReferenceMatches,
      int subjectUniqueMatches, int excludedSubjectMatches, List<MetricComparison> metrics,
      List<String> limitations) { }

  @Transactional
  public Cohort create(String owner, CreateInput input) {
    if (owner == null || owner.isBlank() || owner.length() > 100) throw new IllegalArgumentException("Authenticated account identity is invalid.");
    if (input == null) throw new IllegalArgumentException("Cohort details are required.");
    String name = clean(input.name(), 160, "Cohort name");
    String region = clean(input.region(), 32, "Region").toLowerCase(Locale.ROOT);
    if (!REGIONS.contains(region)) throw new IllegalArgumentException("Choose a supported leaderboard region.");
    String act = clean(input.actId(), 160, "Act");
    if (input.leaderboardSnapshotDate() == null) throw new IllegalArgumentException("Leaderboard snapshot date is required.");
    String access = Objects.toString(input.dataAccessStatus(), "UNCONFIRMED").toUpperCase(Locale.ROOT);
    if (!Set.of("UNCONFIRMED", "OWNER_REPORTED_PERMISSION").contains(access)) throw new IllegalArgumentException("Unsupported data access status.");
    if (input.members() == null || input.members().size() < REQUIRED_PLAYERS || input.members().size() > REQUIRED_PLAYERS)
      throw new IllegalArgumentException("A reference cohort must record exactly 10 selected players.");
    Set<Integer> ranks = new HashSet<>(); Set<String> identities = new HashSet<>();
    List<MemberInput> members = new ArrayList<>(input.members());
    members.sort(Comparator.comparingInt(MemberInput::selectionRank));
    for (MemberInput member : members) {
      if (member == null || member.selectionRank() < 1 || member.selectionRank() > 10 || !ranks.add(member.selectionRank()))
        throw new IllegalArgumentException("Selection ranks must be unique values from 1 to 10.");
      String riot = clean(member.riotId(), 300, "Player Riot ID");
      if (!identities.add(riot.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("A player can appear only once in a cohort.");
      if (member.matchesRequested() != null && (member.matchesRequested() < 1 || member.matchesRequested() > 20))
        throw new IllegalArgumentException("Requested match count must be between 1 and 20.");
      if (member.displayName() != null && member.displayName().length() > 200) throw new IllegalArgumentException("Display name is too long.");
    }
    if (!ranks.equals(Set.of(1,2,3,4,5,6,7,8,9,10))) throw new IllegalArgumentException("Cohort selection ranks must cover 1 through 10.");
    String id = UUID.randomUUID().toString();
    db.update("insert into mi_benchmark_cohorts(cohort_id,owner_username,name,region,act_id,leaderboard_snapshot_date,data_access_status) values(?,?,?,?,?,?,?)",
        id, owner, name, region, act, java.sql.Date.valueOf(input.leaderboardSnapshotDate()), access);
    for (MemberInput member : members) {
      String riot = member.riotId().trim();
      db.update("insert into mi_benchmark_memberships(cohort_id,selection_rank,riot_id,normalized_riot_id,display_name,matches_requested) values(?,?,?,?,?,?)",
          id, member.selectionRank(), riot, riot.toLowerCase(Locale.ROOT), blankToNull(member.displayName()),
          member.matchesRequested() == null ? 20 : member.matchesRequested());
    }
    return get(owner, id);
  }

  public List<Cohort> list(String owner) {
    List<String> ids = db.query("select cohort_id from mi_benchmark_cohorts where owner_username=? order by created_at desc,cohort_id", (rs, n) -> rs.getString(1), owner);
    return ids.stream().map(id -> get(owner, id)).toList();
  }

  public Cohort get(String owner, String id) {
    List<CohortRow> rows = db.query("select cohort_id,name,region,act_id,leaderboard_snapshot_date,data_access_status from mi_benchmark_cohorts where owner_username=? and cohort_id=?",
        (rs, n) -> new CohortRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
            rs.getDate(5).toLocalDate(), rs.getString(6)), owner, id);
    if (rows.isEmpty()) throw notFound();
    CohortRow cohort = rows.getFirst();
    List<MemberRaw> members = db.query("select selection_rank,riot_id,display_name,matches_requested from mi_benchmark_memberships where cohort_id=? order by selection_rank",
        (rs, n) -> new MemberRaw(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4)), id);
    List<MemberCoverage> coverage = members.stream().map(member -> coverage(owner, member)).toList();
    int requested = coverage.stream().mapToInt(MemberCoverage::matchesRequested).sum();
    int imported = coverage.stream().mapToInt(MemberCoverage::matchesSuccessfullyImported).sum();
    Set<String> ids = new HashSet<>();
    for (MemberRaw member : members) sourceRows(owner,member.riotId(),null,member.matchesRequested()).stream()
        .map(SourceRow::matchId).filter(Objects::nonNull).forEach(ids::add);
    int available = (int) coverage.stream().filter(MemberCoverage::identityAvailable).count();
    return new Cohort(cohort.id(), cohort.name(), cohort.region(), cohort.actId(), cohort.snapshotDate(), cohort.accessStatus(),
        cohort.accessStatus().equals("OWNER_REPORTED_PERMISSION")
            ? "Owner reported permission; this status is not independently verified."
            : "Permission/access status has not been confirmed.",
        requested, imported, ids.size(), available, coverage);
  }

  public Comparison compare(String owner, String cohortId, String subjectRiotId,
      String map, String mode, String agent) {
    String subject = clean(subjectRiotId, 300, "Subject Riot ID");
    Cohort cohort = get(owner, cohortId);
    String mapFilter = optionalFilter(map, 100, "Map");
    String modeFilter = optionalFilter(mode, 80, "Mode");
    String agentFilter = optionalFilter(agent, 80, "Agent");
    List<BenchPlayer> reference = new ArrayList<>(); Set<String> uniqueMatches = new HashSet<>();
    boolean subjectIsMember = false;
    for (MemberCoverage member : cohort.members()) {
      if (same(member.riotId(), subject)) { subjectIsMember = true; continue; }
      List<Analyzed> analyzed = analyzeOwnedMatches(owner, member.riotId(), cohort.actId(), mapFilter, modeFilter, agentFilter);
      if (!analyzed.isEmpty()) {
        reference.add(new BenchPlayer(member.riotId(), analyzed));
        analyzed.forEach(item -> uniqueMatches.add(item.matchId()));
      }
    }
    List<Analyzed> subjectMatches = analyzeOwnedMatches(owner, subject, cohort.actId(), mapFilter, modeFilter, agentFilter);
    int excludedSubjectMatches=subjectIsMember?subjectMatches.size():0;
    Map<String,List<Double>> distributions = new LinkedHashMap<>();
    Map<String,Set<String>> metricMatchIds = new HashMap<>();
    Map<String,String> labels = new HashMap<>(), units = new HashMap<>();
    for (BenchPlayer player : reference) {
      Map<String,Aggregate> aggregated = aggregate(player.matches());
      aggregated.forEach((key, value) -> {
        if (value.denominator() > 0) {
          distributions.computeIfAbsent(key, unused -> new ArrayList<>()).add(value.numerator() / value.denominator());
          player.matches().stream().filter(item->item.report().metrics().stream().anyMatch(metric->metric.key().equals(key)&&metric.denominator()!=null&&metric.denominator()>0))
              .map(Analyzed::matchId).forEach(id->metricMatchIds.computeIfAbsent(key,unused->new HashSet<>()).add(id));
        }
      });
      for (Analyzed item : player.matches()) item.report().metrics().forEach(metric -> { labels.putIfAbsent(metric.key(), metric.label()); units.putIfAbsent(metric.key(), metric.unit()); });
    }
    Map<String,Aggregate> subjectAggregate = aggregate(subjectMatches);
    for (Analyzed item : subjectMatches) item.report().metrics().forEach(metric -> { labels.putIfAbsent(metric.key(), metric.label()); units.putIfAbsent(metric.key(), metric.unit()); });
    List<MetricComparison> metrics = new ArrayList<>();
    boolean sufficient = reference.size() >= REQUIRED_PLAYERS && uniqueMatches.size() >= REQUIRED_UNIQUE_MATCHES;
    for (String key : List.of("player_opening_kills","player_opening_deaths","team_opening_conversion","confirmed_trades","team_loadout_advantage_rounds","objective_events","multi_kill_rounds","no_death_rounds","recorded_damage")) {
      List<Double> values = distributions.getOrDefault(key, List.of()).stream().sorted().toList();
      Aggregate s = subjectAggregate.get(key);
      Double subjectValue = s != null && s.denominator() > 0 ? s.numerator()/s.denominator() : null;
      Double median = sufficient && !values.isEmpty() ? quantile(values, .5) : null;
      metrics.add(new MetricComparison(key, labels.getOrDefault(key, key), subjectValue, median,
          sufficient && !values.isEmpty() ? quantile(values,.25) : null,
          sufficient && !values.isEmpty() ? quantile(values,.75) : null,
          median != null && subjectValue != null ? subjectValue-median : null,
          units.getOrDefault(key, "source-defined unit"), values.size(), metricMatchIds.getOrDefault(key,Set.of()).size(), subjectMatches.size(),
          !sufficient ? "INSUFFICIENT_EVIDENCE" : values.size()<REQUIRED_PLAYERS ? "INSUFFICIENT_METRIC_COVERAGE" : subjectValue==null ? "SUBJECT_METRIC_UNAVAILABLE" : "OBSERVED"));
    }
    String scenario = "competitive · act " + cohort.actId() + (mapFilter==null?"":" · map "+mapFilter)
        +(modeFilter==null?"":" · mode "+modeFilter)+(agentFilter==null?"":" · agent "+agentFilter);
    List<String> limits = List.of("Cohort rank is the owner-recorded selection rank; participant rank is match-time source data and is not treated as the same measurement.",
        "One member contributes one per-player aggregate to the reference distribution. Shared match IDs are counted once for unique-match coverage.",
        "Quartiles show between-player spread, not a confidence interval. They do not establish equal opponent difficulty or correct decisions.",
        "Economy categories, lobby strength and role equivalence are not inferred; only versioned source-supported metrics are eligible.",
        cohort.dataAccessStatusMeaning());
    return new Comparison(sufficient?"OBSERVED":"INSUFFICIENT_EVIDENCE", subject, cohortId, scenario,
        REQUIRED_PLAYERS, reference.size(), uniqueMatches.size(), subjectMatches.size(), excludedSubjectMatches, metrics, limits);
  }

  private MemberCoverage coverage(String owner, MemberRaw member) {
    List<SourceRow> rows = sourceRows(owner, member.riotId(), null, member.matchesRequested());
    Map<String,Integer> coverage = new TreeMap<>(); Set<String> matchIds = new HashSet<>(); Set<String> ranks = new TreeSet<>();
    int imported = 0; boolean identityAvailable=false;
    for (SourceRow row : rows) {
      if (row.matchId()==null) continue;
      matchIds.add(row.matchId());
      if (!"INVALID".equals(row.quality())) imported++;
      try {
        CanonicalMatch match = mapper.readValue(row.canonicalJson(), CanonicalMatch.class);
        identityAvailable |= identity(match, member.riotId()) != null;
        if (match.availability()!=null) {
          if (match.availability().participantRounds()) coverage.merge("participantRounds",1,Integer::sum);
          if (match.availability().damageInteractions()) coverage.merge("damageInteractions",1,Integer::sum);
          if (match.availability().killEvents()) coverage.merge("killEvents",1,Integer::sum);
          if (match.availability().economy()) coverage.merge("economy",1,Integer::sum);
          if (match.availability().objectiveEvents()) coverage.merge("objectiveEvents",1,Integer::sum);
        }
        MatchIdentityResolver.Identity identity = identity(match, member.riotId());
        if (identity!=null) match.participants().stream().filter(p->p.identity()!=null&&p.identity().stableKey().equals(identity.stableKey()))
            .map(Participant::observedRank).filter(Objects::nonNull).forEach(ranks::add);
      } catch (Exception ignored) { /* malformed old row is reported as no coverage, not exposed */ }
    }
    return new MemberCoverage(member.rank(),member.riotId(),member.displayName(),member.matchesRequested(), imported,
        matchIds.size(),identityAvailable,Map.copyOf(coverage),List.copyOf(ranks));
  }

  private List<Analyzed> analyzeOwnedMatches(String owner, String riotId, String actId,
      String map, String mode, String agent) {
    List<SourceRow> rows = sourceRows(owner,riotId,actId,20);
    List<Analyzed> result = new ArrayList<>();
    for (SourceRow row : rows) try {
      CanonicalMatch match = mapper.readValue(row.canonicalJson(),CanonicalMatch.class);
      if (!Boolean.TRUE.equals(match.ranked()) || !Objects.toString(match.modeName(),"").toLowerCase(Locale.ROOT).contains("competitive")) continue;
      if (map!=null&&!same(match.mapName(),map) || mode!=null&&!same(match.modeName(),mode)) continue;
      MatchIdentityResolver.Identity identity = identity(match,riotId);
      if(identity==null) continue;
      Participant player=match.participants().stream().filter(p->p.identity()!=null&&p.identity().stableKey().equals(identity.stableKey())).findFirst().orElse(null);
      if(player==null || agent!=null&&!same(player.agent(),agent)) continue;
      CanonicalMatch scoped = new CanonicalMatch(match.provider(),match.providerMatchId(),match.responseSource(),match.modeKey(),match.modeName(),match.queueId(),match.mapId(),match.mapName(),match.patch(),match.actId(),match.startedAt(),match.durationMillis(),match.ranked(),match.overtime(),match.complete(),identity.stableKey(),riotId,player.teamId(),match.teams(),match.participants(),match.rounds(),match.playerRounds(),match.damageInteractions(),match.events(),match.positions(),match.mapTransform(),match.availability(),match.provenance(),match.parserVersion(),match.dataQuality(),match.issues());
      result.add(new Analyzed(match.providerMatchId(),analyzer.analyze(scoped)));
    } catch(Exception ignored) { /* invalid historical row is unavailable */ }
    return result;
  }

  private List<SourceRow> sourceRows(String owner,String riotId,String actId,int limit) {
    String sql="select provider_match_id,quality,selected_player_riot_id,selected_player_team_id,canonical_json from (select jm.provider,jm.provider_match_id,jm.quality,jm.selected_player_riot_id,jm.selected_player_team_id,v.canonical_json,v.act_id,v.source_started_at,j.created_at,jm.entry_number,row_number() over(partition by jm.provider,jm.provider_match_id order by j.created_at desc,jm.entry_number desc) rn from mi_import_job_matches jm join mi_import_jobs j on j.job_id=jm.job_id join mi_match_versions v on v.version_id=jm.version_id where j.owner_username=? and lower(jm.selected_player_riot_id)=lower(?) and jm.version_id is not null and (cast(? as varchar) is null or v.act_id=cast(? as varchar))) owned where rn=1 order by source_started_at desc nulls last,provider_match_id limit ?";
    return db.query(sql,(rs,n)->new SourceRow(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5)),owner,riotId,actId,actId,limit);
  }

  private MatchIdentityResolver.Identity identity(CanonicalMatch match,String riotId) {
    return match.participants().stream().map(Participant::identity).filter(Objects::nonNull)
        .filter(identity->same(identity.observedRiotId(),riotId)).findFirst().orElse(null);
  }

  private Map<String,Aggregate> aggregate(List<Analyzed> matches) {
    Map<String,double[]> totals=new HashMap<>();
    for(Analyzed analyzed:matches) for(DeterministicMatchAnalyzer.Metric metric:analyzed.report().metrics()) {
      if(metric.numerator()==null||metric.denominator()==null)continue;
      double[] values=totals.computeIfAbsent(metric.key(),unused->new double[2]); values[0]+=metric.numerator(); values[1]+=metric.denominator();
    }
    Map<String,Aggregate> result=new HashMap<>(); totals.forEach((key,value)->result.put(key,new Aggregate(value[0],value[1]))); return result;
  }
  private double quantile(List<Double> sorted,double q) { double p=(sorted.size()-1)*q; int lo=(int)Math.floor(p),hi=(int)Math.ceil(p); return sorted.get(lo)+(sorted.get(hi)-sorted.get(lo))*(p-lo); }
  private String clean(String value,int max,String label) { if(value==null||value.isBlank()||value.trim().length()>max)throw new IllegalArgumentException(label+" is required and must be at most "+max+" characters.");return value.trim(); }
  private String optionalFilter(String value,int max,String label) { if(value==null||value.isBlank())return null;if(value.trim().length()>max)throw new IllegalArgumentException(label+" filter is too long.");return value.trim(); }
  private String blankToNull(String value) { return value==null||value.isBlank()?null:value.trim(); }
  private boolean same(String a,String b) { return a!=null&&b!=null&&a.equalsIgnoreCase(b); }
  private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Benchmark cohort was not found for this account."); }
  private record CohortRow(String id,String name,String region,String actId,LocalDate snapshotDate,String accessStatus) { }
  private record MemberRaw(int rank,String riotId,String displayName,int matchesRequested) { }
  private record SourceRow(String matchId,String quality,String riotId,String teamId,String canonicalJson) { }
  private record Analyzed(String matchId,DeterministicMatchAnalyzer.Report report) { }
  private record BenchPlayer(String riotId,List<Analyzed> matches) { }
  private record Aggregate(double numerator,double denominator) { }
}
