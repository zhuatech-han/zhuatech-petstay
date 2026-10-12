-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
-- 公开源码学习版，未经书面授权不得商用；新增查询索引及字典版本，不改写订单快照、照片或金额。
create index ix_care_order_owner_state_id on care_order(owner_id,status,id);
create index ix_care_order_provider_state_id on care_order(provider_account_id,status,id);
create index ix_care_order_dept_state_id on care_order(department_id,status,id);
create index ix_care_photo_order_created on care_photo(order_id,created_at);
alter table dictionary_entry add column version bigint not null default 1;
create index ix_care_request_status_id on care_request(status,id);
create index ix_care_order_status_id on care_order(status,id);
create index ix_care_order_resource_status_id on care_order(provider_id,status,id);
