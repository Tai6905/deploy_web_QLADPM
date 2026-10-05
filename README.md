# ShopFlow Frontend

Frontend demo for a small shop's sales and inventory operations. Built with Vue 3, TypeScript, Vite, Vue Router, and Lucide icons.

## Run locally

```sh
npm install
npm run dev
```

Open `http://localhost:5173`. To verify the production bundle, run `npm run build`.

## Run against the ShopFlow API

Copy `.env.example` to `.env.local`, then set:

```env
VITE_DATA_SOURCE=api
VITE_API_BASE_URL=/api
```

The Vite development server proxies `/api/*` to `http://localhost:8080/*`. Set `VITE_API_BASE_URL` to the backend origin if the API is hosted elsewhere. API mode restores `/auth/session`, uses the backend's `HttpOnly` session cookie, sends the `XSRF-TOKEN` cookie as `X-XSRF-TOKEN` on writes, authenticates through `/auth/login`, loads the storefront catalog, and sends storefront checkout to `POST /orders` using the documented request shape. The demo role picker is disabled in API mode.

## Reference app analysis

The attached ShopFlow frontend is a Vue 3 feature-oriented app. Its main architecture is:

- `src/router`: route registration and role-aware navigation guards.
- `src/layouts` and `src/components/layout`: shared workspace shell and navigation.
- `src/views`: separate catalog, inventory, delivery, returns, and authentication screens.
- `src/api`: typed Axios clients organized by business domain.
- `src/stores` and `src/plugins`: session/app-shell state and Vue Query cache.
- `src/i18n`: Vietnamese and English translations.

The main workflow spans product catalog, customer orders and simulated payment, inventory adjustment and receiving, delivery updates, returns, and low-stock alerts. Protected warehouse/owner pages are separated from the public customer storefront. The source already includes form validation, error handling, and API contracts, so the production frontend is designed to connect to a separate backend rather than fabricate persistent server behavior.

## This implementation

This workspace was empty, so this is a standalone UI scaffold rather than a modification of either attached repository. It combines the existing operations-first ShopFlow layout with the second frontend's Sora/Manrope typography and its typed API/service-boundary approach. It includes the operations overview, product catalog, order management, inventory, receiving, delivery, returns, a public customer storefront/cart, login, and a forbidden page. Demo actions create products/orders/return requests, receive stock, progress order statuses, process return lifecycle actions, and export CSV files. Demo data persists in browser `localStorage` under `shopflow.*` keys; clearing those keys restores the seed data.

## Roles and pagination

`src/auth/permissions.ts` defines the route/action matrix and `src/main.ts` applies it in a Vue Router guard. Owner can access all operations; warehouse can access inventory, receiving, delivery, and returns but cannot view sales totals, manage orders/catalog, or approve/reject returns; customers and guests can use the public storefront. In demo mode, use the role selector in the header. These client checks are for UX only: the Spring Boot API must remain the security boundary and enforce the same roles.

Tables use client-side pagination with `?page=` and `?pageSize=` (5, 10, or 25), so a filtered page can be bookmarked/shared. The current ShopFlow API specifications return unpaginated arrays; switch to server-side pagination in the adapter when the backend adds page/size support.

## API integration points

- `src/api/httpClient.ts`: base URL, credentials, CSRF token, and normalized API errors.
- `src/api/shopflowApi.ts`: replaceable `ShopflowApi` contract, typed DTOs, and endpoint paths matching the repository's API specs.
- `src/auth/session.ts`: demo/API session mode and session restoration.
- `src/auth/permissions.ts`: frontend route/action permissions.
- `.env.example`: API base URL and data-source switch.

The GitHub backend currently has no endpoint for a dashboard summary, an order list, or catalog CRUD. Those views remain demo-backed until those APIs exist; the adapter deliberately does not invent endpoints for them. The API adapter has typed operations for inventory, receiving, deliveries, and returns too, but the corresponding workspace tables still use demo state until their backend DTOs are mapped into the view models. See the canonical [ShopFlow repository](https://github.com/hoangtuan2k5/shopflow) and its `docs/api-*-spec.md` contracts before changing request shapes.
