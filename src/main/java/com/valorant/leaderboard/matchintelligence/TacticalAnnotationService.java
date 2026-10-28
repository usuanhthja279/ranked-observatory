package com.valorant.leaderboard.matchintelligence;

import static com.valorant.leaderboard.matchintelligence.CanonicalMatch.*;

import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Per-account private expert labels with append-only corrections and withdrawals. */
@Service
public class TacticalAnnotationService {
  public static final String GUIDELINE_VERSION="tactical-review-guidelines-v1";
  public record Input(int roundNumber,String eventId,Long eventTimeRaw,String judgment,String reason,
      String betterAlternative,Boolean insufficientEvidence,Integer confidence,String vodUrl,String disagreementGroup) {}
  public record Annotation(String annotationId,int revision,String providerMatchId,int roundNumber,
      String eventId,Long eventTimeRaw,String judgment,String reason,String betterAlternative,
      boolean insufficientEvidence,int confidence,String vodUrl,String guidelineVersion,String status,
      String disagreementGroup,String annotator,Instant createdAt) {}
  private final JdbcTemplate db; private final MatchIntelligenceStore store;
  public TacticalAnnotationService(JdbcTemplate db,MatchIntelligenceStore store){this.db=db;this.store=store;}

  @Transactional
  public Annotation create(String owner,String matchId,Input input){
    MatchIntelligenceStore.MatchView view=store.getMatch(owner,matchId); validate(view,input);
    String id=UUID.randomUUID().toString();
    insert(id,1,owner,matchId,input,"ACTIVE"); return getRevision(owner,id,1);
  }
  public List<Annotation> list(String owner,String matchId){
    store.getMatch(owner,matchId);
    return db.query("select a.* from mi_tactical_annotations a join (select annotation_id,max(revision) revision from mi_tactical_annotations where owner_username=? and provider='tracker.gg' and provider_match_id=? group by annotation_id) h on h.annotation_id=a.annotation_id and h.revision=a.revision where a.owner_username=? order by a.created_at desc,a.annotation_id",this::map,owner,matchId,owner);
  }
  @Transactional
  public Annotation withdraw(String owner,String id,int expectedRevision){
    List<Annotation> latest=db.query("select * from mi_tactical_annotations where owner_username=? and annotation_id=? order by revision desc limit 1 for update",this::map,owner,id);
    if(latest.isEmpty())throw notFound(); Annotation previous=latest.getFirst();
    if(previous.revision()!=expectedRevision)throw new ResponseStatusException(HttpStatus.CONFLICT,"This annotation changed since it was opened. Reload before withdrawing it.");
    if(previous.status().equals("WITHDRAWN"))return previous;
    Input retained=new Input(previous.roundNumber(),previous.eventId(),previous.eventTimeRaw(),previous.judgment(),previous.reason(),previous.betterAlternative(),previous.insufficientEvidence(),previous.confidence(),previous.vodUrl(),previous.disagreementGroup());
    store.getMatch(owner,previous.providerMatchId());
    insert(id,expectedRevision+1,owner,previous.providerMatchId(),retained,"WITHDRAWN");
    return getRevision(owner,id,expectedRevision+1);
  }

