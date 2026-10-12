-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
-- 公开源码学习版，未经书面授权不得商用；初始业务为空。
create table member_profile (
 id bigint auto_increment primary key,
 department_id bigint not null,
 version bigint not null,
 account_id bigint not null unique,
 contact_name varchar(120) not null,
 contact varchar(160) not null,
 city varchar(80) not null,
 district varchar(80) not null,
 foreign key(department_id) references department(id),
 foreign key(account_id) references account(id)
);
create index ix_member_profile_dept on member_profile(department_id,id);
create table pet (
 id bigint auto_increment primary key,
 department_id bigint not null,
 version bigint not null,
 owner_id bigint not null,
 name varchar(120) not null,
 species varchar(60) not null,
 weight_kg decimal(7,2) not null,
 care_notes varchar(1800) not null,
 enabled boolean not null,
 foreign key(department_id) references department(id),
 foreign key(owner_id) references account(id)
);
create index ix_pet_dept on pet(department_id,id);
create table care_provider (
 id bigint auto_increment primary key,
 department_id bigint not null,
 version bigint not null,
 account_id bigint not null unique,
 display_name varchar(120) not null,
 city varchar(80) not null,
 district varchar(80) not null,
 address varchar(500) not null,
 intro varchar(1200) not null,
 species varchar(20) not null,
 max_weight_kg decimal(7,2) not null,
 boarding boolean not null,
 home_visit boolean not null,
 boarding_capacity int not null,
 visit_capacity int not null,
 boarding_price_minor bigint not null,
 visit_price_minor bigint not null,
 status varchar(20) not null,
 review_note varchar(1200) not null,
 updated_at timestamp(6) not null,
 foreign key(department_id) references department(id),
 foreign key(account_id) references account(id)
);
create index ix_care_provider_dept on care_provider(department_id,id);
create table care_availability (
 id bigint auto_increment primary key,
 department_id bigint not null,
 version bigint not null,
 provider_id bigint not null,
 kind varchar(20) not null,
 visit_window varchar(20) not null,
 starts_on date not null,
 ends_on date not null,
 foreign key(department_id) references department(id),
 foreign key(provider_id) references care_provider(id)
);
create index ix_care_availability_dept on care_availability(department_id,id);
create table care_request (
 id bigint auto_increment primary key,
 department_id bigint not null,
 version bigint not null,
 owner_id bigint not null,
 pet_id bigint not null,
 kind varchar(20) not null,
 city varchar(80) not null,
 district varchar(80) not null,
 title varchar(120) not null,
 public_note varchar(1000) not null,
 address varchar(500) not null,
 care_notes varchar(1800) not null,
 visit_window varchar(20) not null,
 starts_on date not null,
 ends_on date not null,
 status varchar(20) not null,
 created_at timestamp(6) not null,
 foreign key(department_id) references department(id),
 foreign key(owner_id) references account(id),
 foreign key(pet_id) references pet(id)
);
create index ix_care_request_dept on care_request(department_id,id);
create table care_offer (
 id bigint auto_increment primary key,
 department_id bigint not null,
 version bigint not null,
 request_id bigint not null,
 provider_id bigint not null,
 request_version bigint not null,
 provider_version bigint not null,
 pet_version bigint not null,
 unit_minor bigint not null,
 units int not null,
 total_minor bigint not null,
 note varchar(1000) not null,
 status varchar(20) not null,
 expires_at timestamp(6) not null,
 foreign key(department_id) references department(id),
 foreign key(request_id) references care_request(id),
 foreign key(provider_id) references care_provider(id)
);
create index ix_care_offer_dept on care_offer(department_id,id);
create table care_order (
 id bigint auto_increment primary key,
 department_id bigint not null,
 version bigint not null,
 request_id bigint not null unique,
 offer_id bigint not null unique,
 owner_id bigint not null,
 provider_id bigint not null,
 provider_account_id bigint not null,
 pet_id bigint not null,
 reference varchar(50) not null unique,
 kind varchar(20) not null,
 visit_window varchar(20) not null,
 starts_on date not null,
 ends_on date not null,
 total_minor bigint not null,
 snapshot longtext not null,
 status varchar(20) not null,
 created_at timestamp(6) not null,
 foreign key(department_id) references department(id),
 foreign key(request_id) references care_request(id),
 foreign key(offer_id) references care_offer(id),
 foreign key(owner_id) references account(id),
 foreign key(provider_id) references care_provider(id),
 foreign key(provider_account_id) references account(id),
 foreign key(pet_id) references pet(id)
);
create index ix_care_order_dept on care_order(department_id,id);
create table care_task (
 id bigint auto_increment primary key,
 department_id bigint not null,
 version bigint not null,
 order_id bigint not null,
 task_date date not null,
 kind varchar(20) not null,
 status varchar(20) not null,
 foreign key(department_id) references department(id),
 foreign key(order_id) references care_order(id),
 unique(order_id,task_date,kind)
);
create index ix_care_task_dept on care_task(department_id,id);
create table care_entry (
 id bigint auto_increment primary key,
 department_id bigint not null,
 version bigint not null,
 order_id bigint not null,
 task_id bigint,
 supersedes_id bigint,
 actor_id bigint not null,
 action varchar(30) not null,
 note varchar(1800) not null,
 created_at timestamp(6) not null,
 foreign key(department_id) references department(id),
 foreign key(order_id) references care_order(id),
 foreign key(task_id) references care_task(id),
 foreign key(supersedes_id) references care_entry(id),
 foreign key(actor_id) references account(id)
);
create index ix_care_entry_dept on care_entry(department_id,id);
create table care_photo (
 id bigint auto_increment primary key,
 department_id bigint not null,
 version bigint not null,
 order_id bigint not null,
 actor_id bigint not null,
 caption varchar(300) not null,
 mime varchar(30) not null,
 sha256 varchar(64) not null,
 data longblob not null,
 created_at timestamp(6) not null,
 foreign key(department_id) references department(id),
 foreign key(order_id) references care_order(id),
 foreign key(actor_id) references account(id)
);
create index ix_care_photo_dept on care_photo(department_id,id);
create table money_entry (
 id bigint auto_increment primary key,
 department_id bigint not null,
 version bigint not null,
 order_id bigint not null,
 original_id bigint,
 actor_id bigint not null,
 amount_minor bigint not null,
 kind varchar(20) not null,
 reference varchar(120) not null,
 note varchar(1000) not null,
 created_at timestamp(6) not null,
 foreign key(department_id) references department(id),
 foreign key(order_id) references care_order(id),
 foreign key(actor_id) references account(id),
 foreign key(original_id) references money_entry(id)
);
create index ix_money_entry_dept on money_entry(department_id,id);
create table care_review (
 id bigint auto_increment primary key,
 department_id bigint not null,
 version bigint not null,
 order_id bigint not null unique,
 provider_id bigint not null,
 owner_id bigint not null,
 rating int not null,
 comment varchar(1200) not null,
 status varchar(20) not null,
 moderation_note varchar(1200) not null,
 created_at timestamp(6) not null,
 foreign key(department_id) references department(id),
 foreign key(order_id) references care_order(id),
 foreign key(provider_id) references care_provider(id),
 foreign key(owner_id) references account(id)
);
create index ix_care_review_dept on care_review(department_id,id);
create index ix_order_capacity on care_order(provider_id,kind,visit_window,starts_on,ends_on,status);
create index ix_order_pet on care_order(pet_id,starts_on,ends_on,status);
create index ix_request_open on care_request(status,city,district,starts_on,ends_on);
create index ix_available on care_availability(provider_id,kind,visit_window,starts_on,ends_on);
