create table mi_benchmark_cohorts (
  cohort_id varchar(36) primary key,
  owner_username varchar(100) not null,
  name varchar(160) not null,
  region varchar(32) not null,
  act_id varchar(160) not null,
  leaderboard_snapshot_date date not null,
  data_access_status varchar(40) not null check (data_access_status in ('UNCONFIRMED','OWNER_REPORTED_PERMISSION')),
  created_at timestamp with time zone not null default current_timestamp,
  updated_at timestamp with time zone not null default current_timestamp,
  check (length(trim(name)) > 0)
);
create index mi_benchmark_cohorts_owner on mi_benchmark_cohorts(owner_username, created_at desc);

create table mi_benchmark_memberships (
  cohort_id varchar(36) not null references mi_benchmark_cohorts(cohort_id) on delete cascade,
  selection_rank integer not null check (selection_rank between 1 and 10),
  riot_id varchar(300) not null,
  normalized_riot_id varchar(300) not null,
  display_name varchar(200),
  matches_requested integer not null default 20 check (matches_requested between 1 and 20),
  primary key (cohort_id, selection_rank),
  unique (cohort_id, normalized_riot_id)
);
create index mi_benchmark_membership_identity on mi_benchmark_memberships(normalized_riot_id);
