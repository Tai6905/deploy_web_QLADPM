import { computed, ref, watch } from "vue";
import type { Product } from "./data";

/** Cart snapshots stay independent of the current catalog page and search. */
export function useCart(storage?: Storage, key = "shopflow.cart.v1") {
  const cart = ref<Record<number, number>>({});
  const snapshots = ref<Record<number, Product>>({});
  try {
    const saved = JSON.parse(storage?.getItem(key) || "null");
    if (saved?.version === 1 && Array.isArray(saved.items)) {
      for (const { product, quantity } of saved.items) {
        if (product && Number.isSafeInteger(product.id) && product.id > 0 &&
            typeof product.name === "string" && Number.isFinite(product.price) && product.price >= 0 &&
            Number.isSafeInteger(product.stock) && product.stock >= 0 &&
            Number.isSafeInteger(quantity) && quantity > 0) {
          cart.value[product.id] = quantity;
          snapshots.value[product.id] = product;
        }
      }
    }
  } catch { /* Ignore corrupt or unavailable browser storage. */ }

  const cartItems = computed(() => Object.entries(cart.value).flatMap(([id, quantity]) => {
    const product = snapshots.value[Number(id)];
    return product ? [{ product, quantity }] : [];
  }));
  const cartTotal = computed(() => cartItems.value.reduce((sum, item) => sum + item.product.price * item.quantity, 0));
  watch([cart, snapshots], () => {
    try { storage?.setItem(key, JSON.stringify({ version: 1, items: cartItems.value })); }
    catch { /* A full storage quota must not prevent checkout. */ }
  }, { deep: true });

  function add(product: Product, amount = 1) {
    const quantity = cart.value[product.id] || 0;
    if (!Number.isSafeInteger(amount) || amount < 1 || quantity + amount > product.stock) return false;
    snapshots.value[product.id] = { ...product };
    cart.value[product.id] = quantity + amount;
    return true;
  }
  function adjust(productId: number, delta: number) {
    const quantity = (cart.value[productId] || 0) + delta;
    if (quantity <= 0) {
      delete cart.value[productId];
      delete snapshots.value[productId];
    } else if (delta < 0 || quantity <= (snapshots.value[productId]?.stock ?? 0)) {
      cart.value[productId] = quantity;
    }
  }
  function refresh(items: Product[]) {
    for (const product of items) {
      if (cart.value[product.id]) snapshots.value[product.id] = { ...product };
    }
  }
  function clear() { cart.value = {}; snapshots.value = {}; }
  function removeSkus(skus: readonly string[] = []) {
    const retired = new Set(skus.map(sku => sku.toUpperCase()));
    for (const product of Object.values(snapshots.value)) {
      if (retired.has((product.sku || '').toUpperCase())) adjust(product.id, -(cart.value[product.id] || 0));
    }
  }
  return { cart, cartItems, cartTotal, add, adjust, refresh, clear, removeSkus };
}
