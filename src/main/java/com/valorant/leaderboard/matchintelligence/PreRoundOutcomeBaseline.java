package com.valorant.leaderboard.matchintelligence;

import static com.valorant.leaderboard.matchintelligence.CanonicalMatch.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Deterministic CPU-only round-outcome baseline. It is a research baseline, not tactical coaching. */
@Service
public class PreRoundOutcomeBaseline {
  public static final String FEATURE_VERSION = "pre-round-score-side-v1";
  public static final String MODEL_VERSION = "cpu-logistic-regression-v1";
  public static final long SPLIT_SEED = 20261028L;
  public static final int MIN_MATCHES = 10;
  public static final int MIN_ROWS = 200;
  public record Example(String matchId, int roundNumber, double[] features, int outcome) {
    public Example { features = features.clone(); }
    @Override public double[] features() { return features.clone(); }
  }
  public record Evaluation(String status, String featureVersion, String modelVersion, long splitSeed,
      String splitStrategy, int matchCount, int trainMatchCount, int testMatchCount,
      int trainRows, int testRows, Double accuracy, Double brierScore,
      Map<String,Double> classPrecision, Map<String,Double> classRecall,
      Map<String,Double> confidenceCalibration, Double falsePositiveRate, List<String> limitations) {
    public Evaluation { classPrecision=Map.copyOf(classPrecision); classRecall=Map.copyOf(classRecall); confidenceCalibration=Map.copyOf(confidenceCalibration); limitations=List.copyOf(limitations); }
  }
  private final MatchIntelligenceStore store;
  private final JdbcTemplate db;
  public PreRoundOutcomeBaseline(MatchIntelligenceStore store, JdbcTemplate db) { this.store=store; this.db=db; }

  /** Extract only score state before this round, round index, and selected player's reported side. */
  public List<Example> extract(CanonicalMatch match) {
    if(match==null||match.selectedPlayerTeamId()==null) return List.of();
    String own=match.selectedPlayerTeamId();
    Map<Integer,PlayerRound> selectedRounds=new HashMap<>();
    for(PlayerRound row:match.playerRounds()) if(row.identity()!=null&&row.identity().stableKey().equalsIgnoreCase(match.selectedPlayerIdentity())) selectedRounds.put(row.roundNumber(),row);
    List<Round> rounds=match.rounds().stream().sorted(Comparator.comparingInt(Round::roundNumber)).toList();
    List<Example> result=new ArrayList<>(); int ownWins=0, oppWins=0, completed=0;
    for(Round round:rounds) {
      PlayerRound row=selectedRounds.get(round.roundNumber());
      String sideName=row==null?"":row.side().toUpperCase(Locale.ROOT);
      double side=(sideName.equals("ATTACK")||sideName.equals("ATTACKER"))?1.0:(sideName.equals("DEFENSE")||sideName.equals("DEFENDER"))?-1.0:Double.NaN;
      if(round.winningTeamId()!=null&&Double.isFinite(side)) {
        // The score feature is read before updating with this round's result.
        double priorDiff=completed==0?0.0:(ownWins-oppWins)/(double)completed;
        result.add(new Example(match.providerMatchId(),round.roundNumber(),new double[]{round.roundNumber()/30.0,priorDiff,side},round.winningTeamId().equals(own)?1:0));
      }
      if(round.winningTeamId()!=null) { if(round.winningTeamId().equals(own)) ownWins++; else oppWins++; completed++; }
    }
    return List.copyOf(result);
  }

