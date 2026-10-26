package com.valorant.leaderboard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Disabled-by-default preparation for the Act Trends data foundation.
 * Do not enable or invoke until the historical archive has finished and its
 * additive schema migration has been deliberately applied.
 */
@Service
@ConditionalOnProperty(prefix="leaderboard.historical.trends",name="enabled",havingValue="true")
public class HistoricalTrendFoundationService {
  private final JdbcTemplate db;
  private final ObjectMapper mapper;
  private final TransactionTemplate tx;

  public HistoricalTrendFoundationService(JdbcTemplate db,ObjectMapper mapper,PlatformTransactionManager manager){
    this.db=db;this.mapper=mapper;this.tx=new TransactionTemplate(manager);
  }

  /**
   * Audits source extent and field capability without treating COMPLETE as
   * proof that the provider returned every eligible player.
   */
  public List<Map<String,Object>> auditAct(String act){
    requireHistoricalAct(act);
    ensureArchiveComplete();
    var rows=db.queryForList("""
      select s.source_region,s.status,s.pages,s.players,
        (select hp.page from historical_act_pages hp where hp.act=s.act and hp.source_region=s.source_region order by hp.page desc limit 1) last_page,
        (select hp.rows from historical_act_pages hp where hp.act=s.act and hp.source_region=s.source_region order by hp.page desc limit 1) last_page_rows,
        count(p.player_key) fetched_rows,
        count(distinct lower(p.player_key)) distinct_entries,
        count(p.player_key) filter(where p.country is not null) known_country_rows,
        count(p.player_key) filter(where p.country is null) unknown_country_rows,
        min(p.source_rank) first_rank,max(p.source_rank) last_rank,
        count(p.player_key) filter(where regexp_replace(lower(p.tier),'[^a-z0-9]','','g')='immortal1') i1,
        count(p.player_key) filter(where regexp_replace(lower(p.tier),'[^a-z0-9]','','g')='immortal2') i2,
        count(p.player_key) filter(where regexp_replace(lower(p.tier),'[^a-z0-9]','','g')='immortal3') i3,
        count(p.player_key) filter(where regexp_replace(lower(p.tier),'[^a-z0-9]','','g')='immortal') combined_immortal
      from historical_act_sources s
      left join historical_act_players p on p.act=s.act and p.source_region=s.source_region
      where s.act=? group by s.act,s.source_region,s.status,s.pages,s.players
      order by case when s.source_region='global' then 0 else 1 end,s.source_region
        """,act);

    for(var row:rows){
      String source=Objects.toString(row.get("source_region"));
      long i1=number(row.get("i1")),i2=number(row.get("i2")),i3=number(row.get("i3")),combined=number(row.get("combined_immortal"));
      String capability=(combined>0&&i1+i2+i3==0)?"COMBINED_ONLY":(i1+i2+i3>0?"SUBDIVISIONS_PRESENT":"UNKNOWN");
      long fetched=number(row.get("fetched_rows"));
      boolean terminal="COMPLETE".equals(row.get("status"))&&row.get("last_page_rows") instanceof Number n&&n.longValue()<100;
      var warnings=new ArrayList<String>();
      if(!"COMPLETE".equals(row.get("status")))warnings.add("SOURCE_NOT_TERMINAL");
      else if(!terminal)warnings.add("TERMINAL_PAGE_NOT_VERIFIED");
      if("global".equals(source))warnings.add("GLOBAL_IS_AUDIT_SOURCE_NOT_ADDITIVE_TO_REGIONAL_COUNTS");
      if(fetched==5000||fetched==15000)warnings.add("POSSIBLE_SOURCE_CAP_NOT_VERIFIED");
      if("COMBINED_ONLY".equals(capability))warnings.add("IMMORTAL_SUBDIVISIONS_UNAVAILABLE");
      long matched=0,conflicts=0;
      if(!"global".equals(source)){
        var comparison=db.queryForMap("""
          select count(g.player_key) matched,
            count(g.player_key) filter(where g.rr<>p.rr or lower(g.tier)<>lower(p.tier)
              or coalesce(g.country,'')<>coalesce(p.country,'')) conflicting
          from historical_act_players p left join historical_act_players g
            on g.act=p.act and g.source_region='global' and g.player_region=p.player_region
            and lower(g.player_key)=lower(p.player_key)
          where p.act=? and p.source_region=?
          """,act,source);
        matched=number(comparison.get("matched"));conflicts=number(comparison.get("conflicting"));
        if(conflicts>0)warnings.add("GLOBAL_AND_REGIONAL_FIELDS_DIVERGE");
      }
      int version=Optional.ofNullable(db.queryForObject("select coalesce(max(audit_version),0)+1 from historical_source_audits where act=? and source_region=?",Integer.class,act,source)).orElse(1);
      db.update("""
        insert into historical_source_audits(act,source_region,audit_version,source_status,pagination_terminal,source_pages,last_page,terminal_page_rows,
          fetched_rows,distinct_entries,first_rank,last_rank,suspected_cap,tier_capability,
          known_country_rows,unknown_country_rows,matched_global_rows,conflicting_global_rows,warnings)
        values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb)
        """,act,source,version,row.get("status"),terminal,number(row.get("pages")),row.get("last_page"),row.get("last_page_rows"),fetched,number(row.get("distinct_entries")),
        row.get("first_rank"),row.get("last_rank"),(fetched==5000||fetched==15000)?"POSSIBLE_NOT_VERIFIED":null,
        capability,number(row.get("known_country_rows")),number(row.get("unknown_country_rows")),matched,conflicts,json(warnings));
      row.put("pagination_terminal",terminal);row.put("tier_capability",capability);row.put("matched_global_rows",matched);row.put("conflicting_global_rows",conflicts);row.put("warnings",warnings);
    }
    return rows;
  }

