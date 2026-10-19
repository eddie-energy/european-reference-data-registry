<script setup lang="ts">
import { computed } from 'vue'
import {
  functionalBuildingBlockLabels,
  type FunctionalBuildingBlock,
} from '@/constants/functionalBuildingBlocks'

const props = defineProps<{ modelValue?: FunctionalBuildingBlock | null; disabled?: boolean }>()
const emit = defineEmits<{
  'update:modelValue': [value: FunctionalBuildingBlock | undefined]
}>()

const selection = computed({
  get: () => props.modelValue ?? undefined,
  set: (value) => emit('update:modelValue', value),
})
</script>

<template>
  <select v-model="selection" aria-label="Functional building block" :disabled>
    <option :value="undefined">None</option>
    <option v-for="(label, value) in functionalBuildingBlockLabels" :key="value" :value="value">
      {{ label }}
    </option>
  </select>
</template>