  /** Evaluate the baseline with deterministic match-grouped split; no model is activated in the web app. */
  public Evaluation evaluate(List<CanonicalMatch> matches) {
    Map<String,CanonicalMatch> unique=new TreeMap<>();
    if(matches!=null) for(CanonicalMatch match:matches) if(match!=null) unique.putIfAbsent(match.providerMatchId(),match);
    List<String> ids=new ArrayList<>(unique.keySet()); ids.sort(Comparator.comparingLong(id->groupOrder(id,SPLIT_SEED)));
    int testGroups=Math.max(1,(int)Math.ceil(ids.size()*0.2));
    Set<String> testIds=new HashSet<>(ids.subList(0,Math.min(testGroups,ids.size())));
    List<Example> all=new ArrayList<>(); unique.values().forEach(m->all.addAll(extract(m)));
    List<Example> train=all.stream().filter(e->!testIds.contains(e.matchId())).toList();
    List<Example> test=all.stream().filter(e->testIds.contains(e.matchId())).toList();
    List<String> limits=new ArrayList<>(List.of("Outcome prediction is not causal analysis and does not identify a mistake.","Only pre-round score state, normalized round index, and reported side are features; no current/future round outcome, event timeline or final match score is used.","Matches are the split unit so rounds or selected-player perspectives from one match cannot cross train/test."));
    if(ids.size()<MIN_MATCHES||all.size()<MIN_ROWS||test.isEmpty()||train.isEmpty()) {
      limits.add("Insufficient data for evaluation: require at least "+MIN_MATCHES+" distinct matches and "+MIN_ROWS+" supported round examples; no model is activated.");
      return new Evaluation("INSUFFICIENT_EVIDENCE",FEATURE_VERSION,MODEL_VERSION,SPLIT_SEED,"deterministic SHA-256 match-group holdout",ids.size(),ids.size()-testIds.size(),testIds.size(),train.size(),test.size(),null,null,Map.of(),Map.of(),Map.of(),null,limits);
    }
    Logistic model=fit(train); List<Double> probabilities=test.stream().map(e->model.predict(e.features())).toList();
    int tp=0,fp=0,tn=0,fn=0;double brier=0;int[][] bins=new int[5][2];double[] confidence=new double[5];
    for(int i=0;i<test.size();i++){Example e=test.get(i);double p=probabilities.get(i);int pred=p>=0.5?1:0;if(e.outcome()==1&&pred==1)tp++;else if(e.outcome()==0&&pred==1)fp++;else if(e.outcome()==0)tn++;else fn++;brier+=(p-e.outcome())*(p-e.outcome());int b=Math.min(4,(int)(p*5));bins[b][0]++;bins[b][1]+=e.outcome();confidence[b]+=p;}
    Map<String,Double> precision=Map.of("round_win",tp+fp==0?0:tp/(double)(tp+fp),"round_loss",tn+fn==0?0:tn/(double)(tn+fn));
    Map<String,Double> recall=Map.of("round_win",tp+fn==0?0:tp/(double)(tp+fn),"round_loss",tn+fp==0?0:tn/(double)(tn+fp));
    Map<String,Double> calibration=new TreeMap<>();for(int b=0;b<5;b++)if(bins[b][0]>0)calibration.put(String.format(Locale.ROOT,"%d-%d%%",b*20,(b+1)*20),Math.abs(confidence[b]/bins[b][0]-bins[b][1]/(double)bins[b][0]));
    return new Evaluation("EVALUATED_BASELINE",FEATURE_VERSION,MODEL_VERSION,SPLIT_SEED,"deterministic SHA-256 match-group holdout",ids.size(),ids.size()-testIds.size(),testIds.size(),train.size(),test.size(),(tp+tn)/(double)test.size(),brier/test.size(),precision,recall,calibration,fp+tn==0?0:fp/(double)(fp+tn),limits);
  }
  public Evaluation evaluateOwnedMatches(String owner) {
    return evaluateOwnedMatches(owner, null);
  }
  public Evaluation evaluateOwnedMatches(String owner,String playerId) {
    List<String> ids=playerId==null
        ? db.query("select distinct jm.provider_match_id from mi_import_job_matches jm join mi_import_jobs j on j.job_id=jm.job_id where j.owner_username=? and jm.quality in ('COMPLETE','PARTIAL') and jm.provider_match_id is not null order by jm.provider_match_id limit 500",(rs,n)->rs.getString(1),owner)
        : db.query("select distinct jm.provider_match_id from mi_import_job_matches jm join mi_import_jobs j on j.job_id=jm.job_id where j.owner_username=? and jm.quality in ('COMPLETE','PARTIAL') and jm.provider_match_id is not null and (lower(jm.selected_player_riot_id)=lower(?) or lower(jm.selected_player_identity)=lower(?)) order by jm.provider_match_id limit 500",(rs,n)->rs.getString(1),owner,playerId,playerId);
    List<CanonicalMatch> matches=new ArrayList<>();for(String id:ids)try{matches.add(store.getMatch(owner,id,playerId).withSelectedPerspective());}catch(ResponseStatusException ignored){}
    return evaluate(matches);
  }
  private static long groupOrder(String id,long seed){try{byte[] hash=MessageDigest.getInstance("SHA-256").digest((seed+":"+id).getBytes(StandardCharsets.UTF_8));return java.nio.ByteBuffer.wrap(hash).getLong()&Long.MAX_VALUE;}catch(Exception e){throw new IllegalStateException(e);}}
  private static Logistic fit(List<Example> rows){double[] mean=new double[3],scale=new double[3];for(Example e:rows)for(int j=0;j<3;j++)mean[j]+=e.features()[j]/rows.size();for(Example e:rows)for(int j=0;j<3;j++)scale[j]+=Math.pow(e.features()[j]-mean[j],2)/rows.size();for(int j=0;j<3;j++)scale[j]=Math.sqrt(scale[j])<1e-9?1:Math.sqrt(scale[j]);double[] w=new double[4];double rate=.08,lambda=.01;for(int epoch=0;epoch<1500;epoch++){double[] grad=new double[4];for(Example e:rows){double[] f=e.features();double z=w[0];for(int j=0;j<3;j++)z+=w[j+1]*(f[j]-mean[j])/scale[j];double err=sigmoid(z)-e.outcome();grad[0]+=err;for(int j=0;j<3;j++)grad[j+1]+=err*(f[j]-mean[j])/scale[j];}for(int j=0;j<4;j++)w[j]-=rate*(grad[j]/rows.size()+(j==0?0:lambda*w[j]));}return new Logistic(w,mean,scale);}
  private static double sigmoid(double x){if(x>35)return 1;if(x< -35)return 0;return 1/(1+Math.exp(-x));}
  private record Logistic(double[] weights,double[] means,double[] scales){double predict(double[] f){double z=weights[0];for(int j=0;j<f.length;j++)z+=weights[j+1]*(f[j]-means[j])/scales[j];return sigmoid(z);}}
}
