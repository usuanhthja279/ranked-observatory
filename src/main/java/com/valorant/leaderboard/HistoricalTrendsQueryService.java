package com.valorant.leaderboard;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Read-only Act Trends queries over completed regional rows in the V7 archive. */
@Service
public class HistoricalTrendsQueryService {
  private static final int MAX_RANGE_ACTS = 36;
  private final JdbcTemplate db;

  public HistoricalTrendsQueryService(JdbcTemplate db){this.db=db;}

  /** An act is eligible only when all six regional ladders reached a short terminal page. */
  public List<Map<String,Object>> catalog(){
    return db.queryForList("""
      select c.act,c.label,c.episode,c.act_order,c.current_act,c.threshold_profile,
        count(distinct s.source_region) filter(where s.source_region in ('ap','eu','na','latam','br','kr')
          and s.status='COMPLETE' and terminal.page is not null and terminal.status='COMPLETE' and terminal.rows<100) completed_regions,
        count(distinct s.source_region) filter(where s.source_region='global' and s.status='COMPLETE') global_source_complete,
        max(s.completed_at) filter(where s.source_region in ('ap','eu','na','latam','br','kr')) completed_at
      from historical_act_catalog c
      left join historical_act_sources s on s.act=c.act
      left join historical_act_pages terminal on terminal.act=s.act and terminal.source_region=s.source_region and terminal.page=s.pages
      where (c.current_act=false or exists(select 1 from global_leaderboard_runs r where r.act=c.act and r.status='COMPLETE'))
      group by c.act,c.label,c.episode,c.act_order,c.current_act,c.threshold_profile
      having c.current_act=true or count(distinct s.source_region) filter(where s.source_region in ('ap','eu','na','latam','br','kr')
        and s.status='COMPLETE' and terminal.page is not null and terminal.status='COMPLETE' and terminal.rows<100)=6
      order by c.current_act,c.act_order
      """);
  }

  /** Exact Immortal tier coverage in each completed act and regional archive. */
  public Map<String,Object> capabilities(){
    var acts=catalog();acts.removeIf(a->Boolean.TRUE.equals(a.get("current_act")));
    if(acts.isEmpty())return Map.of("acts",List.of(),"rows",List.of());
    var ids=acts.stream().map(a->a.get("act").toString()).toList();
    String marks=String.join(",",Collections.nCopies(ids.size(),"?"));
    var rows=db.queryForList("""
      with tier_rows as (
        select p.act,p.player_region region,p.rr,c.act_order,c.threshold_profile,
          case regexp_replace(lower(p.tier),'[^a-z0-9]','','g')
            when 'immortal' then 'COMBINED'
            when 'immortal1' then 'I1' when 'immortal2' then 'I2' when 'immortal3' then 'I3'
            else 'OTHER' end tier
        from historical_act_players p join historical_act_catalog c on c.act=p.act
        where p.act in (%s) and p.source_region=p.player_region
      ), counts as (
        select act,region,count(*) filter(where tier='COMBINED') combined_immortal,
          count(*) filter(where tier in ('I1','I2','I3')) split_immortal,
          count(*) filter(where tier='I1') immortal1,count(*) filter(where tier='I2') immortal2,
          count(*) filter(where tier='I3') immortal3,
          count(*) filter(where tier='I1' or (tier='COMBINED' and rr<i2_rr)) estimated_immortal1,
          count(*) filter(where tier='I2' or (tier='COMBINED' and rr>=i2_rr and rr<i3_rr)) estimated_immortal2,
          count(*) filter(where tier='I3' or (tier='COMBINED' and rr>=i3_rr)) estimated_immortal3,
          max(threshold_profile) threshold_profile,
          max(i2_rr) immortal2_threshold_rr,max(i3_rr) immortal3_threshold_rr
        from (
          select t.*,
            case when threshold_profile='V26_ACT_II_PLUS' then case when region in ('latam','kr') then 90 else 100 end else case region when 'ap' then 80 when 'eu' then 100 when 'br' then 100 else 90 end end i2_rr,
            case when threshold_profile='V26_ACT_II_PLUS' then case region when 'latam' then 150 when 'kr' then 150 else 200 end
              else case region when 'br' then 230 when 'latam' then 150 when 'kr' then 150 else 200 end end i3_rr
          from tier_rows t
        ) thresholded group by act,region,threshold_profile
      )
      select c.act,c.label,c.act_order,x.region,x.combined_immortal,x.split_immortal,x.immortal1,x.immortal2,x.immortal3,
        x.estimated_immortal1,x.estimated_immortal2,x.estimated_immortal3,x.threshold_profile,x.immortal2_threshold_rr,x.immortal3_threshold_rr,
        case
          when x.combined_immortal>0 and x.split_immortal>0 then 'MIXED'
          when x.combined_immortal>0 then 'COMBINED_ONLY'
          when x.split_immortal>0 then 'SPLIT'
          else 'NO_IMMORTAL_ROWS'
        end tier_detail
      from historical_act_catalog c
      join counts x on x.act=c.act
      where c.act in (%s)
      order by c.act_order,x.region
      """.formatted(marks,marks),java.util.stream.Stream.concat(ids.stream(),ids.stream()).toArray());
    return Map.of("acts",acts,"rows",rows,"sourceBasis","completed_regional_archive_rows","meaning","Source tier counts remain observed. Estimated Immortal 1/2/3 include RR-based subdivisions of combined Immortal rows and must not be read as source-reported tiers.","thresholdPolicy","Use the supplied Episode 3 regional thresholds as a backcast before Episode 3; use V26 Act II thresholds from V26 Act II onward. Radiant is not inferred from RR because its cutoff also depends on leaderboard placement.");
  }

