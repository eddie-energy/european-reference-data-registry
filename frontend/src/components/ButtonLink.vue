<script setup lang="ts">
const {
  buttonStyle,
  disabled = undefined,
  component = 'button',
  size = 'default',
} = defineProps<{
  buttonStyle?: 'primary' | 'secondary' | 'tertiary' | 'error' | 'error-secondary'
  disabled?: boolean
  component?: 'button' | 'a' | 'RouterLink'
  size?: 'default' | 'thicker' | 'wide' | 'compact'
}>()
</script>

<template>
  <component
    :is="component"
    class="button"
    :class="[buttonStyle, { 'is-disabled': disabled }, size]"
    :disabled
  >
    <slot />
  </component>
</template>

<style scoped>
.button {
  --button-block-padding: var(--spacing);
  --button-color: var(--light);
  --button-bg-color: var(--teal);
  display: flex;
  align-items: center;
  gap: var(--spacing);
  padding: var(--button-block-padding) var(--spacing-xlg);
  transition:
    background-color 0.3s ease-in-out,
    color 0.3s ease-in-out,
    border-color 0.3s ease-in-out,
    opacity 0.2s ease-in-out;
  cursor: pointer;
  color: var(--button-color);
  background-color: var(--button-bg-color);
  border: 1px solid var(--button-bg-color);
  border-radius: 1.5rem;
  font-size: 1rem;
  line-height: 1.25rem;
  width: fit-content;
  height: fit-content;
  text-decoration: none;
  justify-content: center;
}

.wide {
  min-width: 250px;
  margin: auto;
}

.compact {
  padding-inline: var(--spacing-md);
}

.button:hover {
  color: var(--button-bg-color);
  background-color: var(--button-color);
}

.button.is-disabled,
.button[disabled],
.button[aria-disabled='true'] {
  opacity: 40%;
  cursor: not-allowed;
  pointer-events: none;
}

.secondary {
  --button-bg-color: var(--light);
  --button-color: var(--primary);
  border-color: var(--button-color);
}

.error {
  --button-bg-color: var(--error);
  --button-color: var(--light);
}

.error-secondary {
  --button-bg-color: var(--light);
  --button-color: var(--error);
  border-color: var(--button-color);
}
</style>
