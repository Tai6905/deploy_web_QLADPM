import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import * as vue from 'vue';
import { loadSource } from './load-source.mjs';
const instruments = loadSource('src/catalog/instruments.ts', { imports: { './instruments.json': { default: JSON.parse(fs.readFileSync('src/catalog/instruments.json', 'utf8')) } } });
class ApiError extends Error { constructor(status) { super('API error'); this.status = status; } }
function page(getProduct) {
  const selected = vue.ref([{ id: 1, name: 'First', image: '' }]);
  const component = loadSource('src/views/ComparePage.vue', { vueSetup: true, imports: {
    vue: { ...vue, watch() {}, onUnmounted() {} },
    '../comparison': { comparison: { selected, remove() {}, clear() {} } },
    '../catalog/instruments': instruments,
    '../api/shopflowApi': { shopflowApi: { getProduct } },
    '../api/httpClient': { ApiError }, '../auth/session': { dataSource: 'api' },
    '../data': { money: String, seedProducts: [] },
  } });
  return { selected, app: component.default.setup({ cartQuantities: {} }, { expose() {}, emit() {} }) };
}
const detail = id => ({ id, name: `Product ${id}`, sku: String(id), categoryName: 'Guitar', listPrice: 1000, productVariants: [{ availableQuantity: 2 }], productImages: [], averageRating: 0, reviewCount: 0, specifications: { instrumentType: 'guitar' } });

test('comparison ignores an older response after selection changes', async () => {
  const pending = [];
  const { app, selected } = page(() => new Promise(resolve => pending.push(resolve)));
  const old = app.load();
  selected.value = [{ id: 2, name: 'Second', image: '' }];
  const fresh = app.load();
  pending[1](detail(2)); await fresh;
  pending[0](detail(1)); await old;
  assert.deepEqual(app.products.value.map(p => p.id), [2]);
  assert.equal(app.loading.value, false);
});

test('comparison retains available products and offers recovery for deleted products', async () => {
  const { app, selected } = page(async id => { if (id === 2) throw new ApiError(404); return detail(id); });
  selected.value.push({ id: 2, name: 'Deleted', image: '' });
  await app.load();
  assert.deepEqual(app.products.value.map(p => p.id), [1]);
  assert.equal(app.failures.value[0].id, 2);
  assert.match(app.failures.value[0].message, /ngừng bán/);
  assert.equal(app.loading.value, false);
});
