create table reported_profile_countries (
 player_key varchar(300) primary key,
 country varchar(2) not null,
 fetched_at timestamp with time zone not null
);
