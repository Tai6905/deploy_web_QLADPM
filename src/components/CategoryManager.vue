<script setup lang="ts">
import { reactive, ref, watch } from "vue";
import { Pencil, Plus, Trash2, X } from "lucide-vue-next";
import type { Category, CategoryInput } from "../api/shopflowApi";

const props = defineProps<{
  categories: Category[];
  busy: boolean;
  error: string;
}>();

const emit = defineEmits<{
  close: [];
  save: [payload: { id?: number; input: CategoryInput }];
  remove: [id: number];
}>();

const editingId = ref<number | null>(null);
const form = reactive({ name: "", slug: "", description: "" });

watch(
  () => props.categories,
  (categories) => {
    if (editingId.value !== null && !categories.some((item) => item.id === editingId.value)) {
      resetForm();
    }
  },
);
watch(
  () => props.busy,
  (busy, wasBusy) => {
    if (wasBusy && !busy && !props.error) resetForm();
  },
);

function resetForm() {
  editingId.value = null;
  Object.assign(form, { name: "", slug: "", description: "" });
}

function editCategory(category: Category) {
  editingId.value = category.id;
  Object.assign(form, {
    name: category.name,
    slug: category.slug,
    description: category.description ?? "",
  });
}

function save() {
  emit("save", {
    id: editingId.value ?? undefined,
    input: {
      name: form.name.trim(),
      slug: form.slug.trim(),
      description: form.description.trim() || null,
    },
  });
}
</script>

<template>
  <div class="modal-backdrop category-manager-backdrop" @click.self="emit('close')">
    <section class="category-manager" role="dialog" aria-modal="true" aria-labelledby="category-manager-title">
      <header class="category-manager-heading">
        <div>
          <span class="modal-kicker">CATALOG · BACKEND</span>
          <h2 id="category-manager-title">Quản lý danh mục</h2>
          <p>Thay đổi sẽ được lưu trực tiếp vào cơ sở dữ liệu.</p>
        </div>
        <button class="icon-button" aria-label="Đóng" @click="emit('close')"><X :size="18" /></button>
      </header>

      <div class="category-manager-grid">
        <div class="category-list">
          <div class="category-list-heading"><strong>Danh mục hiện có</strong><span>{{ categories.length }}</span></div>
          <div v-if="!categories.length" class="empty-state">Chưa có danh mục nào.</div>
          <article v-for="category in categories" :key="category.id" class="category-row">
            <div class="category-row-copy"><strong>{{ category.name }}</strong><small>{{ category.slug }}</small></div>
            <button class="icon-button" :aria-label="`Sửa ${category.name}`" @click="editCategory(category)"><Pencil :size="15" /></button>
            <button class="icon-button category-delete" :aria-label="`Xóa ${category.name}`" :disabled="busy" @click="emit('remove', category.id)"><Trash2 :size="15" /></button>
          </article>
        </div>

        <form class="category-form" @submit.prevent="save">
          <h3>{{ editingId === null ? "Thêm danh mục" : "Sửa danh mục" }}</h3>
          <label class="form-label">Tên danh mục<input v-model="form.name" required maxlength="255" placeholder="Ví dụ: Guitar acoustic" /></label>
          <label class="form-label">Slug<input v-model="form.slug" required maxlength="255" placeholder="do-dung" /></label>
          <label class="form-label">Mô tả<textarea v-model="form.description" maxlength="5000" rows="3" placeholder="Mô tả ngắn (tùy chọn)" /></label>
          <p v-if="error" class="form-error" role="alert">{{ error }}</p>
          <div class="category-form-actions">
            <button v-if="editingId !== null" type="button" class="button button-outline" @click="resetForm">Hủy sửa</button>
            <button type="submit" class="button button-primary" :disabled="busy"><Plus v-if="editingId === null" :size="15" />{{ busy ? "Đang lưu…" : editingId === null ? "Thêm danh mục" : "Lưu thay đổi" }}</button>
          </div>
        </form>
      </div>
    </section>
  </div>
</template>