  /**
   * Produces one row per act and display entity. World scope returns six shard
   * totals; region scope returns reported countries; country scope returns its
   * shard rows. Global source records are deliberately excluded.
   */
  public Map<String,Object> series(String fromAct,String toAct,String region,String country,String cohort){
    var acts=catalog();
    if(acts.isEmpty())return Map.of("acts",List.of(),"rows",List.of(),"message","No acts have all six terminal regional ladders yet.");
    int from=indexOf(acts,fromAct),to=indexOf(acts,toAct);
    if(from<0||to<0||from>to)throw new IllegalArgumentException("Choose an ordered range from the completed-act list.");
    validateRangeSize(from,to);
    var selected=acts.subList(from,to+1);
    var ids=selected.stream().map(a->a.get("act").toString()).toList();
    String marks=String.join(",",Collections.nCopies(ids.size(),"?"));
    boolean world=region.equals("all")&&country.equals("all");
    String groupSql=world?"""
      select act,region,'all' country,sum(players) players,
        sum(radiant) radiant,sum(immortal_all) immortal_all,sum(immortal3) immortal3,sum(immortal2) immortal2,sum(immortal1) immortal1,
        sum(estimated_immortal3) estimated_immortal3,sum(estimated_immortal2) estimated_immortal2,sum(estimated_immortal1) estimated_immortal1,
        sum(unknown_country_players) unknown_country_players,
        sum(unknown_country_radiant) unknown_country_radiant,sum(unknown_country_immortal_all) unknown_country_immortal_all,
        sum(unknown_country_immortal3) unknown_country_immortal3,sum(unknown_country_immortal2) unknown_country_immortal2,sum(unknown_country_immortal1) unknown_country_immortal1,
        sum(unknown_country_estimated_immortal3) unknown_country_estimated_immortal3,sum(unknown_country_estimated_immortal2) unknown_country_estimated_immortal2,sum(unknown_country_estimated_immortal1) unknown_country_estimated_immortal1,
        max(region_known_players) region_known_players,max(region_unknown_players) region_unknown_players,
        max(region_known_radiant) region_known_radiant,max(region_unknown_radiant) region_unknown_radiant,
        max(region_known_immortal_all) region_known_immortal_all,max(region_unknown_immortal_all) region_unknown_immortal_all,
        max(region_known_immortal3) region_known_immortal3,max(region_unknown_immortal3) region_unknown_immortal3,
        max(region_known_immortal2) region_known_immortal2,max(region_unknown_immortal2) region_unknown_immortal2,
        max(region_known_immortal1) region_known_immortal1,max(region_unknown_immortal1) region_unknown_immortal1,
        max(region_known_estimated_immortal3) region_known_estimated_immortal3,max(region_unknown_estimated_immortal3) region_unknown_estimated_immortal3,
        max(region_known_estimated_immortal2) region_known_estimated_immortal2,max(region_unknown_estimated_immortal2) region_unknown_estimated_immortal2,
        max(region_known_estimated_immortal1) region_known_estimated_immortal1,max(region_unknown_estimated_immortal1) region_unknown_estimated_immortal1,
        bool_and(has_subtiers) has_subtiers,bool_or(has_combined_immortal) has_combined_immortal,
        null::numeric median_rr
      from country_rows group by act,region order by act,region
      """: """
      select * from country_rows where %s order by act,region,country
      """;
    int depth=cohortLimit(cohort);var args=new ArrayList<Object>(ids);
    // These values are validated by the controller (region/country) or selected from a fixed cohort
    // enum (depth). Rendering the optional predicates avoids JDBC type inference ambiguity for
    // `? = 'all'` when this query's UNION mixes archived and current-act sources.
    String historicalRegionFilter=region.equals("all")?"true":"p.player_region='"+region+"'";
    String currentRegionFilter=region.equals("all")?"true":"p.region='"+region+"'";
    String historicalDepthFilter=depth==0?"true":"p.source_rank<="+depth;
    String currentDepthFilter=depth==0?"true":"p.regional_rank<="+depth;
    String countryFilter=world||country.equals("all")?"true":"country='"+country+"'";
    if(!world)groupSql=groupSql.formatted(countryFilter);
    String sql="""
      with complete_acts as (select act,act_order,current_act,threshold_profile from historical_act_catalog where act in (%s)),
      base as (
        select p.act,p.player_region region,coalesce(p.country,'unknown') country,p.rr,
          case regexp_replace(lower(p.tier),'[^a-z0-9]','','g')
            when 'radiant' then 'RADIANT' when 'immortal3' then 'IMMORTAL_3'
            when 'immortal2' then 'IMMORTAL_2' when 'immortal1' then 'IMMORTAL_1'
            when 'immortal' then 'IMMORTAL_COMBINED' else 'UNCLASSIFIED' end tier,
          case when a.threshold_profile='V26_ACT_II_PLUS' then case when p.player_region in ('latam','kr') then 90 else 100 end else case p.player_region when 'ap' then 80 when 'eu' then 100 when 'br' then 100 else 90 end end i2_rr,
          case when a.threshold_profile='V26_ACT_II_PLUS' then case p.player_region when 'latam' then 150 when 'kr' then 150 else 200 end
            else case p.player_region when 'br' then 230 when 'latam' then 150 when 'kr' then 150 else 200 end end i3_rr,
          a.threshold_profile
        from historical_act_players p join complete_acts a on a.act=p.act and not a.current_act
        where p.source_region=p.player_region and %s and %s
        union all
        select run.act,p.region,coalesce(p.country,'unknown'),p.rr,
          case regexp_replace(lower(p.tier),'[^a-z0-9]','','g')
            when 'radiant' then 'RADIANT' when 'immortal3' then 'IMMORTAL_3'
            when 'immortal2' then 'IMMORTAL_2' when 'immortal1' then 'IMMORTAL_1'
            when 'immortal' then 'IMMORTAL_COMBINED' else 'UNCLASSIFIED' end,
          case when a.threshold_profile='V26_ACT_II_PLUS' then case when p.region in ('latam','kr') then 90 else 100 end else case p.region when 'ap' then 80 when 'eu' then 100 when 'br' then 100 else 90 end end,
          case when a.threshold_profile='V26_ACT_II_PLUS' then case when p.region='latam' then 150 when p.region='kr' then 150 else 200 end else case when p.region='br' then 230 when p.region in ('latam','kr') then 150 else 200 end end,
          a.threshold_profile
        from global_leaderboard_players p join global_leaderboard_runs run on run.id=p.run_id
          join complete_acts a on a.act=run.act and a.current_act
        where run.id=(select max(r2.id) from global_leaderboard_runs r2 where r2.act=run.act and r2.status='COMPLETE')
          and %s and %s
      ), country_counts as (
        select act,region,country,count(*) players,
          count(*) filter(where tier='RADIANT') radiant,
          count(*) filter(where tier like 'IMMORTAL%%') immortal_all,
          count(*) filter(where tier='IMMORTAL_3') immortal3,
          count(*) filter(where tier='IMMORTAL_2') immortal2,
          count(*) filter(where tier='IMMORTAL_1') immortal1,
          count(*) filter(where tier='IMMORTAL_3' or (tier='IMMORTAL_COMBINED' and rr>=i3_rr)) estimated_immortal3,
          count(*) filter(where tier='IMMORTAL_2' or (tier='IMMORTAL_COMBINED' and rr>=i2_rr and rr<i3_rr)) estimated_immortal2,
          count(*) filter(where tier='IMMORTAL_1' or (tier='IMMORTAL_COMBINED' and rr<i2_rr)) estimated_immortal1,
          count(*) filter(where country='unknown') unknown_country_players,
          count(*) filter(where country='unknown' and tier='RADIANT') unknown_country_radiant,
          count(*) filter(where country='unknown' and tier like 'IMMORTAL%%') unknown_country_immortal_all,
          count(*) filter(where country='unknown' and tier='IMMORTAL_3') unknown_country_immortal3,
          count(*) filter(where country='unknown' and tier='IMMORTAL_2') unknown_country_immortal2,
          count(*) filter(where country='unknown' and tier='IMMORTAL_1') unknown_country_immortal1,
          count(*) filter(where country='unknown' and (tier='IMMORTAL_3' or (tier='IMMORTAL_COMBINED' and rr>=i3_rr))) unknown_country_estimated_immortal3,
          count(*) filter(where country='unknown' and (tier='IMMORTAL_2' or (tier='IMMORTAL_COMBINED' and rr>=i2_rr and rr<i3_rr))) unknown_country_estimated_immortal2,
          count(*) filter(where country='unknown' and (tier='IMMORTAL_1' or (tier='IMMORTAL_COMBINED' and rr<i2_rr))) unknown_country_estimated_immortal1,
          max(threshold_profile) threshold_profile,max(i2_rr) immortal2_threshold_rr,max(i3_rr) immortal3_threshold_rr,
          percentile_cont(0.5) within group(order by rr) median_rr
        from base group by act,region,country
      ), region_counts as (
        select act,region,
          count(*) filter(where country<>'unknown') region_known_players,count(*) filter(where country='unknown') region_unknown_players,
          count(*) filter(where country<>'unknown' and tier='RADIANT') region_known_radiant,count(*) filter(where country='unknown' and tier='RADIANT') region_unknown_radiant,
          count(*) filter(where country<>'unknown' and tier like 'IMMORTAL%%') region_known_immortal_all,count(*) filter(where country='unknown' and tier like 'IMMORTAL%%') region_unknown_immortal_all,
          count(*) filter(where country<>'unknown' and tier='IMMORTAL_3') region_known_immortal3,count(*) filter(where country='unknown' and tier='IMMORTAL_3') region_unknown_immortal3,
          count(*) filter(where country<>'unknown' and tier='IMMORTAL_2') region_known_immortal2,count(*) filter(where country='unknown' and tier='IMMORTAL_2') region_unknown_immortal2,
          count(*) filter(where country<>'unknown' and tier='IMMORTAL_1') region_known_immortal1,count(*) filter(where country='unknown' and tier='IMMORTAL_1') region_unknown_immortal1,
          count(*) filter(where country<>'unknown' and (tier='IMMORTAL_3' or (tier='IMMORTAL_COMBINED' and rr>=i3_rr))) region_known_estimated_immortal3,
          count(*) filter(where country='unknown' and (tier='IMMORTAL_3' or (tier='IMMORTAL_COMBINED' and rr>=i3_rr))) region_unknown_estimated_immortal3,
          count(*) filter(where country<>'unknown' and (tier='IMMORTAL_2' or (tier='IMMORTAL_COMBINED' and rr>=i2_rr and rr<i3_rr))) region_known_estimated_immortal2,
          count(*) filter(where country='unknown' and (tier='IMMORTAL_2' or (tier='IMMORTAL_COMBINED' and rr>=i2_rr and rr<i3_rr))) region_unknown_estimated_immortal2,
          count(*) filter(where country<>'unknown' and (tier='IMMORTAL_1' or (tier='IMMORTAL_COMBINED' and rr<i2_rr))) region_known_estimated_immortal1,
          count(*) filter(where country='unknown' and (tier='IMMORTAL_1' or (tier='IMMORTAL_COMBINED' and rr<i2_rr))) region_unknown_estimated_immortal1,
          (bool_or(tier in ('IMMORTAL_1','IMMORTAL_2','IMMORTAL_3')) and not bool_or(tier='IMMORTAL_COMBINED')) has_subtiers,
          bool_or(tier='IMMORTAL_COMBINED') has_combined_immortal
        from base group by act,region
      ), country_rows as (
        select c.*,r.region_known_players,r.region_unknown_players,r.region_known_radiant,r.region_unknown_radiant,
          r.region_known_immortal_all,r.region_unknown_immortal_all,r.region_known_immortal3,r.region_unknown_immortal3,
          r.region_known_immortal2,r.region_unknown_immortal2,r.region_known_immortal1,r.region_unknown_immortal1,
          r.region_known_estimated_immortal3,r.region_unknown_estimated_immortal3,r.region_known_estimated_immortal2,r.region_unknown_estimated_immortal2,
          r.region_known_estimated_immortal1,r.region_unknown_estimated_immortal1,r.has_subtiers,r.has_combined_immortal
        from country_counts c join region_counts r using(act,region)
      )
      %s
      """.formatted(marks,historicalRegionFilter,historicalDepthFilter,currentRegionFilter,currentDepthFilter,groupSql);
    // Complete acts are immutable. This read uses only the six validated regional source rows.
    var rows=db.queryForList(sql,args.toArray());
    return Map.of("acts",selected,"rows",rows,"region",region,"country",country,"cohort",cohort,"sourceBasis","six_completed_regional_ladders","captureSemantics","archived_standings","globalSourceUsed",false);
  }

