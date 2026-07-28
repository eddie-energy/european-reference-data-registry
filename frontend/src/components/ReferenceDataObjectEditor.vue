<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  referenceDataObject,
  updateReferenceDataObject,
  updateReferenceDataObjects,
} from '@/stores/referenceDataObject'
import {
  createVersion,
  publishVersion,
  deleteReferenceDataObject,
  deleteVersion,
  unlinkField,
  reorderFields,
} from '@/api'
import FieldForm from '@/components/FieldForm.vue'
import ButtonLink from '@/components/ButtonLink.vue'
import VersionTable from '@/components/VersionTable.vue'
import { useConfirmDialog } from '@/composables/confirm-dialog'
import useToast from '@/composables/useToast'
import type { components } from '@/schema'

const { id } = defineProps<{ id: components['parameters']['ReferenceDataObjectId'] }>()

const router = useRouter()
const { confirm } = useConfirmDialog()
const { danger, success } = useToast()

const load = () => updateReferenceDataObject(id)

const draftVersion = computed(() => {
  const versions = referenceDataObject.value?.versions
  const lastVersion = versions?.[versions.length - 1]
  return lastVersion?.publishState === 'DRAFT' ? lastVersion : undefined
})

const hasFields = computed(() =>
  (referenceDataObject.value?.versions ?? []).some((version) => version.fields.length > 0),
)

const isDraftMode = computed(() =>
  (referenceDataObject.value?.versions ?? []).every((version) => version.publishState === 'DRAFT'),
)

const canDeleteObject = computed(() => !hasFields.value || isDraftMode.value)

const canDeleteDraftVersion = computed(
  () => !!draftVersion.value && (referenceDataObject.value?.versions.length ?? 0) > 1,
)

const submitting = ref(false)

const startNewVersion = async () => {
  submitting.value = true
  try {
    const { error } = await createVersion(id)
    if (error) {
      danger(error.message ?? 'Failed to start new version')
      return
    }
    await load()
  } finally {
    submitting.value = false
  }
}

const publish = async () => {
  if (!draftVersion.value) return
  if (
    !(await confirm(
      'Publish version',
      `Publish version ${draftVersion.value.versionCode}? It will become visible to all users and can't be unpublished.`,
    ))
  )
    return
  submitting.value = true
  try {
    const { error } = await publishVersion(id, draftVersion.value.id)
    if (error) {
      danger(error.message ?? 'Failed to publish version')
      return
    }
    success(`Version ${draftVersion.value.versionCode} published`)
    await load()
  } finally {
    submitting.value = false
  }
}

const deleteDraftVersion = async () => {
  if (!draftVersion.value) return
  if (
    !(await confirm(
      'Delete draft version',
      `Delete draft version ${draftVersion.value.versionCode}? Fields only used in this draft will be removed. This cannot be undone.`,
    ))
  )
    return
  submitting.value = true
  try {
    const { error } = await deleteVersion(id, draftVersion.value.id)
    if (error) {
      danger(error.message ?? 'Failed to delete draft version')
      return
    }
    success(`Draft version ${draftVersion.value.versionCode} deleted`)
    await load()
  } finally {
    submitting.value = false
  }
}

const deleteField = async (fieldId: string, fieldName: string) => {
  if (!draftVersion.value) return
  if (!(await confirm('Delete field', `Delete "${fieldName}"? This cannot be undone.`))) return
  submitting.value = true
  try {
    const { error } = await unlinkField(id, draftVersion.value.id, fieldId)
    if (error) {
      danger(error.message ?? 'Failed to delete field')
      return
    }
    success(`Field "${fieldName}" deleted`)
    await load()
  } finally {
    submitting.value = false
  }
}

const moveField = async (fieldId: string, direction: 'left' | 'right') => {
  if (!draftVersion.value) return
  const fields = draftVersion.value.fields
  const index = fields.findIndex((field) => field.id === fieldId)
  const swapWith = direction === 'left' ? index - 1 : index + 1
  if (index === -1 || swapWith < 0 || swapWith >= fields.length) return

  const current = fields[index]
  const neighbor = fields[swapWith]
  if (!current || !neighbor) return

  const reordered = fields.map((field, position) => {
    if (position === index) return neighbor
    if (position === swapWith) return current
    return field
  })
  const fieldIds = reordered.map((field) => field.id)

  submitting.value = true
  try {
    const { error } = await reorderFields(id, draftVersion.value.id, fieldIds)
    if (error) {
      danger(error.message ?? 'Failed to reorder fields')
      return
    }
    await load()
  } finally {
    submitting.value = false
  }
}

