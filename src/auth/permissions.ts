import type { UserRole } from "../api/shopflowApi";

export type AppPage =
  | "overview"
  | "products"
  | "orders"
  | "inventory"
  | "delivery"
  | "receiving"
  | "returns"
  | "staff"
  | "profile"
  | "purchases"
  | "customer";

const pageRoles: Record<AppPage, UserRole[]> = {
  overview: ["SHOP_OWNER"],
  products: ["SHOP_OWNER"],
  orders: ["SHOP_OWNER", "SALES_STAFF"],
  inventory: ["WAREHOUSE", "SHOP_OWNER"],
  delivery: ["WAREHOUSE", "SHOP_OWNER", "SALES_STAFF"],
  receiving: ["WAREHOUSE", "SHOP_OWNER"],
  returns: ["WAREHOUSE", "SHOP_OWNER"],
  staff: ["SHOP_OWNER"],
  customer: ["CUSTOMER", "WAREHOUSE", "SHOP_OWNER", "SALES_STAFF"],
  profile: ["CUSTOMER", "WAREHOUSE", "SHOP_OWNER", "SALES_STAFF"],
  purchases: ["CUSTOMER", "WAREHOUSE", "SHOP_OWNER", "SALES_STAFF"],
};

export function canAccessPage(page: string, role: UserRole | null) {
  if (page === "about" || page === "guide" || /^product\/\d+$/.test(page)) return true;
  if (page === "login" || page === "forbidden") return true;
  return (pageRoles[page as AppPage] ?? []).includes(role as UserRole);
}

export function landingPage(role: UserRole | null) {
  if (role === "SHOP_OWNER") return "/overview";
  if (role === "WAREHOUSE") return "/inventory";
  if (role === "SALES_STAFF") return "/orders";
  return "/customer";
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
  if (action === "updateDelivery") return role === "WAREHOUSE" || role === "SHOP_OWNER" || role === "SALES_STAFF";
  if (action === "manageOrders") return role === "SHOP_OWNER" || role === "SALES_STAFF";
  if (action === "receiveStock" || action === "restockReturn")
    return role === "WAREHOUSE" || role === "SHOP_OWNER";
  if (
    action === "manageCatalog" ||
    action === "approveReturn"
  )
    return role === "SHOP_OWNER";
  return false;
}
