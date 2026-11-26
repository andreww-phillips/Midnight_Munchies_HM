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
the backend process and reset when it restarts. Ready orders waiting for a
courier are the exception: DLV stores their FIFO queue and order snapshots in
`backend/data/dlv-dispatch-queue.txt` so they can be restored after a backend
restart. Set `DLV_DISPATCH_QUEUE_FILE` to change that location. The frontend
uses the backend API at `http://localhost:8080`; `VITE_API_URL` can override
that address if needed.

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

### Resetting the simulation

Log in as `operator@example.com` with password `password`, select **Reset
simulator**, then **Confirm reset**. Reset restores the initial catalog and
accounts and clears orders, preparation queues, deliveries, billing, refunds,
and the persisted dispatch queue. Seeded accounts retain their identities and
sessions; accounts created since startup are removed and their sessions revoked.
The dashboard reloads its current view and clears old selections and console
history, retaining the Reset result.

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

## Code Metrics

Phase IV measures the code with [Lizard](https://github.com/terryyin/lizard)
1.26.0. The raw output of every measurement is kept in `docs/metrics/`, one
folder per snapshot (`phase3`, `phase4-refactored`, `phase4`). Each folder's
`RUN.md` records the commit measured, the tool version and the exact command.

Requirements: Python 3.9 or newer.

```bash
pip install lizard==1.26.0
```

Run these from the repository root. Replace `phase3` with the snapshot being
measured, and use the same options every time:

```bash
python -m lizard backend/src/main/java frontend/src -l java -l javascript -l tsx -x "*.test.js" -x "*.test.jsx" -C 15 -Ecognitive -o docs/metrics/phase3/lizard-report.txt
python -m lizard backend/src/main/java frontend/src -l java -l javascript -l tsx -x "*.test.js" -x "*.test.jsx" -C 15 -Ecognitive --csv -o docs/metrics/phase3/lizard-functions.csv
python docs/metrics/per_class.py docs/metrics/phase3
```

- `lizard-report.txt` and `lizard-functions.csv` are the raw tool output.
- `per-class.csv` is derived from them by `per_class.py` (one row per class or
  file).
- `coupling.md` holds the hand-counted coupling of the hotspot classes.

The first command exits with code 1 when Lizard reports warnings (functions
over the complexity thresholds); that is expected. Only production code is
measured: tests are excluded. Lizard writes the files itself (`-o`), so the
commands behave the same in Git Bash and PowerShell.

To measure a tagged snapshot without touching your working copy, check it out
in a separate worktree, run the commands there, then copy its
`docs/metrics/<snapshot>/` folder back to your branch and remove the worktree:

```bash
git worktree add ../mm-phase4 phase4
git worktree remove ../mm-phase4
```

## Running with Docker

Docker runs the backend and the frontend in containers, with no local Java or
Node.js install. Install Docker Desktop, then run this from the repository root:

```bash
docker compose up --build
```

The frontend is served at `http://localhost:5173` and the backend API at
`http://localhost:8080`. The backend only accepts browser requests from
`http://localhost:5173`, so keep that port for the frontend.

Stop the containers with `Ctrl+C`, or `docker compose down`. Accounts, orders and
menus live in memory and reset when the backend container restarts. The FIFO
dispatch queue file is kept in the `backend-data` volume;
`docker compose down -v` deletes it too.

Optional settings (Supabase mode, Google Maps keys) go in a `.env` file at the
repository root. `.env.example` lists every variable. Frontend values are baked
into the build, so rebuild after changing them with `docker compose up --build`.
