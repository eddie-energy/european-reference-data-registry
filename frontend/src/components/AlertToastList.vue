<!--
SPDX-FileCopyrightText: 2025 The EDDIE Developers <eddie.developers@fh-hagenberg.at>
SPDX-License-Identifier: Apache-2.0
-->

<script setup lang="ts">
import useToast from '@/composables/useToast'
import AlertToast from './AlertToast.vue'

const { toasts } = useToast()
</script>

<template>
  <TransitionGroup class="alert-list" tag="div">
    <AlertToast
      v-for="[id, { severity, message, duration, canClose }] in toasts"
      :key="id"
      :severity
      :message
      :duration
      :canClose
      :id
    />
  </TransitionGroup>
</template>

<style scoped>
.alert-list {
  position: absolute;
  margin-bottom: 3rem;
  padding-inline: var(--spacing-xlg);
  right: 0;
  bottom: 0;
  height: fit-content;
  display: flex;
  gap: var(--spacing-md);
  flex-direction: column;
  justify-content: flex-end;
  align-items: end;
}

.v-enter-active,
.v-move,
.v-leave-active {
  transition:
    transform 0.5s ease,
    opacity 0.5s ease;
}

.v-enter-from,
.v-leave-to {
  opacity: 0;
  transform: translateY(var(--spacing-xxl));
}
</style>
