alter table global_leaderboard_runs add column regional_reconciled_at timestamp with time zone;
update global_leaderboard_runs set regional_reconciled_at=completed_at where status='COMPLETE' and source like 'global+regional%';
alter table global_leaderboard_players add column carried_forward boolean not null default false;
alter table global_leaderboard_players add column observation_source varchar(10) not null default 'global';
alter table global_leaderboard_players add column regional_rank_updated_at timestamp with time zone;
update global_leaderboard_players set observation_source=region where global_rank is null;
update global_leaderboard_players set regional_rank_updated_at=source_updated_at where regional_rank is not null and global_rank is null;
create table regional_observation_cache (
 act varchar(36) not null,region varchar(10) not null,player_key varchar(300) not null,
 display_name varchar(300) not null,country varchar(2),regional_rank integer not null,
 rr integer not null,tier varchar(40) not null,wins integer not null,source_updated_at timestamp with time zone not null,
 rank_ambiguous boolean not null,stats_ambiguous boolean not null,
 observation_source varchar(10) not null,regional_rank_updated_at timestamp with time zone,
 primary key(act,region,player_key)
);
insert into regional_observation_cache
 select r.act,p.region,p.player_key,p.display_name,p.country,p.regional_rank,p.rr,p.tier,p.wins,p.source_updated_at,p.rank_ambiguous,p.stats_ambiguous,p.observation_source,p.regional_rank_updated_at
 from global_leaderboard_players p join global_leaderboard_runs r on p.run_id=r.id
 where r.id=(select max(r2.id) from global_leaderboard_runs r2 where r2.act=r.act and r2.status='COMPLETE' and r2.regional_reconciled_at is not null)
 and p.regional_rank is not null;
