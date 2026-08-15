alter table teams
    add column badge_url varchar(1000),
    add column logo_url varchar(1000),
    add column banner_url varchar(1000),
    add column equipment_url varchar(1000),
    add column fanart_url varchar(1000);

alter table leagues
    add column badge_url varchar(1000),
    add column logo_url varchar(1000),
    add column banner_url varchar(1000),
    add column poster_url varchar(1000),
    add column trophy_url varchar(1000),
    add column fanart_url varchar(1000);
