<script setup lang="ts">
import { ref } from 'vue'
import CrossIcon from '@/assets/icons/CrossIcon.svg'

const { title, size } = defineProps<{
  title: string
  size?: 'default' | 'large'
}>()

const modal = ref<HTMLDialogElement>()

const showModal = () => {
  modal.value?.showModal()
}
const close = () => {
  modal.value?.close()
}

defineExpose({ showModal, close })
</script>

<template>
  <dialog class="dialog" :class="{ large: size === 'large' }" ref="modal" closedby="any">
    <header class="dialog-header">
      <p class="title-domain">{{ title }}</p>
      <button type="button" @click="close" aria-label="Close Modal Window" class="close-button">
        <CrossIcon />
      </button>
    </header>
    <div class="-body">
      <slot />
    </div>
  </dialog>
</template>

<style scoped>
.dialog {
  margin: auto;
  width: 33vw;
  max-width: 1620px;
  padding: var(--spacing-xxl);
  border-radius: var(--spacing-xxl);
  border: none;
  color: var(--dark);
  outline: none;
  background-color: var(--light);
  &::backdrop {
    background-color: rgba(0, 0, 0, 0.3);
  }

  &.large {
    width: 80vw;
  }
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-xlg);
}

.close-button {
  display: flex;
  cursor: pointer;

  svg {
    color: var(--dark);
    width: 2rem;
    height: 2rem;
  }
}

@media screen and (min-width: 1024px) {
  .dialog {
    overflow: auto;
  }
}
</style>
