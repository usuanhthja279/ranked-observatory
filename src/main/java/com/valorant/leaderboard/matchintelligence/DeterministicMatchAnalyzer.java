package com.valorant.leaderboard.matchintelligence;

import static com.valorant.leaderboard.matchintelligence.CanonicalMatch.*;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Deterministic, evidence-linked metrics. Missing or ambiguous source evidence remains unavailable. */
@Service
public class DeterministicMatchAnalyzer {
  public static final String ANALYSIS_VERSION = "deterministic-tactics-v1";
  public static final String ECONOMY_RULESET_VERSION = "team-loadout-bands-v1";
  @Value("${match-intelligence.analysis.trade-window-raw:10000}")
  private long tradeWindowRaw = 10_000L;

  public record Metric(String key, String label, Long numerator, Long denominator, Double value,
      String unit, String evidenceType, String availability, List<String> sourceRefs) {
    public Metric { sourceRefs = List.copyOf(sourceRefs); }
  }
  public record Finding(String matchId, Integer roundNumber, String category, String title,
      String whatHappened, String whyItMayMatter, String confidence, List<String> sourceEventIds,
      List<String> sourceRefs, String analysisVersion, String evidenceType,
      String sourceCompleteness, String reason, Long numerator, Long denominator,
      String reviewQuestion, String applicableBenchmark) {
    public Finding { sourceEventIds = List.copyOf(sourceEventIds); sourceRefs = List.copyOf(sourceRefs); }
  }
  public record Report(String matchId, String analysisVersion, String economyRulesetVersion,
      List<Metric> metrics, List<Finding> findings, List<String> unavailableAnalyses) {
    public Report { metrics=List.copyOf(metrics); findings=List.copyOf(findings); unavailableAnalyses=List.copyOf(unavailableAnalyses); }
  }
  private record Opening(int round, MatchEvent event, String teamId) { }
  private record TradeSummary(long confirmed, long eligible, long possible, long missingTime,
      List<String> refs, List<Finding> findings) { }
  private record EconomySummary(long advantage, long complete, Map<String,long[]> bandResults,
      List<String> refs) { }
  private record CombatSummary(long multiKill, long killRows, long survival, long deathRows,
      long damage, long damageRows, List<String> refs) { }

