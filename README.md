# ShopFlow

ShopFlow includes a Vue storefront and operations workspace backed by a Spring Boot API and PostgreSQL.

The API runs on port `18081` and connects directly to PostgreSQL on Aiven with SSL. Docker is not required.

For a hosted project demo, follow [Vercel frontend + Railway Java backend deployment](docs/deploy-vercel-railway.md). The repository includes deployment configuration; replace the example backend domain in `vercel.json` before deploying.

## Run locally

Requirements: Java 21 or later, Node.js 22, and a network connection for Maven and Aiven.

1. Copy `backend/application-local.properties.example` to `backend/application-local.properties` if the local file is absent.
2. Set `spring.datasource.password` in that local file, or leave `${DB_PASSWORD:}` to enter the password privately in the terminal. This file is ignored by Git; do not put database passwords in `VITE_*` variables.
3. Start the backend from the workspace root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1
```

To create the first shop owner, use a new email address and enter its password when prompted:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1 -BootstrapOwner
```

Existing accounts are preserved. Registration on the website creates customers; an owner can create sales and warehouse employees from the staff screen.

Connection settings are in `backend/src/main/resources/application.yml` and the ignored local properties file. The configured Aiven service is `qldapm-261-hcmut-ltw.e.aivencloud.com:28028/defaultdb`, username `avnadmin`, with `sslmode=require`. A fully validated server certificate can be configured with `sslmode=verify-full` and the Aiven CA file in the JDBC URL. The connection pool defaults to 5 connections.

The API is available at `http://localhost:18081`. OpenAPI JSON is at `/v3/api-docs` and Swagger UI is at `/docs`. On startup the ordered schema initializer applies only pending scripts in a transaction; review changes before starting against a shared database. Hibernate never recreates the online schema (`ddl-auto=none`). Demo seed SQL is optional and is not run against Aiven automatically.

## Frontend connection

The Vue frontend proxies API routes to the backend in development and preview. Configure the workspace root `.env.local`:

```env
VITE_DATA_SOURCE=api
VITE_API_BASE_URL=
BACKEND_PROXY_URL=http://127.0.0.1:18081
```

Run `npm.cmd install` if dependencies are absent, then `npm.cmd run dev` from the workspace root and open `http://localhost:5173`. Keep the same hostname while logging in so browser cookies remain associated with the session. `npm.cmd run build` and `npm.cmd run preview` serve the production build on `http://localhost:4173` with the same API proxy. For deployment, configure a reverse proxy for the API routes; Vite's proxy is not embedded in exported static files. For a separate frontend origin set `VITE_API_BASE_URL` and explicitly list its origin in the backend `CORS_ALLOWED_ORIGINS` environment variable.

API mode is the default. Set `VITE_DATA_SOURCE=demo` only for an explicitly disconnected demo. API mode uses server-backed registration/login, session restore/logout, the storefront catalog, owner product/category management, orders/payments, dashboard, inventory, receiving, delivery, returns, and employee-account creation.

The backend auth routes are `/api/auth/register`, `/api/auth/login`, `/api/auth/session`, and `/api/auth/logout`; owner-only staff creation uses `POST /api/employees`. Session writes use the `JSESSIONID` cookie and `X-XSRF-TOKEN` header. The session response provides `csrfToken`; the frontend obtains it before protected writes and refreshes it after login. A second login expires the older session. Product and order listing are zero-based (`page=0`) and return `{ items, page, size, totalItems, totalPages }`; both support `q` search across all matching records. Prices are whole VND amounts. Public product JSON omits cost prices.

Product and category management are restricted to shop owners. Product creation requires an existing category and creates a default variant with zero available stock; stock can then be managed through inventory operations.

## API examples

```bash
curl 'http://localhost:18081/products?page=0&size=20&minPrice=100&maxPrice=1000&sort=priceAsc'
curl 'http://localhost:18081/products/1'
curl 'http://localhost:18081/categories?page=0&size=20'
```

Writes require an appropriate authenticated role and CSRF header. The current payment endpoint is a simulator for the project and does not process bank/card transactions.

## Verification

```powershell
npm.cmd test
npm.cmd run build
cd backend
.\mvnw.cmd test
```

The backend test suite uses an isolated in-memory H2 database and does not read local connection secrets. It covers real HTTP registration/login/logout, CSRF, concurrent sessions, CORS, and protected routes. Frontend regressions cover cart persistence, API error handling, delivery, warehouse returns and stale search responses. `backend/tools/DatabaseCheck.java` checks online connectivity read-only; `backend/tools/OnlineSmoke.java` applies pending schema migrations and exercises business operations inside a rollback transaction, and should only be run after approving online schema updates.

## Accounts and purchase history

| Role | Screens and permissions |
| --- | --- |
| SHOP_OWNER | Dashboard, catalog/categories, all orders, warehouse operations, return approval, create/edit/lock sales and warehouse accounts |
| SALES_STAFF | Orders and deliveries; no catalog writes, staff administration or stock adjustment |
| WAREHOUSE | Inventory, receiving, deliveries and restocking approved returns |
| CUSTOMER | Storefront, personal profile, own purchase history, delivery tracking and requests to return eligible purchased items |

All authenticated roles can use `/profile` and `/purchases`, and have logout controls. Profile edits allow display name and phone; role and login email are controlled separately. Changing a password requires the current password and ends existing sessions. Locking an employee or changing their role revokes their active sessions.

New authenticated customer purchases are associated with the session's user ID, regardless of contact email entered. A shop owner or sales employee can record a purchase for an existing active customer by entering that customer's account email. Guest/legacy orders are not automatically claimed from matching email text. The personal history/detail/return APIs always verify order ownership; guest reference lookup additionally requires the matching receiver phone.

`backend/accounts.local.properties` is an ignored local setup file. `backend/tools/ProvisionAccounts.java` provisions only its four configured accounts in a transaction. It preserves unrelated accounts and refuses to overwrite an existing account with a different role/password. Never commit the local setup file or expose it through frontend environment variables. Website-created employee passwords are set by the owner, and employees can change their own passwords in the profile screen.

The three task-specific release reports and PDF evidence are under `docs/release/`.

## Naming conventions

Java packages use lowercase names. Java classes, records and test classes use `UpperCamelCase`,
with filenames matching their public type; methods and variables use `lowerCamelCase`.

Files under `db/migrations/` use purpose-only names such as
`create_catalog_tables.sql` and `create_catalog_api_indexes.sql`. The ordered list in
`DatabaseSchemaInitializer` controls dependency order, and the `schema_migrations` table records
which scripts have already run. This keeps database filenames focused on what they create instead
of exposing an implementation version prefix.
