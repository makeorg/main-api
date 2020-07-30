alter table question rename column country to countries;

alter table idea drop column country;
alter table idea drop column language;

alter table tag drop column country;
alter table tag drop column language;

commit;
