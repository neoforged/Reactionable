create table discord_users
(
    user   big int not null primary key,
    github text    not null
) without rowid;
