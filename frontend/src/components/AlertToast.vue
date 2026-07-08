<!--
SPDX-FileCopyrightText: 2025 The EDDIE Developers <eddie.developers@fh-hagenberg.at>
SPDX-License-Identifier: Apache-2.0
-->

<script setup lang="ts">
import type { ToastTypes } from '@/types'
import InfoIcon from '@/assets/icons/InfoIcon.svg'
import SuccessIcon from '@/assets/icons/SuccessIcon.svg'
import CrossIcon from '@/assets/icons/CrossIcon.svg'
import useToast from '@/composables/useToast'

const { remove } = useToast()

const {
  severity = 'info',
  message,
  duration = 5000,
  canClose = false,
  id,
} = defineProps<{
  severity?: ToastTypes
  message: string
  id: number
  duration?: number
  canClose?: boolean
}>()

const toastTypes: {
  [key: string]: {
    title: string
    icon: string | undefined
  }
} = {
  info: {
    title: 'info',
    icon: InfoIcon,
  },
  success: {
    title: 'success',
    icon: SuccessIcon,
  },
  neutral: {
    title: 'neutral',
    icon: undefined,
  },
  danger: {
    title: 'danger',
    icon: InfoIcon,
  },
}

const progressBarDuration = `${duration}ms`
</script>

<template>
  <div class="toast" :class="[severity]" aria-live="polite" role="alert">
    <component :is="toastTypes[severity]?.icon" class="icon" />
    <div>
      <p class="toast-message text-small">{{ message }}</p>
    </div>
    <button
      type="button"
      v-if="canClose"
      @click="remove(id)"
      :aria-label="'closeButton'"
      class="close-button"
    >
      <CrossIcon />
    </button>

    <div class="toast-progress-bar" :class="{ hide: !duration }"></div>
  </div>
</template>

<style scoped>
.toast {
  color: var(--dark);
  --severity-color: var(--toast-info);
  z-index: 10;
  position: relative;
  border-radius: 1rem;
  display: flex;
  gap: var(--spacing-md);
  background-color: rgba(from var(--severity-color) r g b / 0.1);
  width: fit-content;
  padding: var(--spacing);
  box-shadow: 0px 3px 10px 0px #00000040;
  min-width: 15vw;
  opacity: 1;
  overflow: hidden;
  border: 1px solid var(--severity-color);
}

.success {
  --severity-color: var(--toast-succes);
}

.neutral {
  --severity-color: var(--toast-neutral);
}

.danger {
  --severity-color: var(--toast-danger);
}

.icon {
  width: 1rem;
  height: 1rem;
  color: var(--severity-color);
}

.toast-title {
  font-weight: 600;
}

.toast-progress-bar {
  position: absolute;
  bottom: 0;
  left: 0;
  height: 5px;
  background-color: var(--severity-color);
  animation: progress v-bind(progressBarDuration) linear;
}

.hide {
  display: none;
}

.close-button {
  cursor: pointer;
  margin-left: auto;
}

@keyframes progress {
  from {
    width: 100%;
  }
  to {
    width: 0%;
  }
}
</style>