  /**
   * Builds one act's canonical regional facts, aggregates, and a new immutable
   * publication manifest. This method refuses to run while any historical
   * source is incomplete; the bean itself remains disabled by default.
   */
  public Map<String,Object> publishAct(String act){
    requireHistoricalAct(act);
    return tx.execute(status->{
      ensureArchiveComplete();
      var audits=auditAct(act);
      String fingerprint=fingerprint(act);
      var included=new ArrayList<String>();
      for(var audit:audits)if(!"global".equals(audit.get("source_region"))&&Boolean.TRUE.equals(audit.get("pagination_terminal")))included.add(audit.get("source_region").toString());
      var quality=new LinkedHashMap<String,Object>();quality.put("sourceCount",audits.size());quality.put("includedRegions",included);quality.put("captureSemantics","ARCHIVED_STANDINGS");
      UUID actRevision=UUID.randomUUID();
      db.update("insert into historical_act_data_revisions(id,act,input_fingerprint,normalization_version,status,included_regions,quality_summary) values(?,?,?,1,'BUILDING',?::jsonb,?::jsonb)",actRevision,act,fingerprint,json(included),json(quality));

      db.update("""
        insert into historical_canonical_entries(act_revision_id,act,player_shard,entry_key,display_name,country,country_provenance,
          raw_tier,normalized_tier,rr,wins,regional_rank,provider_global_rank,global_rr,global_tier,conflict_flags,identity_quality,
          regional_fetched_at,global_fetched_at)
        select ?,p.act,p.player_region,lower(p.player_key),p.display_name,p.country,
          case when p.country is null then 'UNKNOWN' else 'REPORTED_IN_ARCHIVE_SOURCE' end,
          p.tier,
          case regexp_replace(lower(p.tier),'[^a-z0-9]','','g')
            when 'radiant' then 'RADIANT' when 'immortal3' then 'IMMORTAL_3'
            when 'immortal2' then 'IMMORTAL_2' when 'immortal1' then 'IMMORTAL_1'
            when 'immortal' then 'IMMORTAL_COMBINED' else 'UNCLASSIFIED' end,
          p.rr,p.wins,p.source_rank,g.source_rank,g.rr,g.tier,
          to_jsonb(array_remove(array[
            case when g.player_key is not null and g.rr<>p.rr then 'GLOBAL_RR_CONFLICT' end,
            case when g.player_key is not null and lower(g.tier)<>lower(p.tier) then 'GLOBAL_TIER_CONFLICT' end,
            case when g.player_key is not null and coalesce(g.country,'')<>coalesce(p.country,'') then 'GLOBAL_COUNTRY_CONFLICT' end
          ]::text[],null)),
          'NAME_KEY',p.fetched_at,g.fetched_at
        from historical_act_players p
        left join historical_act_players g on g.act=p.act and g.source_region='global'
          and g.player_region=p.player_region and lower(g.player_key)=lower(p.player_key)
        where p.act=? and p.source_region=p.player_region
        on conflict(act_revision_id,player_shard,entry_key) do nothing
        """,actRevision,act);

      db.update("""
        insert into historical_trend_aggregates(act_revision_id,act,player_shard,country,normalized_tier,cohort,
          observed_count,known_country_count,unknown_country_count,rr_sample_count,median_rr,p75_rr,
          wins_sample_count,median_act_wins,p75_act_wins,flagged_count)
        select act_revision_id,act,player_shard,coalesce(country,'unknown'),normalized_tier,'ALL_OBSERVED',count(*),
          count(*) filter(where country is not null),count(*) filter(where country is null),count(rr),
          percentile_cont(0.5) within group(order by rr),percentile_cont(0.75) within group(order by rr),
          count(wins),percentile_cont(0.5) within group(order by wins),percentile_cont(0.75) within group(order by wins),
          count(*) filter(where conflict_flags<>'[]'::jsonb)
        from historical_canonical_entries where act_revision_id=?
        group by act_revision_id,act,player_shard,coalesce(country,'unknown'),normalized_tier
        """,actRevision);

      Long canonical=db.queryForObject("select count(*) from historical_canonical_entries where act_revision_id=?",Long.class,actRevision);
      Long aggregated=db.queryForObject("select coalesce(sum(observed_count),0) from historical_trend_aggregates where act_revision_id=?",Long.class,actRevision);
      if(!Objects.equals(canonical,aggregated))throw new IllegalStateException("Canonical-to-aggregate row reconciliation failed.");
      db.update("update historical_act_data_revisions set status='READY',ready_at=now() where id=?",actRevision);

      UUID publication=UUID.randomUUID();
      List<UUID> current=db.query("select publication_id from historical_trend_current_publication where singleton=true",(rs,row)->rs.getObject(1,UUID.class));
      UUID parent=current.isEmpty()?null:current.get(0);
      db.update("insert into historical_trend_publications(id,parent_id,status,formula_version) values(?,?,'STAGING','observed-v1')",publication,parent);
      if(parent!=null)db.update("insert into historical_trend_publication_acts(publication_id,act,act_revision_id) select ?,act,act_revision_id from historical_trend_publication_acts where publication_id=? and act<>?",publication,parent,act);
      db.update("insert into historical_trend_publication_acts(publication_id,act,act_revision_id) values(?,?,?)",publication,act,actRevision);
      db.update("update historical_trend_publications set status='PUBLISHED',published_at=now() where id=?",publication);
      db.update("insert into historical_trend_current_publication(singleton,publication_id) values(true,?) on conflict(singleton) do update set publication_id=excluded.publication_id,updated_at=now()",publication);
      return Map.of("act",act,"actRevision",actRevision.toString(),"publication",publication.toString(),"canonicalRows",canonical==null?0:canonical,"includedRegions",included,"status","PUBLISHED");
    });
  }

