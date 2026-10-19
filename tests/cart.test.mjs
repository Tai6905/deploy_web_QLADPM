import { test } from "node:test";
import assert from "node:assert/strict";
import { nextTick } from "vue";
import { loadSource, memoryStorage } from "./load-source.mjs";
const { useCart } = loadSource("src/cart.ts");
const cup = { id: 1, name: "Cup", price: 25000, stock: 4 };
const bag = { id: 2, name: "Bag", price: 50000, stock: 2 };

test("cart keeps products from different catalog pages and searches", () => {
  const cart = useCart();
  cart.add(cup); cart.add(bag);
  cart.refresh([{ ...bag, price: 60000 }]);
  assert.equal(cart.cartItems.value.length, 2);
  assert.equal(cart.cartTotal.value, 85000);
});
test("cart survives reload and enforces stock limits", async () => {
  const storage = memoryStorage();
  const cart = useCart(storage);
  for (let i = 0; i < 4; i++) assert.equal(cart.add(cup), true);
  assert.equal(cart.add(cup), false);
  await nextTick();
  const restored = useCart(storage);
  assert.equal(restored.cart.value[1], 4);
  restored.adjust(1, -4);
  assert.equal(restored.cartTotal.value, 0);
});
test("corrupt browser storage cannot break the storefront", () => {
  const cart = useCart({ getItem: () => "{invalid", setItem() {} });
  assert.equal(cart.cartItems.value.length, 0);
  assert.equal(cart.add(cup), true);
});
test("a stock reduction still lets the customer reduce an oversized cart line", () => {
  const cart = useCart();
  for (let i = 0; i < 4; i++) cart.add(cup);
  cart.refresh([{ ...cup, stock: 1 }]);
  cart.adjust(1, -1);
  assert.equal(cart.cart.value[1], 3);
});
