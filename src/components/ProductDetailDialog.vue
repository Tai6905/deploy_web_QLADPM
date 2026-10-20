<script setup lang="ts">
import { ref, watch } from "vue";
import { X } from "lucide-vue-next";
import { money, type Product } from "../data";
import { shopflowApi, type ProductDetail } from "../api/shopflowApi";

const props = defineProps<{ product: Product }>();
const emit = defineEmits<{ close: [] }>();
const detail = ref<ProductDetail | null>(null);
const loading = ref(false);
const error = ref("");

watch(
  () => props.product.id,
  async (id, _previous, onCleanup) => {
    let active = true;
    onCleanup(() => { active = false; });
    loading.value = true;
    error.value = "";
    detail.value = null;
    try {
      const result = await shopflowApi.getProduct(id);
      if (active) detail.value = result;
    } catch (cause) {
      if (active) error.value = cause instanceof Error ? cause.message : "Không tải được chi tiết sản phẩm.";
    } finally {
      if (active) loading.value = false;
    }
  },
  { immediate: true },
);
</script>

<template>
  <div class="modal-backdrop" @click.self="emit('close')" @keydown.esc="emit('close')">
    <section class="product-detail-dialog" role="dialog" aria-modal="true" aria-labelledby="product-detail-title">
      <button class="icon-button product-detail-close" aria-label="Đóng" @click="emit('close')"><X :size="18" /></button>
      <div v-if="loading" class="product-detail-state">Đang tải chi tiết sản phẩm…</div>
      <div v-else-if="error" class="product-detail-state api-error-banner" role="alert">{{ error }}</div>
      <template v-else-if="detail">
        <div class="product-detail-layout">
          <div class="product-detail-gallery">
            <img v-if="detail.productImages.length" :src="detail.productImages.find((image) => image.primary)?.imageUrl || detail.productImages[0]?.imageUrl" :alt="detail.name" />
            <div v-else class="product-detail-no-image">{{ detail.categoryName }}</div>
            <div v-if="detail.productImages.length > 1" class="product-detail-thumbnails">
              <img v-for="image in detail.productImages" :key="image.id" :src="image.imageUrl" :alt="detail.name" />
            </div>
          </div>
          <div class="product-detail-copy">
            <span class="auth-kicker">{{ detail.categoryName }} · {{ detail.sku }}</span>
            <h2 id="product-detail-title">{{ detail.name }}</h2>
            <p class="product-detail-price">{{ money(detail.listPrice) }}</p>
            <p class="product-detail-description">{{ detail.description || "Chưa có mô tả sản phẩm." }}</p>
            <RouterLink class="button button-outline" :to="`/product/${detail.id}`" @click="emit('close')">Mở trang sản phẩm / chỉnh sửa nội dung →</RouterLink>
            <h3>Phiên bản và tồn khả dụng</h3>
            <div v-if="detail.productVariants.length" class="variant-list">
              <div v-for="variant in detail.productVariants" :key="variant.id" class="variant-row">
                <span>{{ [variant.color, variant.size].filter(Boolean).join(" · ") || `Phiên bản ${variant.id}` }}</span>
                <strong :class="{ 'variant-out-of-stock': variant.availableQuantity <= 0 }">
                  {{ variant.availableQuantity }} còn hàng
                </strong>
              </div>
            </div>
            <p v-else class="muted-cell">Sản phẩm chưa có phiên bản.</p>
          </div>
        </div>
      </template>
    </section>
  </div>
</template>
