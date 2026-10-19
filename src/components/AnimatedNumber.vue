<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";

const props = withDefaults(defineProps<{ value: number; format?: (value: number) => string }>(), {
  format: (value: number) => Math.round(value).toLocaleString("vi-VN"),
});
const displayed = ref(props.value);
const target = computed(() => Number.isFinite(props.value) ? props.value : 0);
const label = computed(() => props.format(target.value));
let frame = 0;
let mounted = false;
let preference: MediaQueryList | undefined;
function finish() { cancelAnimationFrame(frame); displayed.value = target.value; }
function animate(from: number) {
  cancelAnimationFrame(frame);
  if (preference?.matches || from === target.value) { finish(); return; }
  const value = target.value;
  const start = performance.now();
  function tick(now: number) {
    const progress = Math.min(1, (now - start) / 850);
    displayed.value = from + (value - from) * (1 - (1 - progress) ** 3);
    if (progress < 1) frame = requestAnimationFrame(tick);
  }
  frame = requestAnimationFrame(tick);
}
watch(target, () => { if (mounted) animate(displayed.value); });
onMounted(() => {
  mounted = true;
  preference = window.matchMedia("(prefers-reduced-motion: reduce)");
  preference.addEventListener("change", finish);
  animate(0);
});
onUnmounted(() => { cancelAnimationFrame(frame); preference?.removeEventListener("change", finish); });
</script>

<template>
  <span class="animated-number"><span aria-hidden="true">{{ format(Math.round(displayed)) }}</span><span class="sr-only">{{ label }}</span></span>
</template>
