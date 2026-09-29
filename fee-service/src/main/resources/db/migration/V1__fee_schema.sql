-- fee-service owns schema "fees" (Flyway creates it; default-schema = fees).

create table fee_schedule (
    id             bigint generated always as identity primary key,
    code           varchar(20)   not null,
    name           varchar(200)  not null,
    effective_from date          not null,
    effective_to   date,
    notes          varchar(1000),
    constraint uq_fee_schedule_code unique (code),
    constraint ck_fee_schedule_range check (effective_to is null or effective_to >= effective_from)
);

create index ix_fee_schedule_effective on fee_schedule (effective_from, effective_to);

create table fee_item (
    id            bigint generated always as identity primary key,
    schedule_id   bigint        not null references fee_schedule (id) on delete cascade,
    fee_code      varchar(40)   not null,
    description   varchar(300)  not null,
    category      varchar(20)   not null,
    fee_group     varchar(60)   not null,
    large_entity  numeric(12, 2) not null,
    small_entity  numeric(12, 2) not null,
    micro_entity  numeric(12, 2) not null,
    unit          varchar(20)   not null,
    display_order integer       not null,
    constraint uq_fee_item_code unique (schedule_id, fee_code),
    constraint ck_fee_item_category check (category in ('PATENT', 'TRADEMARK')),
    constraint ck_fee_item_unit check (unit in ('EACH', 'PER_CLAIM', 'PER_CLASS', 'PER_50_SHEETS', 'PER_MONTH')),
    constraint ck_fee_item_amounts check (large_entity >= 0 and small_entity >= 0 and micro_entity >= 0)
);

create index ix_fee_item_schedule on fee_item (schedule_id, display_order);

create table saved_quote (
    id            uuid          primary key,
    kind          varchar(20)   not null,
    label         varchar(200),
    request       jsonb         not null,
    schedule_code varchar(20)   not null,
    total         numeric(14, 2) not null,
    created_at    timestamptz   not null,
    constraint ck_saved_quote_kind check (kind in ('PATENT_FILING', 'MAINTENANCE', 'TRADEMARK'))
);

create index ix_saved_quote_created on saved_quote (created_at);
