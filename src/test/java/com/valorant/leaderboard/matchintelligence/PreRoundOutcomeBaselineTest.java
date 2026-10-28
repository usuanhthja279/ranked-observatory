package com.valorant.leaderboard.matchintelligence;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import static com.valorant.leaderboard.matchintelligence.CanonicalMatch.*;

class PreRoundOutcomeBaselineTest {
  private final MatchExportParser parser=new MatchExportParser(MatchIntelligenceFixtures.JSON);
  private final PreRoundOutcomeBaseline baseline=new PreRoundOutcomeBaseline(null,null);

  @Test void featuresContainOnlyRoundIndexPriorScoreAndReportedSide() throws Exception {
    CanonicalMatch base=parse();
    List<PreRoundOutcomeBaseline.Example> before=baseline.extract(base);
    assertEquals(2,before.size());
    assertArrayEquals(new double[]{1.0/30.0,0.0,1.0},before.getFirst().features());
    List<Round> altered=List.of(base.rounds().getFirst(),new Round(2,"Red",base.rounds().get(1).rawWinMethod(),base.rounds().get(1).plantEvidence(),base.rounds().get(1).defuseEvidence(),base.rounds().get(1).detonationTimeRaw(),base.rounds().get(1).overtimeByRoundPosition()));
    CanonicalMatch futureChanged=copy(base,base.providerMatchId(),altered);
    var after=baseline.extract(futureChanged);
    assertArrayEquals(before.getFirst().features(),after.getFirst().features());
    assertEquals("pre-round-score-side-v1",PreRoundOutcomeBaseline.FEATURE_VERSION);
  }

  @Test void modelEvaluationUsesMatchGroupedDeterministicSplitAndReportsCalibration() throws Exception {
    CanonicalMatch base=parse();List<CanonicalMatch> matches=new ArrayList<>();
    for(int i=0;i<120;i++){
      List<Round> rounds=new ArrayList<>();
      for(Round r:base.rounds()){
        String winner=((i+r.roundNumber())%3==0)?"Red":"Blue";
        rounds.add(new Round(r.roundNumber(),winner,r.rawWinMethod(),r.plantEvidence(),r.defuseEvidence(),r.detonationTimeRaw(),r.overtimeByRoundPosition()));
      }
      matches.add(copy(base,"synthetic-group-"+i,rounds));
    }
    matches.add(matches.getFirst());
    var a=baseline.evaluate(matches);var b=baseline.evaluate(matches);
    assertEquals("EVALUATED_BASELINE",a.status());
    assertEquals(120,a.matchCount());
    assertEquals(96,a.trainMatchCount());assertEquals(24,a.testMatchCount());
    assertEquals(240,a.trainRows()+a.testRows());
    assertNotNull(a.brierScore());assertNotNull(a.falsePositiveRate());
    assertFalse(a.confidenceCalibration().isEmpty());
    assertEquals(a,b);
  }

  @Test void insufficientSamplesDoNotTrainOrReportPredictiveMetrics() throws Exception {
    var result=baseline.evaluate(List.of(parse()));
    assertEquals("INSUFFICIENT_EVIDENCE",result.status());
    assertNull(result.accuracy());assertNull(result.brierScore());assertNull(result.falsePositiveRate());
    assertTrue(result.limitations().stream().anyMatch(x->x.contains("no model is activated")));
  }

  private CanonicalMatch parse() throws Exception {
    var report=parser.parse(MatchIntelligenceFixtures.JSON.writeValueAsString(MatchIntelligenceFixtures.export(2,1)),MatchIntelligenceFixtures.PLAYER);
    return report.matches().getFirst().match();
  }
  private CanonicalMatch copy(CanonicalMatch m,String id,List<Round> rounds){return new CanonicalMatch(m.provider(),id,m.responseSource(),m.modeKey(),m.modeName(),m.queueId(),m.mapId(),m.mapName(),m.patch(),m.actId(),m.startedAt(),m.durationMillis(),m.ranked(),m.overtime(),m.complete(),m.selectedPlayerIdentity(),m.selectedPlayerRiotId(),m.selectedPlayerTeamId(),m.teams(),m.participants(),rounds,m.playerRounds(),m.damageInteractions(),m.events(),m.positions(),m.mapTransform(),m.availability(),m.provenance(),m.parserVersion(),m.dataQuality(),m.issues());}
}
