-- Prepared for future Act Trends work. This directory is deliberately outside
-- Spring/Flyway's classpath migration locations while the archive is running.
-- Move into db/migration only after historical extraction is complete and
-- migration execution is explicitly scheduled.

create table historical_act_metadata (
  act varchar(36) primary key references historical_act_catalog(act),
  starts_at timestamp with time zone,
  ends_at timestamp with time zone,
  date_source varchar(300),
  dates_verified boolean not null default false,
  rules_era varchar(80),
  platform varchar(20) not null default 'pc',
  capture_semantics varchar(40) not null default 'ARCHIVED_STANDINGS',
  metadata_version integer not null default 1,
  updated_at timestamp with time zone not null default now(),
  check (ends_at is null or starts_at is null or ends_at > starts_at)
);

create table historical_rank_rules (
  rules_era varchar(80) primary key,
  valid_from_act varchar(36) references historical_act_catalog(act),
  valid_through_act varchar(36) references historical_act_catalog(act),
  tier_system jsonb not null,
  regional_thresholds jsonb not null default '{}'::jsonb,
  structural_changes jsonb not null default '[]'::jsonb,
  source_url varchar(1000),
  verified_at timestamp with time zone,
  rules_version integer not null default 1
);

create table historical_subregion_membership (
  membership_id bigint generated always as identity primary key,
  grouping_id varchar(80) not null,
  grouping_version integer not null,
  parent_region varchar(10) not null,
  country varchar(2) not null,
  subregion varchar(80) not null,
  effective_from_act varchar(36) references historical_act_catalog(act),
  effective_through_act varchar(36) references historical_act_catalog(act),
  authority varchar(40) not null,
  source_url varchar(1000),
  unique(grouping_id,grouping_version,parent_region,country,subregion,effective_from_act)
);

create table historical_source_audits (
  act varchar(36) not null references historical_act_catalog(act),
  source_region varchar(10) not null,
  audit_version integer not null,
  source_status varchar(20) not null,
  pagination_terminal boolean not null,
  source_pages integer not null default 0,
  last_page integer,
  terminal_page_rows integer,
  fetched_rows bigint not null default 0,
  distinct_entries bigint not null default 0,
  first_rank integer,
  last_rank integer,
  suspected_cap varchar(40),
  tier_capability varchar(40) not null,
  known_country_rows bigint not null default 0,
  unknown_country_rows bigint not null default 0,
  matched_global_rows bigint not null default 0,
  conflicting_global_rows bigint not null default 0,
  warnings jsonb not null default '[]'::jsonb,
  audited_at timestamp with time zone not null default now(),
  primary key(act,source_region,audit_version)
);

create table historical_act_data_revisions (
  id uuid primary key,
  act varchar(36) not null references historical_act_catalog(act),
  input_fingerprint varchar(128) not null,
  normalization_version integer not null,
  status varchar(20) not null,
  included_regions jsonb not null default '[]'::jsonb,
  quality_summary jsonb not null default '{}'::jsonb,
  created_at timestamp with time zone not null default now(),
  ready_at timestamp with time zone,
  failure_reason varchar(1000)
);
create index idx_historical_data_revision_inputs
  on historical_act_data_revisions(act,input_fingerprint,normalization_version,status);

create table historical_canonical_entries (
  act_revision_id uuid not null references historical_act_data_revisions(id) on delete cascade,
  act varchar(36) not null references historical_act_catalog(act),
  player_shard varchar(10) not null,
  entry_key varchar(300) not null,
  stable_account_id varchar(200),
  display_name varchar(300) not null,
  country varchar(2),
  country_provenance varchar(40) not null,
  raw_tier varchar(40) not null,
  normalized_tier varchar(40) not null,
  rr integer not null,
  wins integer not null,
  regional_rank integer not null,
  provider_global_rank integer,
  global_rr integer,
  global_tier varchar(40),
  conflict_flags jsonb not null default '[]'::jsonb,
  identity_quality varchar(30) not null default 'NAME_KEY',
  regional_fetched_at timestamp with time zone not null,
  global_fetched_at timestamp with time zone,
  primary key(act_revision_id,player_shard,entry_key)
);
create index idx_historical_canonical_country_tier
  on historical_canonical_entries(act_revision_id,player_shard,country,normalized_tier);
create index idx_historical_canonical_rank
  on historical_canonical_entries(act_revision_id,player_shard,regional_rank,entry_key);

create table historical_trend_aggregates (
  act_revision_id uuid not null references historical_act_data_revisions(id) on delete cascade,
  act varchar(36) not null references historical_act_catalog(act),
  player_shard varchar(10) not null,
  country varchar(12) not null,
  normalized_tier varchar(40) not null,
  cohort varchar(30) not null default 'ALL_OBSERVED',
  observed_count bigint not null,
  known_country_count bigint not null,
  unknown_country_count bigint not null,
  rr_sample_count bigint not null,
  median_rr numeric(12,3),
  p75_rr numeric(12,3),
  wins_sample_count bigint not null,
  median_act_wins numeric(12,3),
  p75_act_wins numeric(12,3),
  flagged_count bigint not null default 0,
  quality_flags jsonb not null default '[]'::jsonb,
  primary key(act_revision_id,player_shard,country,normalized_tier,cohort)
);
create index idx_historical_trend_aggregate_query
  on historical_trend_aggregates(act,player_shard,country,normalized_tier,cohort,act_revision_id);

create table historical_trend_cohorts (
  act_revision_id uuid not null references historical_act_data_revisions(id) on delete cascade,
  player_shard varchar(10) not null,
  cohort varchar(30) not null,
  requested_depth integer not null,
  boundary_rank integer,
  member_count bigint not null,
  tie_policy varchar(30) not null,
  quality_flags jsonb not null default '[]'::jsonb,
  primary key(act_revision_id,player_shard,cohort)
);

create table historical_trend_publications (
  id uuid primary key,
  parent_id uuid references historical_trend_publications(id),
  status varchar(20) not null,
  formula_version varchar(40) not null,
  grouping_version varchar(80),
  created_at timestamp with time zone not null default now(),
  published_at timestamp with time zone,
  failure_reason varchar(1000)
);

create table historical_trend_publication_acts (
  publication_id uuid not null references historical_trend_publications(id) on delete cascade,
  act varchar(36) not null references historical_act_catalog(act),
  act_revision_id uuid not null references historical_act_data_revisions(id),
  primary key(publication_id,act)
);

create table historical_trend_current_publication (
  singleton boolean primary key default true check(singleton),
  publication_id uuid not null references historical_trend_publications(id),
  updated_at timestamp with time zone not null default now()
);