  public Report analyze(CanonicalMatch match) {
    Objects.requireNonNull(match, "match");
    String identity=match.selectedPlayerIdentity(), ownTeam=match.selectedPlayerTeamId();
    List<Metric> metrics=new ArrayList<>(); List<Finding> findings=new ArrayList<>(); List<String> unavailable=new ArrayList<>();
    List<Opening> openings=openings(match); long playerKills=0,playerDeaths=0,ownAdvantages=0,ownConversions=0;
    List<String> openingRefs=new ArrayList<>();
    for(Opening opening:openings) {
      MatchEvent event=opening.event(); openingRefs.add(event.sourceRef());
      if(same(key(event.actor()),identity)) playerKills++;
      if(Objects.equals(opening.teamId(),ownTeam)) {
        ownAdvantages++;
        if(Objects.equals(opening.teamId(),winner(match,opening.round()))) ownConversions++;
      }
      if(same(key(event.target()),identity)) {
        playerDeaths++;
        if(Objects.equals(opening.teamId(),ownTeam)&&!Objects.equals(opening.teamId(),winner(match,opening.round()))) {
          findings.add(finding(match,opening.round(),"OBSERVED_NEGATIVE","Opening death in a team-loss round",
              "The selected player was targeted by the unique earliest timed credited kill and their team lost the round.",
              "This is an observed outcome, not evidence that the selected player made an error.","LOW",
              List.of(event.eventId()),List.of(event.sourceRef()),"FIRST_DEATH_AND_ROUND_RESULT",1L,1L,
              "Review teammate spacing, trade response, site context and remaining round conditions."));
        }
        findings.add(finding(match,opening.round(),"REVIEW_SUGGESTED","Review the opening death",
            "The selected player was targeted by the unique earliest timed credited kill.",
            "This is a review point only; an opening death alone is not evidence of a mistake.","LOW",
            List.of(event.eventId()),List.of(event.sourceRef()),"FIRST_DEATH_CONTEXT",null,null,
            "Review teammate spacing, trade response and objective context."));
      }
      if(same(key(event.actor()),identity)&&Objects.equals(opening.teamId(),ownTeam)
          &&Objects.equals(opening.teamId(),winner(match,opening.round()))) {
        findings.add(finding(match,opening.round(),"OBSERVED_POSITIVE","Opening kill followed by a team round win",
            "The selected player was credited with the unique earliest timed kill and their team won the round.",
            "This records an outcome only; it does not establish that the decision was optimal.","MEDIUM",
            List.of(event.eventId()),List.of(event.sourceRef()),"FIRST_KILL_AND_ROUND_RESULT",1L,1L,
            "Review positioning and objective context before drawing a tactical conclusion."));
      }
    }
    long openingDenominator=openings.size();
    metrics.add(metric("player_opening_kills","Selected-player opening kills",playerKills,openingDenominator,"eligible opening rounds","unique earliest credited kill with source time",openingRefs,openingDenominator==0?"No unambiguous timed opening events.":"Unique earliest credited kills only; tied or untimed events are excluded."));
    metrics.add(metric("player_opening_deaths","Selected-player opening deaths",playerDeaths,openingDenominator,"eligible opening rounds","unique earliest credited kill with source time",openingRefs,openingDenominator==0?"No unambiguous timed opening events.":"Descriptive only; not an error score."));
    metrics.add(metric("team_opening_conversion","Selected-team round wins after opening advantage",ownConversions,ownAdvantages,"selected-team opening advantages","opening event and round winner",openingRefs,ownAdvantages==0?"No selected-team opening advantages.":"Denominator includes rounds where selected team recorded the first credited kill."));
    if(openingDenominator==0) unavailable.add("Opening engagements: no unique earliest credited kill with a reported round time.");

    TradeSummary trades=trades(match,ownTeam,Math.max(0,tradeWindowRaw));
    metrics.add(metric("confirmed_trades","Confirmed reciprocal trades",trades.confirmed(),trades.eligible(),"eligible reciprocal target sequences","credited kill identities and raw source time",trades.refs(),trades.eligible()==0?"No eligible reciprocal sequences with comparable timing.":"A teammate of the prior victim eliminated the prior killer within the configured raw-time window; the raw unit is unverified."));
    metrics.add(metric("possible_trade_like_responses","Possible trade-like responses",trades.possible(),trades.missingTime(),"reciprocal sequences","credited kill identities; timing incomplete",trades.refs(),trades.possible()==0?"No reciprocal sequence with missing comparable time.":"Target relationship exists, but timing is missing; not counted as a confirmed trade."));
    findings.addAll(trades.findings());
    if(trades.eligible()==0&&trades.possible()==0) unavailable.add("Trade timing: no sufficiently supported reciprocal kill sequence was found.");

    EconomySummary economy=economy(match);
    metrics.add(metric("team_loadout_advantage_rounds","Rounds with higher recorded team loadout",economy.advantage(),economy.complete(),"complete team-loadout rounds","sum of recorded player loadout values",economy.refs(),economy.complete()==0?"Complete player loadout data for both teams was unavailable.":"Comparison uses sums of recorded loadout values and is not a source buy label."));
    for(String band:List.of("LOW","MID","HIGH")) {
      long[] result=economy.bandResults().getOrDefault(band,new long[]{0,0});
      metrics.add(metric("team_loadout_band_"+band.toLowerCase(Locale.ROOT),"Selected-team wins in heuristic "+band.toLowerCase(Locale.ROOT)+" loadout rounds",result[0],result[1],"rounds","versioned own-team loadout band",economy.refs(),result[1]==0?"No complete round in this heuristic band.":"Bands use own team recorded loadout sum: LOW <10,000; MID 10,000–19,999; HIGH ≥20,000. Not a source eco/full-buy label."));
    }
    if(economy.complete()==0) unavailable.add("Economy: both teams did not have complete per-player loadout values in any round.");

    List<MatchEvent> objective=match.events().stream().filter(e->Set.of(EventCategory.PLANT,EventCategory.DEFUSE,EventCategory.DETONATION).contains(e.category())).toList();
    List<String> objectiveRefs=objective.stream().map(MatchEvent::sourceRef).toList();
    metrics.add(metric("objective_events","Reported plant, defuse and detonation events",objective.size(),objective.isEmpty()?0:1,"events","explicit source objective event categories",objectiveRefs,objective.isEmpty()?"No explicit objective events reported.":"Value is a count of explicit events; unreported attempts cannot be reconstructed."));
    for(MatchEvent e:objective) if(same(key(e.actor()),identity)) findings.add(finding(match,e.roundNumber(),"OBSERVED_POSITIVE","Selected player credited with reported "+e.category().name().toLowerCase(Locale.ROOT),
        "The source attached this objective event to the selected player.","This provides objective context; it does not measure whether the timing or decision was optimal.","MEDIUM",List.of(e.eventId()),List.of(e.sourceRef()),"EXPLICIT_PLAYER_OBJECTIVE_EVENT",1L,1L,"Review the event alongside the round result and available positions."));
    if(objective.isEmpty()) unavailable.add("Objectives: plants, defuses and detonations were not reported.");

    CombatSummary combat=combat(match,identity);
    metrics.add(metric("multi_kill_rounds","Selected-player rounds with at least two recorded kills",combat.multiKill(),combat.killRows(),"player-round rows with reported kills","recorded player-round kill totals",combat.refs(),combat.killRows()==0?"Selected-player kill round rows unavailable.":"Counts recorded kills only and does not infer decision quality."));
    metrics.add(metric("no_death_rounds","Selected-player rounds with zero recorded deaths",combat.survival(),combat.deathRows(),"player-round rows with reported deaths","recorded player-round death totals",combat.refs(),combat.deathRows()==0?"Selected-player death round rows unavailable.":"Only rows with a reported death value enter the denominator."));
    metrics.add(metric("recorded_damage","Selected-player damage per reporting round",combat.damage(),combat.damageRows(),"damage per reporting round","player-round damage totals",combat.refs(),combat.damageRows()==0?"Selected-player damage was not reported.":"Mean of reported round damage; not a standalone performance score."));
    unavailable.add("Clutch analysis: not calculated because trustworthy alive-state and revival coverage is not established.");
    unavailable.add("Exact trade opportunity, visibility, movement, utility value and voice coordination: unavailable from discrete captured events.");
    if(findings.isEmpty()) findings.add(finding(match,null,"INSUFFICIENT_EVIDENCE","No player-specific coaching finding meets the evidence threshold",
        "The captured match does not support a player-specific positive, negative or review finding under the current rules.",
        "Missing evidence is kept unknown rather than converted into a performance judgment.","LOW",List.of(),List.of(),"NO_QUALIFYING_PLAYER_EVENT",null,null,
        "Capture richer round evidence or review the match manually."));
    return new Report(match.providerMatchId(),ANALYSIS_VERSION,ECONOMY_RULESET_VERSION,metrics,findings,unavailable);
  }

