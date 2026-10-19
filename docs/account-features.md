# Account features

Implemented four roles: shop owner, sales staff, warehouse staff and customer. Only an owner can create, edit, assign one of the two employee roles, lock or unlock employee accounts. These endpoints cannot create another owner or modify an owner through the employee editor.

Personal account features include display name, phone, membership date, password change with current-password verification, and visible logout controls in both the storefront and account pages.

Purchase history is paginated and searchable. Details include purchased items and prices, shipping information, payment state, a delivery progress indicator and timestamped delivery history. Paid/delivered items can be requested for partial return, up to the remaining purchased quantity; the customer sees subsequent return states. Requests still follow owner approval and warehouse restocking rules.

Ownership is enforced by database customer_id and authenticated identity, not by a customer-supplied user ID or unverified email. Trusted sales/owner operations can associate new orders with an existing customer account. Old guest orders are preserved without guessing ownership. No new online schema migration was needed: users.phone, users.created_at and orders.customer_id already exist.

Regression checks cover HTTP role restrictions, rejected privilege escalation, employee session revocation on lock, profile edits, current-password verification, own-order history, and denial of another customer's order detail/return request. Online business fixtures use a rollback transaction. Initial account credentials are kept only in the ignored local setup file.

Verified with 27 backend tests, 14 frontend tests and a successful production frontend build. The PostgreSQL smoke check passed and confirmed that all business fixture rows were rolled back. With both servers running, `node backend/tools/VerifyRoleAccess.mjs` verifies all four configured accounts through the frontend proxy, including login, profile, purchase history, role restrictions and logout. It reads credentials from the ignored local file without printing them.
