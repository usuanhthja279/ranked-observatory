create table mi_tactical_annotations (
  annotation_id varchar(36) not null,
  revision integer not null check (revision > 0),
  owner_username varchar(100) not null,
  provider varchar(80) not null,
  provider_match_id varchar(160) not null,
  round_number integer not null check (round_number > 0),
  event_id varchar(800),
  event_time_raw bigint,
  judgment varchar(40) not null check (judgment in ('POSITIONING','OPENING','TRADE','UTILITY','ECONOMY','OBJECTIVE','TEAMPLAY','OTHER','INSUFFICIENT_EVIDENCE')),
  reason varchar(4000) not null,
  better_alternative varchar(4000),
  insufficient_evidence boolean not null,
  confidence integer not null check (confidence between 0 and 100),
  vod_url varchar(1200),
  guideline_version varchar(80) not null,
  status varchar(20) not null check (status in ('ACTIVE','WITHDRAWN')),
  disagreement_group varchar(100),
  created_at timestamp with time zone not null default current_timestamp,
  primary key(annotation_id, revision),
  foreign key(provider, provider_match_id) references mi_matches(provider, provider_match_id) on delete cascade
);
create index mi_tactical_annotations_owner_match on mi_tactical_annotations(owner_username, provider, provider_match_id, created_at desc);
