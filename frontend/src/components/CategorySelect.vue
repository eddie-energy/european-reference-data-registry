<script setup lang="ts">
import { computed } from 'vue'
import type { components } from '@/schema'

type Category = components['schemas']['ReferenceDataObjectCategory']

const props = defineProps<{ modelValue?: Category; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: Category | undefined] }>()

const categories: { value: Category; label: string }[] = [
  { value: 'ROLE', label: 'Role' },
  { value: 'SERVICE', label: 'Service' },
]

const selection = computed({
  get: () => props.modelValue ?? undefined,
  set: (value) => emit('update:modelValue', value),
})
</script>

<template>
  <select v-model="selection" aria-label="Category" :disabled>
    <option :value="undefined">None</option>
    <option v-for="category in categories" :key="category.value" :value="category.value">
      {{ category.label }}
    </option>
  </select>
</template>
