import { test } from "node:test";
import assert from "node:assert/strict";
import * as vue from "vue";
import fs from 'node:fs';
import { loadSource, memoryStorage } from "./load-source.mjs";
const instruments = loadSource('src/catalog/instruments.ts', { imports: { './instruments.json': { default: JSON.parse(fs.readFileSync('src/catalog/instruments.json', 'utf8')) } } });
const comparison = loadSource('src/comparison.ts');

function workspace(api, role = "SHOP_OWNER") {
  globalThis.localStorage = memoryStorage();
  const watches = [];
  const route = vue.reactive({ path: "/customer", params: {}, query: {} });
  const session = { dataSource: "api", currentRole: vue.ref(role), currentUser: vue.ref(null) };
  const setup = loadSource("src/App.vue", { vueSetup: true, imports: {
    vue: { ...vue, watch: (...args) => watches.push(args), onUnmounted() {} },
    "vue-router": { useRoute: () => route, useRouter: () => ({ replace: async () => {}, push: async () => {} }) },
    "lucide-vue-next": {}, "./cart": loadSource("src/cart.ts"),
    "./data": { money: String, seedProducts: [], seedOrders: [], seedReturns: [] },
    "./api/shopflowApi": { shopflowApi: api, roleLabels: {}, paginate: (items) => ({ items }) },
    "./auth/session": session,
    "./auth/permissions": loadSource("src/auth/permissions.ts"),
    "./api/viewModels": loadSource("src/api/viewModels.ts"),
    './catalog/instruments': instruments,
    './comparison': comparison,
  } });
  return { app: setup.default.setup({}, { expose() {} }), watches, route };
}
test("paid orders can advance from NONE to PREPARING", async () => {
  const calls = [];
  const { app } = workspace({ updateDelivery: async (id, status) => {
    calls.push(status);
    return { orderId: id, deliveryStatus: status, createdAt: new Date().toISOString(), items: [] };
  } });
  await app.advanceOrder({ apiId: 1, deliveryStatus: "NONE", payment: "Đã thanh toán" });
  assert.deepEqual(calls, ["PREPARING"]);
});

test("unpaid COD orders can enter preparation while unpaid card orders cannot", async () => {
  const calls=[];
  const {app}=workspace({updateDelivery:async(id,status)=>{calls.push(status);return {orderId:id,orderStatus:'PENDING_PAYMENT',paymentMethod:'COD',deliveryStatus:status,createdAt:new Date().toISOString(),items:[]};}});
  await app.advanceOrder({apiId:1,deliveryStatus:'NONE',payment:'Chờ thanh toán',paymentMethod:'CARD'});
  assert.equal(calls.length,0);
  await app.advanceOrder({apiId:1,deliveryStatus:'NONE',payment:'Chờ thanh toán',paymentMethod:'COD'});
  assert.deepEqual(calls,['PREPARING']);
});
test("warehouse returns load without calling owner-only eligible orders", async () => {
  let ownerApiCalled = false;
  const { app, watches } = workspace({
    getReturns: async () => [],
    getReturnableOrders: async () => { ownerApiCalled = true; throw new Error("Forbidden"); },
  }, "WAREHOUSE");
  const loader = watches.find(([sources]) => Array.isArray(sources) && sources.length === 4)[1];
  await loader(["returns"]);
  assert.equal(ownerApiCalled, false);
  assert.equal(app.apiWorkspaceError.value, "");
});
test("slower older catalog response cannot overwrite newer search results", async () => {
  const pending = [];
  const { app, watches } = workspace({
    getProducts: () => new Promise((resolve) => pending.push(resolve)),
    getCategories: async () => ({ items: [] }),
  });
  const loader = watches.find(([sources]) => Array.isArray(sources) && sources.length === 11)[1];
  const first = loader(["customer"]); const second = loader(["customer"]);
  const page = (id) => ({ items: [{ id, name: String(id), listPrice: 1, availableQuantity: 1 }], totalPages: 1 });
  pending[1](page(2)); await second;
  pending[0](page(1)); await first;
  assert.equal(app.products.value[0].id, 2);
});

test('instrument filters reach the catalog API and clear with the other storefront filters', async () => {
  let query;
  const { app, watches } = workspace({ getProducts: async value => { query = value; return { items: [], totalPages: 0 }; }, getCategories: async () => ({ items: [] }) });
  app.instrumentFilters.value = { instrumentType: 'violin', violinSize: '1/2' };
  const loader = watches.find(([sources]) => Array.isArray(sources) && sources.length === 11)[1];
  await loader(['customer']);
  assert.deepEqual(query.specifications, { instrumentType: 'violin', violinSize: '1/2' });
  app.resetStoreFilters();
  assert.deepEqual(app.instrumentFilters.value, {});
});