  private void requireHistoricalAct(String act){
    if(act==null||!act.matches("[a-fA-F0-9-]{36}")||!Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from historical_act_catalog where act=? and current_act=false)",Boolean.class,act)))
      throw new IllegalArgumentException("Unknown historical act.");
  }

  private void ensureArchiveComplete(){
    Long incomplete=db.queryForObject("""
      select count(*) from (
        select c.act from historical_act_catalog c left join historical_act_sources s using(act)
        left join historical_act_pages p on p.act=s.act and p.source_region=s.source_region and p.page=s.pages
        where c.current_act=false group by c.act
        having count(s.source_region)<>7 or count(*) filter(where s.source_region is not null and
          (s.status<>'COMPLETE' or p.page is null or p.status<>'COMPLETE' or p.rows>=100))>0
      ) incomplete_acts
      """,Long.class);
    if(incomplete!=null&&incomplete>0)throw new IllegalStateException("Historical extraction is still active or incomplete; trend audit and publication are deferred.");
  }

  private String fingerprint(String act){
    var sources=db.queryForList("select source_region,status,pages,players,source_updated_at from historical_act_sources where act=? order by source_region",act);
    try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(sources)));}
    catch(Exception e){throw new IllegalStateException("Could not fingerprint archive inputs",e);}
  }

  private String json(Object value){try{return mapper.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException("Could not serialize trend provenance",e);}}
  private long number(Object value){return value instanceof Number n?n.longValue():0;}
}
