<script setup lang="ts">
import { computed } from 'vue';
import { instrumentTypes, instrumentDefinition, setSpecification, type Specifications } from '../catalog/instruments';
const props = withDefaults(defineProps<{ modelValue: Specifications; filters?: boolean; disabled?: boolean }>(), { filters: false, disabled: false });
const emit = defineEmits<{ 'update:modelValue': [value: Specifications] }>();
const definition = computed(() => instrumentDefinition(props.modelValue.instrumentType));
function change(key: string, event: Event) {
  emit('update:modelValue', setSpecification(props.modelValue, key, (event.target as HTMLSelectElement).value));
}
</script>
<template>
  <fieldset class="instrument-fields" :disabled="disabled">
    <legend>{{ filters ? 'Lọc theo thông số nhạc cụ' : 'Thông số nhạc cụ' }}</legend>
    <div class="instrument-fields-grid">
      <label>Nhóm nhạc cụ<select :value="modelValue.instrumentType || ''" @change="change('instrumentType', $event)"><option value="">{{ filters ? 'Tất cả nhạc cụ' : 'Chưa phân loại' }}</option><option v-for="type in instrumentTypes" :key="type.value" :value="type.value">{{ type.label }}</option></select></label>
      <label v-for="field in definition?.fields || []" :key="field.key">{{ field.label }}<select :value="modelValue[field.key] || ''" @change="change(field.key, $event)"><option value="">{{ filters ? 'Tất cả' : 'Chưa cập nhật' }}</option><option v-for="option in field.options" :key="option.value" :value="option.value">{{ option.label }}</option></select></label>
    </div>
    <p v-if="!definition" class="instrument-hint">Chọn nhóm để xem các thông số riêng của guitar, piano, trống, violin hoặc kèn.</p>
  </fieldset>
</template>
