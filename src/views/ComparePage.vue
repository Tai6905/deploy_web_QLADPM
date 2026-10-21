<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';
import { comparison } from '../comparison';
import { comparisonSpecRows, specificationLabel } from '../catalog/instruments';
import { shopflowApi } from '../api/shopflowApi';
import { ApiError } from '../api/httpClient';
import { dataSource } from '../auth/session';
import { money, seedProducts, type Product } from '../data';
import StorefrontNav from '../components/StorefrontNav.vue';
import ProductImage from '../components/ProductImage.vue';
const props = defineProps<{ cartQuantities: Record<number, number> }>();
const emit = defineEmits<{ add: [product: Product] }>();
const { selected, remove, clear } = comparison;
const products = ref<Product[]>([]), failures = ref<{ id: number; name: string; message: string }[]>([]);
const loading = ref(false), error = ref(''), onlyDifferences = ref(false), announcement = ref('');
let generation = 0;
async function load() {
  const id = ++generation;
  const selection = [...selected.value];
  products.value = []; failures.value = []; loading.value = selection.length > 0;
  try {
    if (dataSource === 'demo') {
      let source: Product[] = seedProducts;
      try { const saved = JSON.parse(localStorage.getItem('shopflow.products') || 'null'); if (Array.isArray(saved)) source = saved; } catch { /* Use demo products. */ }
      products.value = selection.flatMap(item => source.find(p => p.id === item.id) || []);
      failures.value = selection.filter(item => !source.some(p => p.id === item.id)).map(item => ({ ...item, message: 'Sản phẩm không còn trong danh mục.' }));
      return;
    }
    const results = await Promise.allSettled(selection.map(item => shopflowApi.getProduct(item.id)));
    if (id !== generation) return;
    results.forEach((result, index) => {
      if (result.status === 'rejected') {
        failures.value.push({ ...selection[index]!, message: result.reason instanceof ApiError && result.reason.status === 404 ? 'Sản phẩm đã ngừng bán hoặc không còn tồn tại.' : 'Chưa tải được sản phẩm. Bạn có thể thử lại.' });
        return;
      }
      const p = result.value;
      products.value.push({ id: p.id, name: p.name, sku: p.sku, categoryId: p.categoryId, category: p.categoryName,
        price: p.listPrice, stock: p.productVariants.reduce((sum, variant) => sum + variant.availableQuantity, 0),
        image: p.productImages.find(image => image.primary)?.imageUrl || p.productImages[0]?.imageUrl || '',
        averageRating: p.averageRating, reviewCount: p.reviewCount, specifications: p.specifications, color: 'sage', threshold: 0 });
    });
  } finally { if (id === generation) loading.value = false; }
}
const rows = computed(() => {
  const basic = [
    { key: 'price', label: 'Giá bán', cells: products.value.map(p => money(p.price)) },
    { key: 'category', label: 'Danh mục', cells: products.value.map(p => p.category) },
    { key: 'type', label: 'Nhóm nhạc cụ', cells: products.value.map(p => specificationLabel('instrumentType', p.specifications?.instrumentType)) },
    { key: 'stock', label: 'Tồn kho khả dụng', cells: products.value.map(p => p.stock > 0 ? `Còn ${p.stock} sản phẩm` : 'Hết hàng') },
    { key: 'rating', label: 'Đánh giá', cells: products.value.map(p => p.reviewCount ? `${p.averageRating?.toFixed(1)} / 5 · ${p.reviewCount} đánh giá` : 'Chưa có đánh giá') },
  ].map(row => ({ ...row, different: new Set(row.cells).size > 1 }));
  return [...basic, ...comparisonSpecRows(products.value)];
});
const visibleRows = computed(() => rows.value.filter(row => products.value.length < 2 || !onlyDifferences.value || row.different));
function add(product: Product) { emit('add', product); announcement.value = `Đã thêm ${product.name} vào giỏ hàng.`; }
watch(() => selected.value.map(item => item.id).join(','), load, { immediate: true });
onUnmounted(() => { ++generation; });
</script>
<template>
  <main class="commerce-page"><StorefrontNav v-model:error="error" />
    <div class="commerce-container compare-container">
      <div class="compare-heading"><div><span class="auth-kicker">SHOPFLOW · CHỌN ĐÚNG ÂM SẮC</span><h1>So sánh nhạc cụ</h1><p>Đặt tối đa 3 sản phẩm cạnh nhau để chọn theo nhu cầu của bạn.</p></div><RouterLink to="/customer" class="button button-outline">Chọn thêm sản phẩm →</RouterLink></div>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p><p class="comparison-announcement" role="status">{{ announcement }}</p>
      <div v-if="!selected.length" class="compare-empty"><h2>Bạn chưa chọn sản phẩm</h2><p>Bấm “So sánh” ở thẻ sản phẩm hoặc trang chi tiết để bắt đầu.</p><RouterLink to="/customer" class="button button-primary">Khám phá nhạc cụ</RouterLink></div>
      <p v-else-if="loading" class="empty-state" role="status">Đang tải giá và thông số mới nhất…</p>
      <template v-else>
        <div v-if="failures.length" class="compare-failures" role="alert"><div v-for="item in failures" :key="item.id"><strong>{{ item.name }}</strong><p>{{ item.message }}</p><button type="button" class="text-link" @click="remove(item.id)">Bỏ sản phẩm</button></div><button type="button" class="button button-outline" @click="load">Thử tải lại</button></div>
        <p v-if="products.length === 1" class="instrument-hint">Chọn thêm một sản phẩm để xem các điểm khác nhau.</p>
        <template v-if="products.length">
          <div class="compare-controls"><label><input v-model="onlyDifferences" type="checkbox" :disabled="products.length < 2" />Chỉ xem điểm khác nhau</label><button type="button" class="text-link" @click="clear">Bỏ tất cả</button></div>
          <div class="compare-table-scroll" role="region" aria-label="Bảng so sánh sản phẩm, cuộn ngang để xem thêm" tabindex="0">
            <table class="compare-table" :style="{ minWidth: `${180 + products.length * 260}px` }"><caption class="sr-only">Giá bán và thông số của {{ products.length }} nhạc cụ đã chọn</caption><thead><tr><th scope="col">Sản phẩm</th><th v-for="product in products" :key="product.id" scope="col"><div class="compare-product"><button type="button" class="compare-remove" :aria-label="`Bỏ ${product.name} khỏi so sánh`" @click="remove(product.id)">×</button><RouterLink :to="`/product/${product.id}`"><ProductImage :src="product.image" :alt="product.name" /><h2>{{ product.name }}</h2></RouterLink><small>{{ product.sku }}</small><button type="button" class="button button-primary" :disabled="product.stock <= (props.cartQuantities[product.id] || 0)" @click="add(product)">{{ product.stock > 0 ? 'Thêm vào giỏ' : 'Hết hàng' }}</button><RouterLink :to="`/product/${product.id}`" class="text-link">Xem chi tiết →</RouterLink></div></th></tr></thead><tbody><tr v-for="row in visibleRows" :key="row.key" :class="{ 'comparison-different': products.length > 1 && row.different }"><th scope="row">{{ row.label }}</th><td v-for="(cell, index) in row.cells" :key="products[index]!.id">{{ cell }}</td></tr><tr v-if="!visibleRows.length"><td :colspan="products.length + 1">Các thông số hiện có giống nhau.</td></tr></tbody></table>
          </div><p class="instrument-hint">Ô tô màu thể hiện thông tin khác nhau. “Chưa cập nhật” là thông tin còn thiếu; “Không áp dụng” là thông số của nhóm nhạc cụ khác.</p>
        </template>
      </template>
    </div>
  </main>
</template>
