import { ref } from 'vue';

export type ComparisonItem = { id: number; name: string; image: string };
export function createComparison(storage: Pick<Storage, 'getItem' | 'setItem'> | null) {
  const key = 'shopflow.compare.v1';
  const selected = ref<ComparisonItem[]>([]);
  try {
    const saved: unknown = JSON.parse(storage?.getItem(key) || '[]');
    if (Array.isArray(saved)) selected.value = saved.filter((item, index, all) => item && Number.isSafeInteger(item.id) && item.id > 0 && typeof item.name === 'string' && typeof item.image === 'string' && all.findIndex(other => other?.id === item.id) === index).slice(0, 3);
  } catch { /* Storage may be unavailable; comparison still works in memory. */ }
  function save() { try { storage?.setItem(key, JSON.stringify(selected.value)); } catch { /* Keep current selection. */ } }
  function remove(id: number) { selected.value = selected.value.filter(item => item.id !== id); save(); }
  function toggle(product: ComparisonItem): string {
    if (selected.value.some(item => item.id === product.id)) { remove(product.id); return ''; }
    if (selected.value.length >= 3) return 'Bạn chỉ có thể so sánh tối đa 3 sản phẩm. Hãy bỏ một sản phẩm trước.';
    if (!Number.isSafeInteger(product.id) || product.id <= 0) return 'Sản phẩm không hợp lệ.';
    selected.value = [...selected.value, { id: product.id, name: product.name, image: product.image }];
    save(); return '';
  }
  function clear() { selected.value = []; save(); }
  return { selected, toggle, remove, clear, contains: (id: number) => selected.value.some(item => item.id === id) };
}
let storage: Storage | null = null;
try { storage = window.localStorage; } catch { /* No persistent storage. */ }
export const comparison = createComparison(storage);
