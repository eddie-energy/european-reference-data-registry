<!--
SPDX-FileCopyrightText: 2025 The EDDIE Developers <eddie.developers@fh-hagenberg.at>
SPDX-License-Identifier: Apache-2.0

SPDX-FileComment: This file was modified to fit the CI and use case of the Marketplace Web app.
SPDX-FileComment: Original source: https://github.com/eddie-energy/eddie/blob/main/aiida/ui/src/components/ConfirmDialog.vue
-->

<script setup lang="ts">
import ModalDialog from './ModalDialog.vue'
import { onMounted, ref } from 'vue'
import ButtonLink from './ButtonLink.vue'
import { useConfirmDialog } from '@/composables/confirm-dialog'

const modal = ref<HTMLDialogElement>()
const {
  titleRef,
  descriptionRef,
  cancelLabelRef,
  confirmLabelRef,
  confirmModalRef,
  onConfirm,
  onCancel,
} = useConfirmDialog()

onMounted(() => {
  confirmModalRef.value = modal.value
})
</script>

<template>
  <ModalDialog :title="titleRef" ref="modal" @close="onCancel">
    <p class="description text-normal">
      {{ descriptionRef }}
    </p>
    <div class="button-pair">
      <ButtonLink button-style="secondary" @click="modal?.close()">
        {{ cancelLabelRef }}
      </ButtonLink>
      <ButtonLink button-style="error" @click="onConfirm">
        {{ confirmLabelRef }}
      </ButtonLink>
    </div>
  </ModalDialog>
</template>

<style scoped>
.description {
  margin-bottom: var(--spacing-xxl);
  max-width: 80%;
}
.button-pair {
  display: flex;
  justify-content: space-between;
}
</style>