  private List<Opening> openings(CanonicalMatch match) {
    List<Opening> result=new ArrayList<>();
    for(Round round:match.rounds()) {
      List<MatchEvent> kills=match.events().stream().filter(e->e.roundNumber()==round.roundNumber()&&e.category()==EventCategory.CREDITED_PLAYER_KILL).toList();
      if(kills.isEmpty()||kills.stream().anyMatch(e->e.roundTimeRaw()==null)) continue;
      long min=kills.stream().mapToLong(MatchEvent::roundTimeRaw).min().orElseThrow();
      List<MatchEvent> earliest=kills.stream().filter(e->e.roundTimeRaw()==min).toList();
      if(earliest.size()==1) { MatchEvent event=earliest.getFirst(); String team=teamOf(match,event.actor()); if(team!=null) result.add(new Opening(round.roundNumber(),event,team)); }
    }
    return result;
  }
  private TradeSummary trades(CanonicalMatch m,String ownTeam,long window) {
    List<MatchEvent> kills=m.events().stream().filter(e->e.category()==EventCategory.CREDITED_PLAYER_KILL).toList();
    List<String> refs=new ArrayList<>();List<Finding> findings=new ArrayList<>();Set<String> usedResponses=new HashSet<>();long confirmed=0,eligible=0,possible=0,missing=0;
    for(MatchEvent death:kills) {
      String victimTeam=teamOf(m,death.target()),killerTeam=teamOf(m,death.actor());
      if(victimTeam==null||killerTeam==null||victimTeam.equals(killerTeam))continue;
      List<MatchEvent> candidates=kills.stream().filter(k->k.roundNumber()==death.roundNumber()&&!k.eventId().equals(death.eventId())&&!usedResponses.contains(k.eventId())&&Objects.equals(teamOf(m,k.actor()),victimTeam)&&Objects.equals(teamOf(m,k.target()),killerTeam)&&!same(key(k.actor()),key(death.target()))).toList();
      if(candidates.isEmpty()) continue;
      MatchEvent response=candidates.stream().filter(k->death.roundTimeRaw()!=null&&k.roundTimeRaw()!=null&&k.roundTimeRaw()>=death.roundTimeRaw()).min(Comparator.comparingLong(MatchEvent::roundTimeRaw)).orElse(candidates.getFirst());
      boolean exactEnough=death.roundTimeRaw()!=null&&response.roundTimeRaw()!=null&&response.roundTimeRaw()>=death.roundTimeRaw();
      if(exactEnough&&response.roundTimeRaw()-death.roundTimeRaw()<=window) {
        confirmed++;eligible++;usedResponses.add(response.eventId());refs.add(death.sourceRef());refs.add(response.sourceRef());
        if(Objects.equals(victimTeam,ownTeam)) findings.add(finding(m,death.roundNumber(),"OBSERVED_POSITIVE","Reciprocal trade-like elimination recorded","A teammate of the selected player was killed and another teammate later eliminated the prior killer within the configured raw-time window.","The source supports the event relationship and order, but not visibility or a missed opportunity.","MEDIUM",List.of(death.eventId(),response.eventId()),List.of(death.sourceRef(),response.sourceRef()),"RECIPROCAL_TARGET_AND_RAW_TIME",1L,1L,"Review positioning and timing; raw time units are not verified seconds."));
      } else if(death.roundTimeRaw()==null||response.roundTimeRaw()==null) {
        possible++;missing++;refs.add(death.sourceRef());refs.add(response.sourceRef());usedResponses.add(response.eventId());
        if(Objects.equals(victimTeam,ownTeam)) findings.add(finding(m,death.roundNumber(),"REVIEW_SUGGESTED","Possible reciprocal trade-like response","A teammate was killed and another teammate later eliminated the prior killer, but comparable timing is unavailable.","The target relationship is relevant; timing, visibility and opportunity remain unknown.","LOW",List.of(death.eventId(),response.eventId()),List.of(death.sourceRef(),response.sourceRef()),"RECIPROCAL_TARGET_TIME_MISSING",null,null,"Review the round timeline without assuming a missed trade."));
      } else eligible++;
    }
    return new TradeSummary(confirmed,eligible,possible,missing,refs,findings);
  }
  private EconomySummary economy(CanonicalMatch m) {
    Map<String,List<Participant>> teams=new HashMap<>();for(Participant p:m.participants())if(p.teamId()!=null)teams.computeIfAbsent(p.teamId(),k->new ArrayList<>()).add(p);
    long complete=0,advantage=0;Map<String,long[]> bands=new HashMap<>();List<String> refs=new ArrayList<>();
    for(Round round:m.rounds()) {
      Map<String,Long> totals=new HashMap<>();boolean valid=teams.size()==2;
      for(var team:teams.entrySet()) {long sum=0;for(Participant p:team.getValue()) {PlayerRound row=m.playerRounds().stream().filter(r->r.roundNumber()==round.roundNumber()&&same(r.identity().stableKey(),p.identity().stableKey())).findFirst().orElse(null);if(row==null||row.loadoutValue()==null){valid=false;break;}sum+=row.loadoutValue();refs.add("player-round:round:"+round.roundNumber());}totals.put(team.getKey(),sum);}
      if(!valid||totals.size()!=2)continue;
      Long own=totals.get(m.selectedPlayerTeamId()),opp=totals.entrySet().stream().filter(e->!e.getKey().equals(m.selectedPlayerTeamId())).map(Map.Entry::getValue).findFirst().orElse(null);if(own==null||opp==null)continue;
      complete++;if(own>opp)advantage++;String band=own<10_000?"LOW":own<20_000?"MID":"HIGH";long[] v=bands.computeIfAbsent(band,k->new long[2]);v[1]++;if(Objects.equals(round.winningTeamId(),m.selectedPlayerTeamId()))v[0]++;
    }
    return new EconomySummary(advantage,complete,bands,refs.stream().distinct().toList());
  }
  private CombatSummary combat(CanonicalMatch m,String identity) {
    List<PlayerRound> rows=m.playerRounds().stream().filter(r->same(r.identity().stableKey(),identity)).toList();
    long multi=rows.stream().filter(r->r.kills()!=null&&r.kills()>=2).count(),killRows=rows.stream().filter(r->r.kills()!=null).count(),survival=rows.stream().filter(r->r.deaths()!=null&&r.deaths()==0).count(),deathRows=rows.stream().filter(r->r.deaths()!=null).count();
    List<PlayerRound> damageRows=rows.stream().filter(r->r.damage()!=null).toList();List<String> refs=rows.stream().map(r->"player-round:round:"+r.roundNumber()).toList();
    return new CombatSummary(multi,killRows,survival,deathRows,damageRows.stream().mapToLong(r->r.damage()).sum(),damageRows.size(),refs);
  }
  private Metric metric(String key,String label,long numerator,long denominator,String unit,String evidence,List<String> refs,String availability) {
    return new Metric(key,label,numerator,denominator,denominator==0?null:(double)numerator/denominator,unit,evidence,availability,refs.stream().distinct().toList());
  }
  private Finding finding(CanonicalMatch m,Integer round,String category,String title,String what,String why,String confidence,List<String> ids,List<String> refs,String evidence,Long numerator,Long denominator,String review) {
    return new Finding(m.providerMatchId(),round,category,title,what,why,confidence,ids,refs,ANALYSIS_VERSION,evidence,String.valueOf(m.availability()),"Rule reflects source records only; no player intention or decision quality is inferred.",numerator,denominator,review,"No benchmark applied.");
  }
  private String winner(CanonicalMatch m,int round){return m.rounds().stream().filter(r->r.roundNumber()==round).map(Round::winningTeamId).findFirst().orElse(null);}
  private String teamOf(CanonicalMatch m,MatchIdentityResolver.Identity id){if(id==null)return null;return m.participants().stream().filter(p->same(p.identity().stableKey(),id.stableKey())).map(Participant::teamId).findFirst().orElse(null);}
  private String key(MatchIdentityResolver.Identity id){return id==null?null:id.stableKey();}
  private boolean same(String a,String b){return a!=null&&b!=null&&a.equalsIgnoreCase(b);}
}
