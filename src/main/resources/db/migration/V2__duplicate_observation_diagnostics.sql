alter table leaderboard_runs add column raw_rows integer not null default 0;
alter table leaderboard_runs add column duplicate_rows integer not null default 0;
alter table leaderboard_runs add column ambiguous_ranks integer not null default 0;
alter table leaderboard_players add column rank_ambiguous boolean not null default false;
