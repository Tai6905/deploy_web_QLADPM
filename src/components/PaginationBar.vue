<script setup lang="ts">
defineProps<{
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
}>();

const emit = defineEmits<{
  changePage: [page: number];
  changeSize: [size: number];
}>();
</script>

<template>
  <div class="pagination-controls" aria-label="Phân trang">
    <span
      >{{ total ? (page - 1) * pageSize + 1 : 0 }}–{{
        Math.min(page * pageSize, total)
      }}
      / {{ total }}</span
    >
    <label
      >Hiển thị
      <select
        :value="pageSize"
        aria-label="Số dòng mỗi trang"
        @change="
          emit('changeSize', Number(($event.target as HTMLSelectElement).value))
        "
      >
        <option :value="5">5</option>
        <option :value="10">10</option>
        <option :value="12">12</option>
        <option :value="25">25</option>
        <option :value="50">50</option>
        <option :value="100">100</option>
      </select>
    </label>
    <button
      :disabled="page <= 1"
      aria-label="Trang trước"
      @click="emit('changePage', page - 1)"
    >
      ‹
    </button>
    <span class="pagination-current">{{ page }} / {{ totalPages }}</span>
    <button
      :disabled="page >= totalPages"
      aria-label="Trang tiếp theo"
      @click="emit('changePage', page + 1)"
    >
      ›
    </button>
  </div>
</template>