  static void validateRangeSize(int fromIndex,int toIndex){
    if(toIndex-fromIndex+1>MAX_RANGE_ACTS)throw new IllegalArgumentException("The selected act range is too large.");
  }

  /** Per-account end-of-act trail, joined to the latest complete current snapshot. */
  public Map<String,Object> playerJourney(String player,String region){
    var rows=db.queryForList("""
      with trail as (
        select p.act,c.label,c.act_order,c.current_act,c.threshold_profile,p.player_region region,coalesce(p.country,'unknown') country,
          p.display_name,p.source_rank regional_rank,g.source_rank global_rank,p.rr,p.tier source_tier,p.wins,
          p.source_updated_at,'ARCHIVED ACT' source_kind
        from historical_act_players p join historical_act_catalog c on c.act=p.act
          left join historical_act_players g on g.act=p.act and g.source_region='global' and g.player_region=p.player_region and g.player_key=p.player_key
        where p.source_region=p.player_region and lower(p.player_key)=lower(?) and (?='all' or p.player_region=?)
        union all
        select r.act,c.label,c.act_order,c.current_act,c.threshold_profile,p.region,coalesce(p.country,'unknown'),p.display_name,
          p.regional_rank,p.global_rank,p.rr,p.tier,p.wins,p.source_updated_at,'LATEST CURRENT SNAPSHOT'
        from global_leaderboard_players p join global_leaderboard_runs r on r.id=p.run_id
          join historical_act_catalog c on c.act=r.act and c.current_act=true
        where r.id=(select max(r2.id) from global_leaderboard_runs r2 where r2.act=r.act and r2.status='COMPLETE')
          and lower(p.player_key)=lower(?) and (?='all' or p.region=?)
      ), normalized as (
        select t.*,
          case when regexp_replace(lower(source_tier),'[^a-z0-9]','','g')='immortal' then
            case when rr>=case when threshold_profile='V26_ACT_II_PLUS' then case when region in ('latam','kr') then 150 else 200 end else case when region='br' then 230 when region in ('latam','kr') then 150 else 200 end end then 'Immortal 3 · estimated'
                 when rr>=case when threshold_profile='V26_ACT_II_PLUS' then case when region in ('latam','kr') then 90 else 100 end else case region when 'ap' then 80 when 'eu' then 100 when 'br' then 100 else 90 end end then 'Immortal 2 · estimated'
                 else 'Immortal 1 · estimated' end
            when regexp_replace(lower(source_tier),'[^a-z0-9]','','g') in ('immortal1','immortal2','immortal3') then 'Immortal '||right(regexp_replace(lower(source_tier),'[^0-9]','','g'),1)||' · observed'
            else source_tier end interpreted_tier
      from trail t
      )
      select act,label,act_order,current_act,threshold_profile,region,country,display_name,regional_rank,global_rank,rr,source_tier,interpreted_tier,wins,source_updated_at,source_kind
      from normalized order by current_act,act_order,region
      """,player,region,region,player,region,region);
    return Map.of("player",player,"region",region,"items",rows,"actCount",rows.size(),"sourceBasis","completed archived end-of-act rows plus latest complete current snapshot","tierNote","Combined Immortal tiers are estimated from act- and region-specific RR thresholds; source tier remains separately shown. Historical archive rows are end-of-act standings, not match-by-match history.");
  }

