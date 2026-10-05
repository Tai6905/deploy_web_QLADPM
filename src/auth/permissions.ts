import type { UserRole } from "../api/shopflowApi";

export type AppPage =
  | "overview"
  | "products"
  | "orders"
  | "inventory"
  | "delivery"
  | "receiving"
  | "returns"
  | "customer";

const pageRoles: Record<AppPage, UserRole[]> = {
  overview: ["SHOP_OWNER"],
  products: ["SHOP_OWNER"],
  orders: ["SHOP_OWNER"],
  inventory: ["WAREHOUSE", "SHOP_OWNER"],
  delivery: ["WAREHOUSE", "SHOP_OWNER"],
  receiving: ["WAREHOUSE", "SHOP_OWNER"],
  returns: ["WAREHOUSE", "SHOP_OWNER"],
  customer: ["CUSTOMER"],
};

export function canAccessPage(page: string, role: UserRole | null) {
  if (page === "customer") return true;
  if (page === "login" || page === "forbidden") return true;
  return (pageRoles[page as AppPage] ?? []).includes(role as UserRole);
}

export function canPerform(
  action:
    | "manageCatalog"
    | "manageOrders"
    | "receiveStock"
    | "updateDelivery"
    | "approveReturn"
    | "restockReturn",
  role: UserRole | null,
) {
  if (
    action === "receiveStock" ||
    action === "updateDelivery" ||
    action === "restockReturn"
  )
    return role === "WAREHOUSE" || role === "SHOP_OWNER";
  if (
    action === "manageCatalog" ||
    action === "manageOrders" ||
    action === "approveReturn"
  )
    return role === "SHOP_OWNER";
  return false;
}
