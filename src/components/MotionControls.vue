<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { ArrowUp, Pause, Play } from "lucide-vue-next";

const paused = ref(false);
const reduced = ref(false);
const scrolled = ref(false);
const progress = ref(0);
const stopped = computed(() => paused.value || reduced.value);
const preferenceKey = "shopflow.motion-paused.v1";
let preference: MediaQueryList | undefined;
let frame = 0;

function applyPreference() {
  document.documentElement.dataset.motion = stopped.value ? "paused" : "active";
  window.dispatchEvent(new Event("shopflow:motion-change"));
}
function syncSystemPreference() {
  reduced.value = preference?.matches ?? false;
  applyPreference();
}
function toggleMotion() {
  if (reduced.value) return;
  paused.value = !paused.value;
  try { localStorage.setItem(preferenceKey, String(paused.value)); } catch { /* Optional preference storage. */ }
  applyPreference();
}
function updateScroll() {
  frame = 0;
  const distance = document.documentElement.scrollHeight - window.innerHeight;
  progress.value = distance > 0 ? Math.min(1, Math.max(0, window.scrollY / distance)) : 0;
  scrolled.value = window.scrollY > 480;
}
function queueScroll() { if (!frame) frame = window.requestAnimationFrame(updateScroll); }
function scrollToTop() { window.scrollTo({ top: 0, behavior: stopped.value ? "instant" : "smooth" }); }
let resizeObserver: ResizeObserver | undefined;
onMounted(() => {
  try { paused.value = localStorage.getItem(preferenceKey) === "true"; } catch { /* Use default. */ }
  preference = window.matchMedia("(prefers-reduced-motion: reduce)");
  syncSystemPreference();
  preference.addEventListener("change", syncSystemPreference);
  window.addEventListener("scroll", queueScroll, { passive: true });
  window.addEventListener("resize", queueScroll);
  if ("ResizeObserver" in window) {
    resizeObserver = new ResizeObserver(queueScroll);
    resizeObserver.observe(document.body);
  }
  updateScroll();
});
onUnmounted(() => {
  window.removeEventListener("scroll", queueScroll);
  window.removeEventListener("resize", queueScroll);
  preference?.removeEventListener("change", syncSystemPreference);
  resizeObserver?.disconnect();
  if (frame) window.cancelAnimationFrame(frame);
  delete document.documentElement.dataset.motion;
});
</script>

<template>
  <div class="reading-progress" :style="{ transform: `scaleX(${progress})` }" aria-hidden="true"></div>
  <div class="experience-controls" aria-label="Tùy chọn hiển thị">
    <button type="button" class="motion-toggle" :aria-pressed="stopped" :disabled="reduced" :title="reduced ? 'Đã giảm chuyển động theo cài đặt thiết bị' : stopped ? 'Bật hiệu ứng chuyển động' : 'Tạm dừng hiệu ứng chuyển động'" @click="toggleMotion">
      <Pause v-if="!stopped" :size="14" /><Play v-else :size="14" /><span>{{ stopped ? 'Đã tắt hiệu ứng' : 'Hiệu ứng' }}</span>
    </button>
    <Transition name="back-top"><button v-if="scrolled" type="button" class="back-to-top" aria-label="Cuộn lên đầu trang" title="Lên đầu trang" @click="scrollToTop"><ArrowUp :size="19" /></button></Transition>
  </div>
</template>
