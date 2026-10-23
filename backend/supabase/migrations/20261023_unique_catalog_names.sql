-- Apply after 20261023_issue7_menu_constraints.sql when using catalog.mode=supabase.
-- Existing duplicates must be resolved explicitly before this migration can succeed.
-- This transaction never renames/deletes catalog rows or changes historical orders.
begin;

create unique index if not exists restaurants_normalized_name_unique
    on public."Restaurant"
    (lower(btrim(regexp_replace(restaurant_name, '[[:space:]]+', ' ', 'g'))));

-- Availability is deliberately not filtered: SOLD_OUT names remain reserved.
-- Deletion is physical in the current repository, so removed names can be reused.
create unique index if not exists menu_items_restaurant_name_unique
    on public."Menu_Items"
    (restaurant_id, lower(btrim(regexp_replace(item_name, '[[:space:]]+', ' ', 'g'))));

notify pgrst, 'reload schema';
commit;
