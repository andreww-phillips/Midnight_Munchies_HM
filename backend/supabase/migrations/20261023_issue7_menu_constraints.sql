-- Issue 7: item creation requires only a name and price.
-- Run in the SQL Editor for the Supabase project used by frontend/.env.
-- Keeps all rows, item IDs, names, prices, restaurant relationships and the
-- primary key. Names and optional metadata may repeat within/across restaurants.
begin;

alter table public."Menu_Items"
    drop constraint if exists "Menu_Items_item_name_key",
    drop constraint if exists "Menu_Items_item_image_key",
    drop constraint if exists "Menu_Items_item_description_key";

-- Also cover these names if they were created as standalone unique indexes.
drop index if exists public."Menu_Items_item_name_key";
drop index if exists public."Menu_Items_item_image_key";
drop index if exists public."Menu_Items_item_description_key";

alter table public."Menu_Items"
    alter column item_image drop not null,
    alter column item_image set default '',
    alter column item_description drop not null,
    alter column item_description set default array[]::text[];

create index if not exists menu_items_restaurant_id_lookup
    on public."Menu_Items" (restaurant_id);

-- Detect a differently named single-column unique constraint/index rather than
-- leaving a partial fix that still rejects legitimate menu additions.
do $$
begin
    if exists (
        select 1
        from pg_index i
        join pg_attribute a on a.attrelid = i.indrelid and a.attnum = i.indkey[0]
        where i.indrelid = 'public."Menu_Items"'::regclass
          and i.indisunique and not i.indisprimary and i.indnkeyatts = 1
          and a.attname in ('item_name', 'item_image', 'item_description')
    ) then
        raise exception 'A differently named unique index still restricts menu names or metadata; inspect Menu_Items indexes before committing.';
    end if;
end $$;

notify pgrst, 'reload schema';
commit;
