create table historical_act_catalog (
  act varchar(36) primary key,
  label varchar(80) not null,
  episode varchar(20) not null,
  act_order integer not null,
  current_act boolean not null default false,
  discovered_at timestamp with time zone not null default now()
);

create table historical_act_sources (
  act varchar(36) not null references historical_act_catalog(act),
  source_region varchar(10) not null,
  status varchar(20) not null default 'PENDING',
  next_page integer not null default 1,
  pages integer not null default 0,
  players integer not null default 0,
  source_updated_at timestamp with time zone,
  started_at timestamp with time zone,
  completed_at timestamp with time zone,
  retry_after timestamp with time zone,
  attempts integer not null default 0,
  error varchar(500),
  primary key(act,source_region)
);

create table historical_act_pages (
  act varchar(36) not null,
  source_region varchar(10) not null,
  page integer not null,
  status varchar(20) not null,
  rows integer not null default 0,
  source_updated_at timestamp with time zone,
  fetched_at timestamp with time zone not null default now(),
  error varchar(500),
  primary key(act,source_region,page),
  foreign key(act,source_region) references historical_act_sources(act,source_region) on delete cascade
);

create table historical_act_players (
  act varchar(36) not null,
  source_region varchar(10) not null,
  player_region varchar(10) not null,
  player_key varchar(300) not null,
  display_name varchar(300) not null,
  country varchar(2),
  source_rank integer not null,
  rr integer not null,
  tier varchar(40) not null,
  wins integer not null,
  source_updated_at timestamp with time zone not null,
  fetched_at timestamp with time zone not null default now(),
  primary key(act,source_region,player_region,player_key),
  foreign key(act,source_region) references historical_act_sources(act,source_region) on delete cascade
);
create index idx_historical_players_country on historical_act_players(act,source_region,country,tier);
create index idx_historical_players_rank on historical_act_players(act,source_region,source_rank);
