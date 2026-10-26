with latest as materialized (
 select id,act,completed_at from global_leaderboard_runs where status='COMPLETE'
 and act='d816f426-48ea-f052-117f-9697a155b319' order by id desc limit 1
), current_players as materialized (
 select p.* from global_leaderboard_players p join latest l on p.run_id=l.id
), reports as (
 select h.region,h.player_key,min(h.country) country,count(distinct h.country) choices
 from global_leaderboard_players h join global_leaderboard_runs r on r.id=h.run_id
 join latest l on r.act=l.act and r.id<=l.id
 where r.status='COMPLETE' and r.completed_at>=l.completed_at-interval '7 days'
 and h.source_updated_at between l.completed_at-interval '7 days' and l.completed_at
 and h.country is not null and not h.carried_forward and not h.stats_ambiguous
 group by h.region,h.player_key
), raw_history as materialized (
 select p.region,p.player_key,p.source_updated_at observed_at,p.rr,p.tier,p.wins,p.regional_rank,
 p.rank_ambiguous,'global' source
 from global_leaderboard_players p join global_leaderboard_runs r on r.id=p.run_id
 join latest l on r.act=l.act and r.id<=l.id
 where r.status='COMPLETE' and not p.carried_forward and not p.stats_ambiguous and p.source_updated_at<=l.completed_at
 union all
 select 'ap',lower(p.player_key),p.source_updated_at,p.rr,p.tier,p.wins,p.regional_rank,
 p.rank_ambiguous,'legacy'
 from leaderboard_players p join leaderboard_runs r on r.id=p.run_id join latest l on r.act=l.act
 where r.status='COMPLETE' and p.source_updated_at<=l.completed_at
), deduplicated as materialized (
 select region,player_key,observed_at,min(rr) rr,min(tier) tier,min(wins) wins,
 min(regional_rank) filter(where not rank_ambiguous) regional_rank,
 bool_or(source='global') has_global
 from raw_history group by region,player_key,observed_at
 having count(distinct (rr,tier,wins))=1
), history_summary as (
 select region,player_key,min(observed_at) first_observed,max(observed_at) last_observed,
 count(*) observations,count(distinct (observed_at at time zone 'UTC')::date) observed_days,
 max(rr) best_observed_rr,min(wins) first_observed_wins,max(wins) max_observed_wins,
 max(case tier when 'Radiant' then 4 when 'Immortal 3' then 3 when 'Immortal 2' then 2 when 'Immortal 1' then 1 else 0 end) peak_observed_tier,
 min(regional_rank) best_observed_regional_rank,
 (array_agg(rr order by observed_at))[1] first_observed_rr,
 (array_agg(tier order by observed_at))[1] first_observed_tier,
 min(observed_at) filter(where has_global) global_first_observed,
 count(*) filter(where has_global) global_observations,
 max(case tier when 'Radiant' then 4 when 'Immortal 3' then 3 when 'Immortal 2' then 2 when 'Immortal 1' then 1 else 0 end) filter(where has_global) global_peak_observed_tier,
 (array_agg(tier order by observed_at) filter(where has_global))[1] global_first_observed_tier
 from deduplicated group by region,player_key
), rows as (
 select p.region,p.player_key,p.country,p.tier,p.rr,p.wins,p.stats_ambiguous,p.rank_ambiguous,p.carried_forward,
 case when p.country is not null then p.country
      when r.choices=1 and (f.country is null or f.country=r.country) then r.country
      when r.choices is null then f.country end resolved_country,
 case when p.country is not null then 'snapshot' when r.choices=1 and (f.country is null or f.country=r.country) then 'recent leaderboard'
      when r.choices is null and f.country is not null then 'reported profile' else 'unknown' end country_source,
 h.first_observed,h.last_observed,h.observations,h.observed_days,h.best_observed_rr,h.first_observed_wins,h.max_observed_wins,
 h.peak_observed_tier,h.best_observed_regional_rank,h.first_observed_rr,h.first_observed_tier,h.global_first_observed,h.global_observations,
 h.global_peak_observed_tier,h.global_first_observed_tier
 from current_players p left join history_summary h using(region,player_key) left join reports r using(region,player_key)
 cross join latest l left join reported_profile_countries f on f.player_key=lower(p.display_name)
 and f.fetched_at between l.completed_at-interval '7 days' and current_timestamp
)
select json_build_object('snapshotId',l.id,'act',l.act,'completedAt',l.completed_at,
 'coverage',(select json_build_object('firstObserved',min(observed_at),'lastObserved',max(observed_at),'distinctSourceTimes',count(distinct observed_at),
 'globalFirstObserved',min(observed_at) filter(where source='global'),'legacyFirstObserved',min(observed_at) filter(where source='legacy'),
 'completeGlobalSnapshots',(select count(*) from global_leaderboard_runs r where r.act=l.act and r.status='COMPLETE' and r.id<=l.id)) from raw_history),
 'players',coalesce((select json_agg(rows order by region,player_key) from rows),'[]'::json))::text from latest l;