  @Transactional
  public Annotation correct(String owner,String id,int expectedRevision,Input input,boolean withdrawn){
    List<Annotation> latest=db.query("select * from mi_tactical_annotations where owner_username=? and annotation_id=? order by revision desc limit 1 for update",this::map,owner,id);
    if(latest.isEmpty())throw notFound(); Annotation previous=latest.getFirst();
    if(previous.revision()!=expectedRevision)throw new ResponseStatusException(HttpStatus.CONFLICT,"This annotation changed since it was opened. Reload before correcting it.");
    MatchIntelligenceStore.MatchView view=store.getMatch(owner,previous.providerMatchId()); validate(view,input);
    insert(id,expectedRevision+1,owner,previous.providerMatchId(),input,withdrawn?"WITHDRAWN":"ACTIVE");
    return getRevision(owner,id,expectedRevision+1);
  }
  private void validate(MatchIntelligenceStore.MatchView view,Input in){
    if(in==null)throw new IllegalArgumentException("Annotation details are required.");
    if(in.roundNumber()<1||view.match().rounds().stream().noneMatch(r->r.roundNumber()==in.roundNumber()))throw new IllegalArgumentException("Select a round present in this match.");
    if(in.eventId()!=null&&view.match().events().stream().noneMatch(e->e.roundNumber()==in.roundNumber()&&e.eventId().equals(in.eventId())))throw new IllegalArgumentException("The selected event does not belong to this match and round.");
    Set<String> labels=Set.of("POSITIONING","OPENING","TRADE","UTILITY","ECONOMY","OBJECTIVE","TEAMPLAY","OTHER","INSUFFICIENT_EVIDENCE");
    if(in.judgment()==null||!labels.contains(in.judgment()))throw new IllegalArgumentException("Choose a supported tactical judgment.");
    if(in.reason()==null||in.reason().isBlank()||in.reason().length()>4000)throw new IllegalArgumentException("Add a review reason up to 4,000 characters.");
    if(in.betterAlternative()!=null&&in.betterAlternative().length()>4000)throw new IllegalArgumentException("The alternative must be at most 4,000 characters.");
    if(in.confidence()==null||in.confidence()<0||in.confidence()>100)throw new IllegalArgumentException("Confidence must be between 0 and 100.");
    if(in.insufficientEvidence()==null)throw new IllegalArgumentException("Mark whether evidence is insufficient.");
    if(in.vodUrl()!=null&&!in.vodUrl().isBlank()){if(in.vodUrl().length()>1200)throw new IllegalArgumentException("VOD URL is too long.");try{URI uri=URI.create(in.vodUrl());if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null)throw new IllegalArgumentException("VOD references must be HTTPS links. They are stored only and never fetched.");}catch(IllegalArgumentException e){throw new IllegalArgumentException("VOD references must be HTTPS links. They are stored only and never fetched.");}}
    if(in.disagreementGroup()!=null&&!in.disagreementGroup().matches("[A-Za-z0-9_-]{1,100}"))throw new IllegalArgumentException("Disagreement group must be a short opaque label.");
  }
  private void insert(String id,int rev,String owner,String match,Input in,String state){
    db.update("insert into mi_tactical_annotations(annotation_id,revision,owner_username,provider,provider_match_id,round_number,event_id,event_time_raw,judgment,reason,better_alternative,insufficient_evidence,confidence,vod_url,guideline_version,status,disagreement_group) values(?,?,?,'tracker.gg',?,?,?,?,?,?,?,?,?,?,?,?,?)",
        id,rev,owner,match,in.roundNumber(),in.eventId(),in.eventTimeRaw(),in.judgment(),in.reason().trim(),blankNull(in.betterAlternative()),in.insufficientEvidence(),in.confidence(),blankNull(in.vodUrl()),GUIDELINE_VERSION,state,blankNull(in.disagreementGroup()));
  }
  private Annotation getRevision(String owner,String id,int rev){return db.queryForObject("select * from mi_tactical_annotations where owner_username=? and annotation_id=? and revision=?",this::map,owner,id,rev);}
  private Annotation map(ResultSet rs,int n)throws SQLException{Timestamp ts=rs.getTimestamp("created_at");return new Annotation(rs.getString("annotation_id"),rs.getInt("revision"),rs.getString("provider_match_id"),rs.getInt("round_number"),rs.getString("event_id"),(Long)rs.getObject("event_time_raw"),rs.getString("judgment"),rs.getString("reason"),rs.getString("better_alternative"),rs.getBoolean("insufficient_evidence"),rs.getInt("confidence"),rs.getString("vod_url"),rs.getString("guideline_version"),rs.getString("status"),rs.getString("disagreement_group"),rs.getString("owner_username"),ts==null?null:ts.toInstant());}
  private ResponseStatusException notFound(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Annotation was not found for this account.");}
  private String blankNull(String s){return s==null||s.isBlank()?null:s.trim();}
}
