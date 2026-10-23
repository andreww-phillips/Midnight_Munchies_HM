# Issue 7 integration after PR #36

## Current runtime

The application uses the in-memory simulator by default. No Supabase credentials,
SQL migration, or database connection is required for the active registration,
restaurant library, menu management, or ordering flow. The earlier database work
is deferred; its adapters and migration are inactive unless deliberately selected
for future development.

PR #36 is present on local main at `c18b42b`. Its authentication responses,
customer browsing, cart, ordering, registration validation, and native form
submission are retained. `CustomerApp` delegates to the shared registration,
library, menu, and order-history screens. The restored purple styling is retained.

Restaurant staff registration creates both the local account and its owned
restaurant in the same backend operation. The restaurant ID and owner ID match
the newly registered staff ID. Logo and menu-file inputs are optional and use
local data URLs; there are no Supabase uploads or profile inserts.

The seeded account `restaurant@example.com` / `password` owns Bobs Burgers.
`customer@example.com` / `password` can browse and order. New staff and customer
accounts work in the same local simulator.

## Issue 7 behavior

- Adding an item requires only a nonblank name and a nonnegative price.
- Names and prices may repeat within and across restaurants. Drink at 3.99 can
  be added more than once; no database uniqueness rule applies.
- Owning staff can add, edit, remove, and switch availability in both directions.
  The authorization proxy enforces ownership on every mutation.
- Customers can order available items. Sold-out and removed items are rejected
  without storing a partial order.
- Existing orders retain their captured names, prices, quantities and IDs after
  the menu is edited, marked unavailable, or removed.
- Accounts, restaurants, menu items and orders reset when the backend restarts.
- The later FIFO changes in Order.java and OrderStates.java remain in place.

## Exact regression history

Issue 7 was implemented through `c3671e4` on feature/menu-management and merged by
PR #32 (`a986fd8`). Commit `45f25fe` merged main into menu_branch. Commit `dca63ae`,
Revert "Merge branch 'main' into menu_branch", explicitly reversed that merge,
removing issue 7 endpoints, proxy methods, controls, snapshots and tests. PR #35
(`9ddcaba`) brought the revert into main.

The feature branch remains an ancestor of main, so merging it again cannot
restore those removed changes. The implementation has been reapplied on top of
PR #36 while preserving its customer and registration features.

```bash
git show --stat dca63ae
git diff 7464710 9ddcaba -- backend/src/main/java/com/midnightmunchies/application/OrdController.java
git merge-base --is-ancestor feature/menu-management main
```

## Validation

Backend coverage includes domain rules, the authorization proxy, menu endpoints,
order snapshots, registration creating an owned in-memory restaurant, repeated
Drink / 3.99 additions, and the full registered-staff/customer flow. Frontend
coverage verifies registration without database credentials, optional local
images, a menu editor with only name and price, all management controls, customer
ordering, and order history. The production build and lint are also checked.

Verified after the in-memory switch: 188 backend tests, 52 frontend tests, lint
and the production build passed. A running local server was also checked through
HTTP for demo login, staff registration and ownership, repeated Drink / 3.99
additions, French Fries / 5.99, edit, availability changes, removal, customer
ordering and preserved order snapshots. No shared database was accessed.
