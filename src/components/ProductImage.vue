<script setup lang="ts">
import { ref, watch } from "vue";
const props = withDefaults(defineProps<{ src?: string | null; alt: string; loading?: 'eager' | 'lazy' }>(), { src: '', loading: 'lazy' });
const failed = ref(false);
watch(() => props.src, () => { failed.value = false; });
</script>
<template><img v-if="src && !failed" :src="src" :alt="alt" :loading="loading" decoding="async" @error="failed = true" /><span v-else class="product-image-placeholder" role="img" :aria-label="`${alt} · ảnh chưa khả dụng`">ShopFlow<br /><small>Ảnh đang cập nhật</small></span></template>
