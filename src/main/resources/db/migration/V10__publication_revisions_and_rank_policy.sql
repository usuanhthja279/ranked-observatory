alter table historical_act_catalog
  add column threshold_profile varchar(40) not null default 'EPISODE_3_BACKCAST';

update historical_act_catalog
set threshold_profile=case when current_act or act_order>=32 then 'V26_ACT_II_PLUS' else 'EPISODE_3_BACKCAST' end;

alter table historical_act_catalog
  add constraint ck_historical_act_threshold_profile
  check (threshold_profile in ('EPISODE_3_BACKCAST','V26_ACT_II_PLUS'));

create table leaderboard_publication_revision (
  scope varchar(20) primary key check (scope in ('current','archive')),
  revision bigint not null default 0
);
insert into leaderboard_publication_revision(scope,revision) values ('current',0),('archive',0);

create function bump_leaderboard_publication_revision() returns trigger language plpgsql as $$
begin
  update leaderboard_publication_revision set revision=revision+1 where scope=TG_ARGV[0];
  return null;
end;
$$;

create trigger leaderboard_revision_current_runs
  after insert or update or delete on global_leaderboard_runs
  for each statement execute function bump_leaderboard_publication_revision('current');
create trigger leaderboard_revision_current_players
  after insert or update or delete on global_leaderboard_players
  for each statement execute function bump_leaderboard_publication_revision('current');
create trigger leaderboard_revision_current_regional
  after insert or update or delete on regional_observation_cache
  for each statement execute function bump_leaderboard_publication_revision('current');
create trigger leaderboard_revision_archive_catalog
  after insert or update or delete on historical_act_catalog
  for each statement execute function bump_leaderboard_publication_revision('archive');
create trigger leaderboard_revision_archive_sources
  after insert or update or delete on historical_act_sources
  for each statement execute function bump_leaderboard_publication_revision('archive');
create trigger leaderboard_revision_archive_pages
  after insert or update or delete on historical_act_pages
  for each statement execute function bump_leaderboard_publication_revision('archive');
create trigger leaderboard_revision_archive_players
  after insert or update or delete on historical_act_players
  for each statement execute function bump_leaderboard_publication_revision('archive');
