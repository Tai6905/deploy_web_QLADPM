import { test } from "node:test";
import assert from "node:assert/strict";
import { loadSource } from "./load-source.mjs";
const { canAccessPage, canPerform, landingPage } = loadSource("src/auth/permissions.ts");
test("sales staff manage orders and delivery but cannot access catalog, staff or warehouse", () => {
  for (const page of ["orders", "delivery", "profile", "purchases"]) assert.equal(canAccessPage(page, "SALES_STAFF"), true);
  for (const page of ["products", "staff", "inventory", "receiving", "overview"]) assert.equal(canAccessPage(page, "SALES_STAFF"), false);
  assert.equal(landingPage("SALES_STAFF"), "/orders");
  assert.equal(canPerform("manageOrders", "SALES_STAFF"), true);
  assert.equal(canPerform("receiveStock", "SALES_STAFF"), false);
});
test("customers can reach personal pages and never employee operations", () => {
  assert.equal(canAccessPage("profile", "CUSTOMER"), true);
  assert.equal(canAccessPage("purchases", "CUSTOMER"), true);
  assert.equal(canAccessPage("staff", "CUSTOMER"), false);
  assert.equal(canAccessPage("orders", "CUSTOMER"), false);
  assert.equal(canAccessPage("purchases", null), false);
});
