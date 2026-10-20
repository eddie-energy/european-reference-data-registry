<script setup lang="ts" generic="T extends string">
import { computed } from 'vue'

const props = defineProps<{
  modelValue?: T | null
  options: Record<T, string>
  label: string
  disabled?: boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [value: T | undefined] }>()

const selection = computed({
  get: () => props.modelValue ?? undefined,
  set: (value) => emit('update:modelValue', value),
})
</script>

<template>
  <select v-model="selection" :aria-label="label" :disabled>
    <option :value="undefined">None</option>
    <option v-for="(optionLabel, value) in options" :key="value" :value>
      {{ optionLabel }}
    </option>
  </select>
</template>
