<script setup lang="ts">
import { comparison } from '../comparison';
import ProductImage from './ProductImage.vue';
const { selected, remove, clear } = comparison;
</script>
<template>
  <template v-if="selected.length">
    <div class="comparison-spacer" aria-hidden="true"></div>
    <aside class="comparison-tray" aria-label="Sản phẩm đã chọn so sánh">
      <div class="comparison-tray-heading"><strong>So sánh nhạc cụ</strong><span aria-live="polite">{{ selected.length }}/3 sản phẩm</span></div>
      <ul><li v-for="item in selected" :key="item.id"><ProductImage :src="item.image" :alt="item.name" /><span>{{ item.name }}</span><button type="button" :aria-label="`Bỏ ${item.name} khỏi so sánh`" @click="remove(item.id)">×</button></li></ul>
      <div class="comparison-tray-actions"><RouterLink to="/compare" class="button button-primary">{{ selected.length >= 2 ? 'So sánh ngay' : 'Xem lựa chọn' }}</RouterLink><button type="button" class="text-link" @click="clear">Bỏ tất cả</button></div>
    </aside>
  </template>
</template>
