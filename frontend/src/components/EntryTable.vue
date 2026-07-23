<script lang="ts" setup>
import { onMounted, ref, watch } from 'vue'
import { createEntry, deleteEntry, listEntries, updateEntry } from '@/api'
import type { components } from '@/schema'
import ButtonLink from './ButtonLink.vue'
import EntryForm from './EntryForm.vue'
import ModalDialog from './ModalDialog.vue'
import { useConfirmDialog } from '@/composables/confirm-dialog'
import useToast from '@/composables/useToast'

const { id, version, editable } = defineProps<{
  id: components['parameters']['ReferenceDataObjectId']
  version: components['schemas']['ReferenceDataObjectVersionDetail']
  editable: boolean
}>()

const { confirm } = useConfirmDialog()
const { danger, success } = useToast()

const entries = ref<components['schemas']['EntryDto'][]>([])
const dialog = ref<InstanceType<typeof ModalDialog>>()
const editing = ref<components['schemas']['EntryDto']>()
const formKey = ref(0)

const load = async () => {
  const { data, error } = await listEntries(id, version.id)
  if (!data) {
    danger(error?.message ?? 'Failed to load entries')
    return
  }
  entries.value = data
}

onMounted(load)
watch(() => version.id, load)

const openCreate = () => {
  editing.value = undefined
  formKey.value++
  dialog.value?.showModal()
}

const openEdit = (entry: components['schemas']['EntryDto']) => {
  editing.value = entry
  formKey.value++
  dialog.value?.showModal()
}

const save = async (payload: {
  nation: components['schemas']['Nation']
  values: components['schemas']['EntryValueDto'][]
}) => {
  const entry = editing.value
  const { error } = entry
    ? await updateEntry(id, version.id, entry.id, payload)
    : await createEntry(id, version.id, payload)
  if (error) {
    danger(error.message ?? 'Failed to save entry')
    return
  }
  dialog.value?.close()
  success(entry ? 'Entry updated' : 'Entry created')
  await load()
}

const remove = async (entry: components['schemas']['EntryDto']) => {
  if (!(await confirm('Delete entry', 'Delete this entry? This cannot be undone.'))) return
  const { error } = await deleteEntry(id, entry.id)
  if (error) {
    danger(error.message ?? 'Failed to delete entry')
    return
  }
  success('Entry deleted')
  await load()
}

const display = (
  entry: components['schemas']['EntryDto'],
  field: components['schemas']['FieldDto'],
) => {
  const value = entry.values.find((candidate) => candidate.fieldId === field.id)
  if (!value) return '—'
  switch (field.dataType) {
    case 'NUMBER':
      return value.numberValue?.toString() ?? '—'
    case 'DATE':
      return value.dateValue ?? '—'
    case 'ENUM':
      return field.options.find((option) => option.id === value.enumOptionId)?.name ?? '—'
    default:
      return value.textValue ?? '—'
  }
}
</script>

<template>
  <section class="entries">
    <header class="entries-header">
      <h3>Entries</h3>
      <ButtonLink
        v-if="editable"
        component="button"
        buttonStyle="secondary"
        size="compact"
        :disabled="!version.fields.length"
        :title="!version.fields.length ? 'Add fields to this version first' : undefined"
        @click="openCreate"
      >
        New Entry
      </ButtonLink>
    </header>

    <p v-if="!entries.length" class="empty">No entries yet.</p>
    <div v-else class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>Country</th>
            <th v-for="field in version.fields" :key="field.id">{{ field.name }}</th>
            <th></th>
            <th v-if="editable"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="entry in entries" :key="entry.id">
            <td>{{ entry.nation ?? '—' }}</td>
            <td v-for="field in version.fields" :key="field.id">{{ display(entry, field) }}</td>
            <td>
              <span
                v-if="!entry.complete"
                class="chip chip-incomplete"
                title="A mandatory field of this version has no value"
              >
                Incomplete
              </span>
            </td>
            <td v-if="editable" class="row-actions">
              <ButtonLink
                component="button"
                buttonStyle="tertiary"
                size="compact"
                @click="openEdit(entry)"
              >
                Edit
              </ButtonLink>
              <ButtonLink
                component="button"
                buttonStyle="error-secondary"
                size="compact"
                @click="remove(entry)"
              >
                Delete
              </ButtonLink>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <ModalDialog ref="dialog" :title="editing ? 'Edit entry' : 'New entry'">
      <EntryForm
        :key="formKey"
        :fields="version.fields"
        :entry="editing"
        @submit="save"
        @cancel="dialog?.close()"
      />
    </ModalDialog>
  </section>
</template>

<style scoped>
.entries {
  margin-block: var(--spacing-lg);
}

.entries-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
}

.entries-header h3 {
  margin: 0;
}

.empty {
  opacity: 0.7;
}

.table-scroll {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}

th,
td {
  text-align: left;
  padding: var(--spacing-sm) var(--spacing-md);
  border-bottom: 1px solid #e4e4e4;
  white-space: nowrap;
}

.row-actions {
  display: flex;
  gap: var(--spacing-sm);
}

.chip-incomplete {
  background-color: var(--error);
  color: var(--light);
}
</style>