  public Map<String,Object> players(String act,String region,String country,String cohort,int page,int size){
    if(catalog().stream().noneMatch(a->act.equals(a.get("act"))))throw new IllegalArgumentException("Player details are available only for acts with six completed regional ladders.");
    int offset=(page-1)*size,depth=cohortLimit(cohort);
    var params=new Object[]{act,region,region,depth,depth,country,country,size,offset};
    var items=db.queryForList("""
      select p.display_name,p.player_key,p.player_region region,coalesce(p.country,'unknown') country,
        p.source_rank regional_rank,p.rr,p.tier,p.tier source_tier,
        case
          when regexp_replace(lower(p.tier),'[^a-z0-9]','','g')='immortal' then
            case when (c.threshold_profile='V26_ACT_II_PLUS' and p.rr>=case when p.player_region in ('latam','kr') then 150 else 200 end) or (c.threshold_profile='EPISODE_3_BACKCAST' and p.rr>=case when p.player_region='br' then 230 when p.player_region in ('latam','kr') then 150 else 200 end) then 'Immortal 3 · estimated'
                 when (c.threshold_profile='V26_ACT_II_PLUS' and p.rr>=case when p.player_region in ('latam','kr') then 90 else 100 end) or (c.threshold_profile='EPISODE_3_BACKCAST' and p.rr>=case p.player_region when 'ap' then 80 when 'eu' then 100 when 'br' then 100 else 90 end) then 'Immortal 2 · estimated'
                 else 'Immortal 1 · estimated' end
          when regexp_replace(lower(p.tier),'[^a-z0-9]','','g') in ('immortal1','immortal2','immortal3') then 'Immortal '||right(regexp_replace(lower(p.tier),'[^0-9]','','g'),1)||' · observed'
          else p.tier end estimated_tier,
        case when regexp_replace(lower(p.tier),'[^a-z0-9]','','g')='immortal' then 'RR threshold estimate' else 'Source tier label' end tier_basis,
        p.wins,p.source_updated_at
      from historical_act_players p join historical_act_catalog c on c.act=p.act where p.act=? and p.source_region=p.player_region
        and (?='all' or p.player_region=?) and (?=0 or p.source_rank<=?) and (?='all' or coalesce(p.country,'unknown')=?)
      order by p.player_region,p.source_rank,lower(p.player_key) limit ? offset ?
      """,params);
    Long total=db.queryForObject("""
      select count(*) from historical_act_players p where p.act=? and p.source_region=p.player_region
        and (?='all' or p.player_region=?) and (?=0 or p.source_rank<=?) and (?='all' or coalesce(p.country,'unknown')=?)
      """,Long.class,act,region,region,depth,depth,country,country);
    return Map.of("act",act,"items",items,"total",total==null?0:total,"page",page,"size",size,"cohort",cohort,"sourceBasis","regional_archive_rows");
  }

  private int cohortLimit(String cohort){return switch(cohort){case "all"->0;case "top100"->100;case "top500"->500;case "top1000"->1000;default->throw new IllegalArgumentException("Unknown rank cohort");};}
  private int indexOf(List<Map<String,Object>> acts,String id){for(int i=0;i<acts.size();i++)if(acts.get(i).get("act").equals(id))return i;return -1;}
}
