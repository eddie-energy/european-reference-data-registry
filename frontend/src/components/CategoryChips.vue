<script setup lang="ts">
import type { components } from '@/schema'

type Category = components['schemas']['ReferenceDataObjectCategory']

defineProps<{ modelValue?: Category; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: Category | undefined] }>()

const categories: { value: Category; label: string }[] = [
  { value: 'ROLE', label: 'Role' },
  { value: 'SERVICE', label: 'Service' },
]
</script>

<template>
  <div class="category-chips" role="group" aria-label="Category">
    <button
      v-for="category in categories"
      :key="category.value"
      type="button"
      class="category-chip"
      :class="{ selected: modelValue === category.value }"
      :aria-pressed="modelValue === category.value"
      :disabled
      @click="emit('update:modelValue', modelValue === category.value ? undefined : category.value)"
    >
      {{ category.label }}
    </button>
  </div>
</template>

<style scoped>
.category-chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-sm);
}

.category-chip {
  padding: var(--spacing-sm) var(--spacing-md);
  border: 1px solid var(--input-border-color);
  border-radius: var(--chip-radius);
  background: var(--light);
  color: var(--dark);
  cursor: pointer;
}

.category-chip.selected {
  border-color: var(--teal);
  background: var(--teal-tint-bg);
  color: var(--teal-tint-text);
}

.category-chip:focus-visible {
  outline: 2px solid var(--teal);
  outline-offset: 2px;
}

.category-chip:disabled {
  opacity: 0.6;
  cursor: default;
}
</style>
