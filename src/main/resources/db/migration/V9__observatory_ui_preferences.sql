create table observatory_ui_preferences (
  account_id bigint primary key references observatory_accounts(id) on delete cascade,
  compact_tables boolean not null default false,
  reduce_motion boolean not null default false,
  updated_at timestamp with time zone not null default current_timestamp
);
