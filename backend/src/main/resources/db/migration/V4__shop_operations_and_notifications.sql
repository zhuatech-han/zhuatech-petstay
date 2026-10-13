-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
-- 公开源码学习版，未经书面授权不得商用。旧订单不补造入住记录。
alter table care_order add column managed_care boolean not null default false;
alter table care_order add column stay_status varchar(20) not null default 'LEGACY';
alter table care_order add column arrived_at timestamp(6) null;
alter table care_order add column departed_at timestamp(6) null;
create index ix_order_stay on care_order(provider_id,stay_status,departed_at);
create table shop_policy (
 id bigint auto_increment primary key, department_id bigint not null, version bigint not null,
 provider_id bigint not null unique, workforce_enabled boolean not null,
 foreign key(department_id) references department(id), foreign key(provider_id) references care_provider(id)
);
create table shop_staff (
 id bigint auto_increment primary key, department_id bigint not null, version bigint not null,
 provider_id bigint not null, account_id bigint not null, role varchar(20) not null,
 status varchar(20) not null, created_at timestamp(6) not null,
 foreign key(department_id) references department(id), foreign key(provider_id) references care_provider(id),
 foreign key(account_id) references account(id), unique(provider_id,account_id)
);
create index ix_staff_account on shop_staff(account_id,status,provider_id);
create table staff_roster (
 id bigint auto_increment primary key, department_id bigint not null, version bigint not null,
 provider_id bigint not null, account_id bigint not null, starts_on date not null, ends_on date not null,
 visit_window varchar(20) not null,
 foreign key(department_id) references department(id), foreign key(provider_id) references care_provider(id), foreign key(account_id) references account(id)
);
create index ix_roster_dates on staff_roster(account_id,starts_on,ends_on);
create table care_assignment (
 id bigint auto_increment primary key, department_id bigint not null, version bigint not null,
 order_id bigint not null, provider_id bigint not null, account_id bigint not null,
 task_date date not null, visit_window varchar(20) not null,
 foreign key(department_id) references department(id), foreign key(order_id) references care_order(id),
 foreign key(provider_id) references care_provider(id), foreign key(account_id) references account(id), unique(order_id,task_date)
);
create index ix_assignment_dates on care_assignment(account_id,task_date,visit_window);
create table care_handover (
 id bigint auto_increment primary key, department_id bigint not null, version bigint not null,
 order_id bigint not null, actor_id bigint not null, kind varchar(20) not null,
 recipient varchar(120) not null, condition_note varchar(1800) not null, belongings varchar(1800) not null,
 created_at timestamp(6) not null,
 foreign key(department_id) references department(id), foreign key(order_id) references care_order(id),
 foreign key(actor_id) references account(id), unique(order_id,kind)
);
create table notification_preference (
 id bigint auto_increment primary key, department_id bigint not null, version bigint not null,
 account_id bigint not null unique, email varchar(254) not null, email_enabled boolean not null,
 reminders_enabled boolean not null, reminder_hour int not null, language varchar(10) not null,
 pending_email varchar(254) not null, code_hash varchar(64) not null,
 code_expires_at timestamp(6) null, code_requested_at timestamp(6) null, code_attempts int not null,
 foreign key(department_id) references department(id), foreign key(account_id) references account(id)
);
create table care_notification (
 id bigint auto_increment primary key, department_id bigint not null, version bigint not null,
 account_id bigint not null, order_id bigint null, event_key varchar(160) not null,
 event varchar(40) not null, reference varchar(50) not null, destination varchar(254) not null,
 language varchar(10) not null, delivery_status varchar(20) not null, attempts int not null,
 failure_code varchar(40) not null, created_at timestamp(6) not null, next_attempt_at timestamp(6) not null,
 read_at timestamp(6) null, sent_at timestamp(6) null,
 foreign key(department_id) references department(id), foreign key(account_id) references account(id),
 foreign key(order_id) references care_order(id), unique(account_id,event_key)
);
create index ix_notification_account on care_notification(account_id,id);
create index ix_notification_queue on care_notification(delivery_status,next_attempt_at,id);
insert into nav_menu(code,name,name_en,permission_code,position,enabled)
 select 'shop','门店与排期','Shops & schedule','portal',5,true where exists(select 1 from account);
insert into nav_menu(code,name,name_en,permission_code,position,enabled)
 select 'notifications','消息与提醒','Messages & reminders','portal',6,true where exists(select 1 from account);
