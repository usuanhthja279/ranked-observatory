package com.valorant.leaderboard.matchintelligence;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class DeterministicMatchAnalyzerTest {
  private final MatchExportParser parser = new MatchExportParser(MatchIntelligenceFixtures.JSON);
  private final DeterministicMatchAnalyzer analyzer = new DeterministicMatchAnalyzer();

  @Test void reportsSourceLinkedOpeningsEconomyAndCombatWithoutScoringIntent() throws Exception {
    var report=analyzer.analyze(parse(MatchIntelligenceFixtures.export(2,1)));
    assertEquals("deterministic-tactics-v1",report.analysisVersion());
    var opening=metric(report,"player_opening_kills");
    assertEquals(1,opening.numerator());
    assertEquals(1,opening.denominator());
    assertEquals("eligible opening rounds",opening.unit());
    var conversion=metric(report,"team_opening_conversion");
    assertEquals(1,conversion.numerator());
    assertEquals(1,conversion.denominator());
    var low=metric(report,"team_loadout_band_low");
    assertEquals(1,low.numerator());
    assertEquals(2,low.denominator());
    assertTrue(low.availability().contains("Not a source eco/full-buy label"));
    var damage=metric(report,"recorded_damage");
    assertEquals(200,damage.numerator());
    assertEquals(2,damage.denominator());
    assertEquals(100d,damage.value());
    assertTrue(report.findings().stream().allMatch(f->f.analysisVersion().equals(report.analysisVersion())));
    assertTrue(report.findings().stream().flatMap(f->f.sourceRefs().stream()).allMatch(r->r.startsWith("data.segments[")));
    assertTrue(report.unavailableAnalyses().stream().anyMatch(x->x.startsWith("Clutch analysis")));
    assertTrue(report.findings().stream().noneMatch(f->f.category().startsWith("EXPERT_CONFIRMED")));
  }

  @Test void tiesAndMissingOpeningTimesStayOutOfOpeningDenominator() throws Exception {
    ObjectNode tied=MatchIntelligenceFixtures.export(2,1);
    ArrayNode segments=MatchIntelligenceFixtures.segments(tied);
    addConsistentKill(tied,1,"Player 1#TAG","Player 6#TAG",3000);
    var tiedReport=analyzer.analyze(parse(tied));
    assertEquals(0,metric(tiedReport,"player_opening_kills").denominator());
    assertNull(metric(tiedReport,"player_opening_kills").value());

    ObjectNode untimed=MatchIntelligenceFixtures.export(2,1);
    ObjectNode row=null;
    for(var segment:MatchIntelligenceFixtures.segments(untimed)) if(segment.path("type").asText().equals("player-round-kills")) { row=(ObjectNode)segment; break; }
    assertNotNull(row);
    ((ObjectNode)row.path("metadata")).remove("roundTime");
    var untimedReport=analyzer.analyze(parse(untimed));
    assertEquals(0,metric(untimedReport,"player_opening_kills").denominator());
    assertTrue(untimedReport.unavailableAnalyses().stream().anyMatch(x->x.startsWith("Opening engagements")));
  }

  @Test void confirmsOnlyReciprocalTimedTradeLikeEliminations() throws Exception {
    ObjectNode export=MatchIntelligenceFixtures.export(2,1);
    ArrayNode segments=MatchIntelligenceFixtures.segments(export);
    addConsistentKill(export,1,"Player 5#TAG","Player 1#TAG",5000);
    addConsistentKill(export,1,"Player 2#TAG","Player 5#TAG",7000);
    ArrayNode rows=MatchIntelligenceFixtures.JSON.createArrayNode();
    segments.forEach(s->{if(s.path("type").asText().equals("player-round-kills"))rows.add(s);});
    ((ObjectNode)rows.get(0).path("metadata")).put("roundTime",3000);
    var report=analyzer.analyze(parse(export));
    assertEquals(1,metric(report,"confirmed_trades").numerator());
    assertTrue(report.findings().stream().anyMatch(f->f.category().equals("OBSERVED_POSITIVE")&&f.title().contains("trade-like")));
  }

  @Test void teamOpeningConversionIncludesTeammateOpeners() throws Exception {
    ObjectNode export=MatchIntelligenceFixtures.export(2,2);
    addConsistentKill(export,2,"Player 1#TAG","Player 6#TAG",3500);
    var report=analyzer.analyze(parse(export));
    assertEquals(1,metric(report,"player_opening_kills").numerator());
    assertEquals(2,metric(report,"team_opening_conversion").numerator());
    assertEquals(2,metric(report,"team_opening_conversion").denominator());
  }

  private CanonicalMatch parse(ObjectNode export) throws Exception {
    var report=parser.parse(MatchIntelligenceFixtures.JSON.writeValueAsString(export),MatchIntelligenceFixtures.PLAYER);
    assertEquals(1,report.acceptedMatches(),report.matches().toString());
    return report.matches().getFirst().match();
  }
  private void addConsistentKill(ObjectNode export,int round,String actor,String target,long time) {
    ArrayNode segments=MatchIntelligenceFixtures.segments(export);
    MatchIntelligenceFixtures.addKill(segments,round,actor,target,"Rifle","Weapon",100);
    ObjectNode kill=null;
    for(var segment:segments) if(segment.path("type").asText().equals("player-round-kills")) kill=(ObjectNode)segment;
    assertNotNull(kill);((ObjectNode)kill.path("metadata")).put("roundTime",time);
    incrementPlayer(segments,"player-summary",actor,"kills",1);
    incrementPlayer(segments,"player-summary",target,"deaths",1);
    incrementPlayerRound(segments,round,actor,"kills",1);
    incrementPlayerRound(segments,round,target,"deaths",1);
    incrementTeam(segments,teamFor(actor),"kills",1);
    incrementTeam(segments,teamFor(target),"deaths",1);
  }
  private void incrementPlayer(ArrayNode segments,String type,String player,String stat,int amount) {
    for(var segment:segments) if(segment.path("type").asText().equals(type)&&segment.path("attributes").path("platformUserIdentifier").asText().equals(player)) {
      ObjectNode value=(ObjectNode)segment.path("stats").path(stat);value.put("value",value.path("value").asInt()+amount);return;
    }
    fail("Missing synthetic player stats for "+player);
  }
  private void incrementPlayerRound(ArrayNode segments,int round,String player,String stat,int amount) {
    for(var segment:segments) if(segment.path("type").asText().equals("player-round")&&segment.path("attributes").path("platformUserIdentifier").asText().equals(player)&&segment.path("attributes").path("round").asInt()==round) {
      ObjectNode value=(ObjectNode)segment.path("stats").path(stat);value.put("value",value.path("value").asInt()+amount);return;
    }
    fail("Missing synthetic player-round stats for "+player);
  }
  private String teamFor(String player) { int index=Integer.parseInt(player.substring("Player ".length(),player.indexOf('#')));return index<5?"Blue":"Red"; }
  private void incrementTeam(ArrayNode segments,String team,String stat,int amount) {
    for(var segment:segments) if(segment.path("type").asText().equals("team-summary")&&segment.path("attributes").path("teamId").asText().equals(team)) {
      ObjectNode value=(ObjectNode)segment.path("stats").path(stat);value.put("value",value.path("value").asInt()+amount);return;
    }
    fail("Missing synthetic team summary for "+team);
  }
  private DeterministicMatchAnalyzer.Metric metric(DeterministicMatchAnalyzer.Report report,String key) {
    return report.metrics().stream().filter(m->m.key().equals(key)).findFirst().orElseThrow();
  }
}