const deleteObject = async () => {
  if (!canDeleteObject.value) return
  const objectName = referenceDataObject.value?.name
  const message = hasFields.value
    ? 'Delete this reference data object? All of its fields will also be deleted. This cannot be undone.'
    : 'Delete this reference data object? This cannot be undone.'
  if (!(await confirm('Delete reference data object', message))) return

  for (const version of referenceDataObject.value?.versions ?? []) {
    for (const field of version.fields) {
      const { error } = await unlinkField(id, version.id, field.id)
      if (error) {
        danger(error.message ?? 'Failed to delete field')
        return
      }
    }
  }

  const { error } = await deleteReferenceDataObject(id)
  if (error) {
    danger(error.message ?? 'Failed to delete — remove all fields first')
    return
  }
  success(`"${objectName}" deleted`)
  await updateReferenceDataObjects()
  router.push({ name: 'dashboard' })
}

const optionNames = (field: components['schemas']['FieldDto']) =>
  field.options.map((option) => option.name).join(', ')

const sampleValue = (field: components['schemas']['FieldDto']): string => {
  switch (field.dataType) {
    case 'NUMBER':
      return '123'
    case 'DATE':
      return new Date().toISOString().slice(0, 10)
    case 'ENUM':
      return field.options[0]?.name ?? '—'
    default:
      return 'Sample text'
  }
}
</script>

<template>
  <section class="editor">
    <h2>Edit</h2>
    <ButtonLink
      v-if="canDeleteObject"
      component="button"
      buttonStyle="error-secondary"
      size="compact"
      @click="deleteObject"
    >
      Delete reference data object
    </ButtonLink>

    <template v-if="draftVersion">
      <p v-if="!draftVersion.fields.length" class="empty">No fields yet — add one below.</p>
      <VersionTable
        v-else
        :version-code="draftVersion.versionCode"
        publish-state="DRAFT"
        :colspan="draftVersion.fields.length + 1"
      >
        <template #header>
          <tr>
            <th>Country</th>
            <th v-for="(field, index) in draftVersion.fields" :key="field.id">
              <div class="field-header">
                <ButtonLink
                  component="button"
                  buttonStyle="tertiary"
                  size="compact"
                  :disabled="index === 0 || submitting"
                  title="Move left"
                  @click="moveField(field.id, 'left')"
                >
                  ◀
                </ButtonLink>
                <span class="field-name">
                  {{ field.name }}
                  <span v-if="field.mandatory" class="mandatory" title="Mandatory field">*</span>
                </span>
                <span
                  class="field-type"
                  :title="field.dataType === 'ENUM' ? optionNames(field) : undefined"
                >
                  {{ field.dataType }}
                </span>
                <ButtonLink
                  component="button"
                  buttonStyle="tertiary"
                  size="compact"
                  :disabled="index === draftVersion.fields.length - 1 || submitting"
                  title="Move right"
                  @click="moveField(field.id, 'right')"
                >
                  ▶
                </ButtonLink>
                <ButtonLink
                  component="button"
                  buttonStyle="error-secondary"
                  size="compact"
                  :disabled="submitting"
                  @click="deleteField(field.id, field.name)"
                >
                  Delete
                </ButtonLink>
              </div>
            </th>
          </tr>
        </template>
        <tr class="preview-row" title="Preview only — not saved data">
          <td>AUT <span class="preview-label">(preview)</span></td>
          <td v-for="field in draftVersion.fields" :key="field.id">
            {{ sampleValue(field) }}
          </td>
        </tr>
      </VersionTable>

      <FieldForm :id :version-id="draftVersion.id" @created="load" />

      <div class="draft-actions">
        <ButtonLink
          component="button"
          buttonStyle="secondary"
          size="compact"
          :disabled="submitting"
          @click="publish"
        >
          Publish version {{ draftVersion.versionCode }}
        </ButtonLink>
        <ButtonLink
          v-if="canDeleteDraftVersion"
          component="button"
          buttonStyle="error-secondary"
          size="compact"
          :disabled="submitting"
          @click="deleteDraftVersion"
        >
          Delete draft version {{ draftVersion.versionCode }}
        </ButtonLink>
      </div>
    </template>

    <template v-else>
      <ButtonLink
        component="button"
        buttonStyle="secondary"
        size="compact"
        :disabled="submitting"
        @click="startNewVersion"
      >
        Start new version to add fields
      </ButtonLink>
    </template>
  </section>
</template>

<style scoped>
.editor {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.empty {
  opacity: 0.7;
}

.preview-row {
  font-style: italic;
  color: var(--dark);
  opacity: 0.6;
}

.preview-label {
  font-style: normal;
  font-size: 0.75rem;
}

.draft-actions {
  display: flex;
  gap: var(--spacing-md);
}

.field-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-weight: 600;
}

.field-name {
  display: flex;
  align-items: center;
  gap: 2px;
}

.mandatory {
  color: var(--error);
}

.field-type {
  font-weight: 400;
  opacity: 0.7;
  text-transform: lowercase;
}
</style>
