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

Install dependencies the first time:

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
npm run build
```
