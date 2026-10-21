import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { loadSource, memoryStorage } from './load-source.mjs';
const schema = JSON.parse(fs.readFileSync('src/catalog/instruments.json', 'utf8'));
const instruments = loadSource('src/catalog/instruments.ts', { imports: { './instruments.json': { default: schema } } });
const { createComparison } = loadSource('src/comparison.ts');

test('switching instrument type clears incompatible filters and selecting all removes a field', () => {
  const guitar = { instrumentType: 'guitar', guitarType: 'acoustic', stringCount: '6' };
  const piano = instruments.setSpecification(guitar, 'instrumentType', 'piano');
  assert.deepEqual(piano, { instrumentType: 'piano' });
  assert.deepEqual(instruments.setSpecification(guitar, 'stringCount', ''), { instrumentType: 'guitar', guitarType: 'acoustic' });
  assert.equal(instruments.matchesSpecifications(guitar, { instrumentType: 'guitar', stringCount: '6' }), true);
  assert.equal(instruments.matchesSpecifications(guitar, { instrumentType: 'piano' }), false);
  assert.equal(instruments.matchesSpecifications(undefined, { instrumentType: 'guitar' }), false);
  assert.equal(instruments.matchesSpecifications(undefined, {}), true);
});

test('comparison persists across reloads, toggles duplicates, and enforces the three product limit', () => {
  const storage = memoryStorage(), model = createComparison(storage);
  const p = id => ({ id, name: `Instrument ${id}`, image: '' });
  for (const id of [1, 2, 3]) assert.equal(model.toggle(p(id)), '');
  assert.ok(model.toggle(p(4)));
  assert.equal(model.selected.value.length, 3);
  const restored = createComparison(storage);
  assert.deepEqual(restored.selected.value.map(p => p.id), [1, 2, 3]);
  restored.toggle(p(2));
  assert.deepEqual(restored.selected.value.map(p => p.id), [1, 3]);
  restored.clear();
  assert.equal(createComparison(storage).selected.value.length, 0);
});

test('broken or unavailable local storage cannot prevent comparison', () => {
  const broken = createComparison({ getItem() { throw Error('blocked'); }, setItem() { throw Error('blocked'); } });
  assert.equal(broken.toggle({ id: 2, name: 'Guitar', image: '' }), '');
  assert.equal(broken.selected.value.length, 1);
  assert.equal(createComparison({ getItem: () => '{broken' }).selected.value.length, 0);
});

test('comparison distinguishes missing information from specifications of another family', () => {
  const rows = instruments.comparisonSpecRows([
    { specifications: { instrumentType: 'guitar', stringCount: '6' } },
    { specifications: { instrumentType: 'guitar' } },
    { specifications: { instrumentType: 'piano', keyCount: '88' } },
  ]);
  const strings = rows.find(row => row.key === 'stringCount');
  assert.equal(strings.cells[0], '6 dây');
  assert.equal(strings.cells[1], 'Chưa cập nhật');
  assert.equal(strings.cells[2], 'Không áp dụng');
  assert.equal(strings.different, true);
  assert.equal(new Set(rows.map(row => row.key)).size, rows.length);
});

test('API sends instrument filters as independent URL parameters along with regular filters', async () => {
  let path;
  const api = loadSource('src/api/shopflowApi.ts', { imports: { './httpClient': { apiRequest: async value => { path = value; return {}; } } } }).shopflowApi;
  await api.getProducts({ page: 2, size: 12, categoryId: 7, minPrice: 100, specifications: { instrumentType: 'violin', violinSize: '1/2' } });
  const query = new URL(path, 'http://example.invalid').searchParams;
  assert.equal(query.get('instrumentType'), 'violin');
  assert.equal(query.get('spec.violinSize'), '1/2');
  assert.equal(query.get('page'), '2');
  assert.equal(query.get('categoryId'), '7');
});
