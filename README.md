# SOEN_343_Project

## Authors:
  - Andrew Phillips (#40299805) - Group Leader & Frontend Developer
  - Anish Mehra (#40305592) - Backend Developer
  - Jeffrey Gueyie (#40315016) - Fullstack Developer
  - Kevin Saliba (#40305591) - Backend Developer
  - Noe Menacho Tardieu (#40306031) - Backend Developer

## Text Stack:
  - PostgreSQL (Supabase)
  - Java
  - JavaScript
  - React.js
  - Springboot 

## Product Overview:
Midnight Munchies is a simulator for a late-night food ordering and delivery platform. It brings together customers, restaurants, couriers, and platform operators inside a single application, and it exposes the interfaces each of these roles needs to do their part in fulfilling an order. Customers browse menus, place and pay for orders, and follow the delivery on a map. Restaurant staff publish menus, accept or reject incoming orders, and mark food ready for pickup. Couriers go on duty, accept the order offered to them by the dispatcher, and confirm pickup and delivery. Operators supervise the running system, pause restaurants when needed, reassign stalled orders, and reset the simulation to its initial state for repeatable demonstrations. The sections that follow describe how the product fits into its environment and the assumptions on which its features rely.

## Repository Structure

```text
Midnight_Munchies/
├── frontend/
│   ├── public/
│   └── src/ui/dash/
├── backend/
│   └── src/
│       ├── main/
│       └── test/
├── README.md
├── .gitignore
└── .gitattributes
```

## Starting the Application

The backend and frontend run as separate processes. Start each one in its own
terminal and leave both terminals open.

### Local configuration

The application now runs as an **in-memory simulator**. No Supabase account,
`.env` credentials, database connection, or SQL migration is required. Existing
Supabase entries in `frontend/.env` are not used by registration, browsing,
menu management, or ordering.

The backend defaults to `catalog.mode=memory` and seeds the local restaurant and
menu catalog. Accounts, restaurants, menus and orders live for the lifetime of
the backend process and reset when it restarts. The frontend uses the backend
API at `http://localhost:8080`; `VITE_API_URL` can override that address if needed.

### Terminal 1: Backend

From the repository root, change to `backend/`.

PowerShell:

```powershell
cd .\backend
.\mvnw.cmd spring-boot:run
```

Git Bash:

```bash
cd backend
./mvnw.cmd spring-boot:run
```

The backend starts at `http://localhost:8080`. Wait until the terminal reports
that `BackendApplication` has started before using the frontend.

### Terminal 2: Frontend

From the repository root, change to `frontend/`.

Install dependencies the first time and after pulling dependency changes:

```bash
npm ci
```

Start the development server:

```bash
npm run dev
```

Open the URL printed by Vite, usually `http://localhost:5173`.

### Stopping the Application

Press `Ctrl+C` in each terminal running the backend or frontend.

## Testing and Building

Run backend tests from `backend/`:

PowerShell:

```powershell
.\mvnw.cmd test
```

Git Bash:

```bash
./mvnw.cmd test
```

Run frontend linting and a production build from `frontend/`:

```bash
npm run lint
npm run test
npm run build
```

Backend tests use memory or an isolated local HTTP fixture; they never access
the shared Supabase database. Frontend tests mock backend API calls.

## Verifying issue 7

1. Start the backend and frontend. No database setup is needed.
2. Log in as `restaurant@example.com` with password `password`. Open
   **Bobs Burgers** and select **Edit menu**. This demo account owns that restaurant.
   Alternatively, register **Restaurant Staff** with a username, password and
   restaurant name; the backend creates its owned restaurant immediately. Logo
   and menu-file selections are optional and kept locally in memory.
3. Add **Drink / 3.99**, **French Fries / 5.99**, and another **Drink / 3.99**.
   Menu items require only a name and price. Names and prices may repeat within
   and across restaurants.
4. Edit a name or price, mark an item sold out and available again, then remove
   an item. Staff can only manage their own restaurant's menu.
5. Use a private browser session to log in as `customer@example.com` with password
   `password`, or register a customer account. Browse the menu and place an order.
   Sold-out and removed items cannot be ordered.
6. After placing an order, edit or remove the item as staff. Refresh the customer's
   orders: the original item name, unit price and quantity remain unchanged.

Other demo logins are `courier@example.com` and `operator@example.com`, both with
password `password`. Couriers can browse; menu management is restricted to owning
restaurant staff. All simulator changes reset when the backend restarts.

See [the issue 7 integration notes](docs/issue-7-integration.md) for the exact
regression history and current implementation.
