create table leaderboard_publication_revision (
    scope varchar(16) primary key,
    revision bigint not null
);
insert into leaderboard_publication_revision(scope, revision) values ('current', 0), ('archive', 0);
