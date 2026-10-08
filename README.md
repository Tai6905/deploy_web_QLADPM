# ModernShopFlow catalog API

Backend scope for HỒ ANH DŨNG's Sprint 2 tasks:

- Task 43: category CRUD with a `409 Conflict` guard when active products still reference a category.
- Task 47: public product/category listing with `page`, `size`, price filters, sorting, category filter and product search.
- Task 51: product detail with `productVariants`, `productImages` and realtime inventory/stock-hold quantities.

The backend runs on port `18081`. PostgreSQL runs on `55432` so it does not collide with the existing local services.

## Run locally

Requirements: Java 21, Docker and a network connection for Maven dependencies.

```bash
docker compose up -d postgres
cd backend
./mvnw test
./mvnw spring-boot:run
```

Load repeatable demo data in another terminal:

```bash
docker compose exec -T postgres psql -U shopflow -d shopflow < ../db/seed/catalog_demo.sql
```

The API is available at `http://localhost:18081`. OpenAPI JSON is at `/v3/api-docs` and Swagger UI is at `/docs`.

## Frontend connection

The Vue frontend proxies `/api`, `/products`, and `/categories` to `http://localhost:18081` in development. Start the backend and PostgreSQL as above, seed the demo catalog, then configure the workspace root `.env.local`:

```env
VITE_DATA_SOURCE=api
VITE_API_BASE_URL=
```

Run `npm install` and `npm run dev` from the workspace root. API mode connects email registration/login, session restore/logout, paginated product/category reads, category create/update/delete, product detail, search/category/price/sort filters, variant stock availability, and product images. The storefront and owner catalog use database data. The backend does not yet implement cart/order/payment, dashboard, inventory adjustments/receiving, delivery, or return endpoints; those pages show an API-unavailable state instead of presenting local demo data as live. Category management is available from the owner Products page.

The backend auth routes are `/api/auth/register`, `/api/auth/login`, `/api/auth/session`, and `/api/auth/logout`. Session writes use the `JSESSIONID` cookie and CSRF `XSRF-TOKEN` cookie/header. Product listing is zero-based (`page=0`) and returns `{ items, page, size, totalItems, totalPages }`; the FE translates its one-based route pagination to/from that contract.

## API examples

```bash
curl 'http://localhost:18081/products?page=0&size=20&minPrice=100&maxPrice=1000&sort=priceAsc'
curl 'http://localhost:18081/products/1'
curl 'http://localhost:18081/categories?page=0&size=20'
curl -X POST 'http://localhost:18081/categories' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Demo","slug":"demo","description":"temporary category"}'
```

The three task-specific release reports and PDF evidence are under `docs/release/`.

## Naming conventions

Java packages use lowercase names. Java classes, records and test classes use `UpperCamelCase`,
with filenames matching their public type; methods and variables use `lowerCamelCase`.

Files under `db/migrations/` use purpose-only names such as
`create_catalog_tables.sql` and `create_catalog_api_indexes.sql`. The ordered list in
`DatabaseSchemaInitializer` controls dependency order, and the `schema_migrations` table records
which scripts have already run. This keeps database filenames focused on what they create instead
of exposing an implementation version prefix.
