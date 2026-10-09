<script setup lang="ts">
const { tabs } = defineProps<{ tabs: { id: string; label: string }[] }>()
const active = defineModel<string>({ required: true })

const move = (offset: number, event: KeyboardEvent) => {
  const index = tabs.findIndex((tab) => tab.id === active.value)
  const next = tabs[(index + offset + tabs.length) % tabs.length]
  if (!next) return
  active.value = next.id
  const bar = (event.currentTarget as HTMLElement).parentElement
  bar?.querySelector<HTMLElement>(`#tab-${next.id}`)?.focus()
}
</script>

<template>
  <div class="tab-bar" role="tablist">
    <button
      v-for="tab in tabs"
      :id="`tab-${tab.id}`"
      :key="tab.id"
      type="button"
      role="tab"
      :aria-selected="active === tab.id"
      :aria-controls="`panel-${tab.id}`"
      :tabindex="active === tab.id ? 0 : -1"
      :class="{ active: active === tab.id }"
      @click="active = tab.id"
      @keydown.right.prevent="move(1, $event)"
      @keydown.left.prevent="move(-1, $event)"
    >
      {{ tab.label }}
    </button>
  </div>
</template>

<style scoped>
.tab-bar {
  display: flex;
  gap: var(--spacing-xs);
  border-bottom: 1px solid var(--border-color);
}

button {
  padding: var(--spacing-sm) var(--spacing-lg);
  border: none;
  border-bottom: 3px solid transparent;
  background: none;
  color: inherit;
  font: inherit;
  cursor: pointer;
}

button.active {
  border-bottom-color: var(--teal);
  color: var(--teal-tint-text);
  font-weight: 600;
}
</style>
