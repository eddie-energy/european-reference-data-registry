<script setup lang="ts">
import {
  functionalBuildingBlockLabels,
  type FunctionalBuildingBlock,
} from '@/constants/functionalBuildingBlocks'

defineProps<{ modelValue?: FunctionalBuildingBlock | null; disabled?: boolean }>()
const emit = defineEmits<{
  'update:modelValue': [value: FunctionalBuildingBlock | undefined]
}>()
</script>

<template>
  <div class="building-block-chips" role="group" aria-label="Functional building block">
    <button
      v-for="(label, value) in functionalBuildingBlockLabels"
      :key="value"
      type="button"
      class="building-block-chip"
      :class="{ selected: modelValue === value }"
      :aria-pressed="modelValue === value"
      :disabled
      @click="emit('update:modelValue', modelValue === value ? undefined : value)"
    >
      {{ label }}
    </button>
  </div>
</template>

<style scoped>
.building-block-chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-sm);
}

.building-block-chip {
  padding: var(--spacing-sm) var(--spacing-md);
  border: 1px solid var(--input-border-color);
  border-radius: var(--chip-radius);
  background: var(--light);
  color: var(--dark);
  cursor: pointer;
  text-align: start;
}

.building-block-chip.selected {
  border-color: var(--teal);
  background: var(--teal-tint-bg);
  color: var(--teal-tint-text);
}

.building-block-chip:focus-visible {
  outline: 2px solid var(--teal);
  outline-offset: 2px;
}

.building-block-chip:disabled {
  opacity: 0.6;
  cursor: default;
}
</style>
