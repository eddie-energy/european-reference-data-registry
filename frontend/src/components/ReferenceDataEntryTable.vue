<script lang="ts" setup>
import { computed, h, onMounted, ref, watch } from 'vue'
import {
  createColumnHelper,
  FlexRender,
  getCoreRowModel,
  getSortedRowModel,
  useVueTable,
} from '@tanstack/vue-table'
import type { ColumnDef, SortingState } from '@tanstack/vue-table'
import {
  createReferenceDataEntry,
  deleteReferenceDataEntry,
  listReferenceDataEntries,
  updateReferenceDataEntry,
} from '@/api'
import type { components } from '@/schema'
import ButtonLink from './ButtonLink.vue'
import ReferenceDataEntryForm from './ReferenceDataEntryForm.vue'
import ModalDialog from './ModalDialog.vue'
import VersionTable from './VersionTable.vue'
import { useConfirmDialog } from '@/composables/confirm-dialog'
import useToast from '@/composables/useToast'
import { referenceDataObject } from '@/stores/referenceDataObject'
import { maintainableNations, userRole } from '@/stores/userInfo'
import { nationFlag, nationName } from '@/constants/nations'

type ReferenceDataEntryDto = components['schemas']['ReferenceDataEntryDto']
type FieldDto = components['schemas']['FieldDto']

const { id, version, editable } = defineProps<{
  id: components['parameters']['ReferenceDataObjectId']
  version: components['schemas']['ReferenceDataObjectVersionDetail']
  editable: boolean
}>()

const { confirm } = useConfirmDialog()
const { danger, success } = useToast()

const referenceDataEntries = ref<ReferenceDataEntryDto[]>([])
const dialog = ref<InstanceType<typeof ModalDialog>>()
const editing = ref<ReferenceDataEntryDto>()
const formKey = ref(0)
const submitting = ref(false)
const loading = ref(true)

const load = async () => {
  loading.value = true
  const { data, error } = await listReferenceDataEntries(id, version.id)
  if (!data) {
    danger(error?.message ?? 'Failed to load reference data entries')
    loading.value = false
    return
  }
  referenceDataEntries.value = data
  loading.value = false
}

onMounted(load)
watch(() => version.id, load)

const openCreate = () => {
  editing.value = undefined
  formKey.value++
  dialog.value?.showModal()
}

const openEdit = (referenceDataEntry: ReferenceDataEntryDto) => {
  editing.value = referenceDataEntry
  formKey.value++
  dialog.value?.showModal()
}

const save = async (payload: {
  nation?: components['schemas']['Nation']
  values: components['schemas']['ReferenceDataEntryValueDto'][]
}) => {
  const referenceDataEntry = editing.value
  submitting.value = true
  try {
    const { error } = referenceDataEntry
      ? await updateReferenceDataEntry(id, version.id, referenceDataEntry.id, payload)
      : await createReferenceDataEntry(id, version.id, payload)
    if (error) {
      danger(error.message ?? 'Failed to save reference data entry')
      return
    }
    dialog.value?.close()
    success(referenceDataEntry ? 'Reference data entry updated' : 'Reference data entry created')
    await load()
  } finally {
    submitting.value = false
  }
}

const remove = async (referenceDataEntry: ReferenceDataEntryDto) => {
  if (
    !(await confirm(
      'Delete Reference Data Entry',
      'Delete this reference data entry? This cannot be undone.',
    ))
  )
    return
  const { error } = await deleteReferenceDataEntry(id, referenceDataEntry.id)
  if (error) {
    danger(error.message ?? 'Failed to delete reference data entry')
    return
  }
  success('Reference data entry deleted')
  await load()
}

const rawValue = (
  referenceDataEntry: ReferenceDataEntryDto,
  field: FieldDto,
): string | number | undefined => {
  const value = referenceDataEntry.values.find((candidate) => candidate.fieldId === field.id)
  if (!value) return undefined
  switch (field.dataType) {
    case 'NUMBER':
      return value.numberValue
    case 'DATE':
      return value.dateValue
    case 'ENUM':
      return field.options.find((option) => option.id === value.enumOptionId)?.name
    default:
      return value.textValue
  }
}

const display = (referenceDataEntry: ReferenceDataEntryDto, field: FieldDto) =>
  rawValue(referenceDataEntry, field)?.toString() ?? '—'

const isPublishedVersionCode = (versionCode: number) =>
  referenceDataObject.value?.versions.some(
    (v) => v.versionCode === versionCode && v.publishState === 'PUBLISHED',
  ) ?? false

const columnHelper = createColumnHelper<ReferenceDataEntryDto>()

const columns = computed<ColumnDef<ReferenceDataEntryDto, any>[]>(() => [
  columnHelper.accessor((referenceDataEntry) => referenceDataEntry.nation, {
    id: 'nation',
    header: 'Country',
    cell: (ctx) => {
      const nation = ctx.row.original.nation
      return nation ? `${nationFlag(nation)} ${nation}` : '—'
    },
  }),
  ...version.fields.map((field) =>
    columnHelper.accessor((referenceDataEntry) => rawValue(referenceDataEntry, field), {
      id: field.id,
      header: field.name,
      cell: (ctx) => display(ctx.row.original, field),
    }),
  ),
  columnHelper.display({
    id: 'incomplete',
    header: 'Latest compatible version',
    enableSorting: false,
    cell: (ctx) => {
      if (ctx.row.original.complete) return null
      const lastComplete = ctx.row.original.lastCompleteVersionCode
      const showVersionCode =
        lastComplete != null &&
        (userRole.value === 'operationalEntity' || isPublishedVersionCode(lastComplete))
      return h(
        'span',
        {
          class: 'chip chip-incomplete',
          title: showVersionCode
            ? `This reference data entry was last complete as of version ${lastComplete}; a mandatory field added since then has no value.`
            : 'This reference data entry has never had values for all of its mandatory fields.',
        },
        showVersionCode ? `v${lastComplete}` : 'Incomplete',
      )
    },
  }),
  ...(editable
    ? [
        columnHelper.display({
          id: 'actions',
          header: '',
          enableSorting: false,
          cell: (ctx) => {
            const referenceDataEntry = ctx.row.original
            if (
              userRole.value !== 'operationalEntity' &&
              (!referenceDataEntry.nation ||
                !maintainableNations(id).includes(referenceDataEntry.nation))
            ) {
              return null
            }
            return [
              h(
                ButtonLink,
                {
                  component: 'button',
                  buttonStyle: 'tertiary',
                  size: 'compact',
                  onClick: () => openEdit(referenceDataEntry),
                },
                () => 'Edit',
              ),
              h(
                ButtonLink,
                {
                  component: 'button',
                  buttonStyle: 'error-secondary',
                  size: 'compact',
                  onClick: () => remove(referenceDataEntry),
                },
                () => 'Delete',
              ),
            ]
          },
        }),
      ]
    : []),
])

const searchQuery = ref('')

const matchesSearch = (referenceDataEntry: ReferenceDataEntryDto, query: string) =>
  (referenceDataEntry.nation &&
    (referenceDataEntry.nation.toLowerCase().includes(query) ||
      nationName(referenceDataEntry.nation).toLowerCase().includes(query))) ||
  version.fields.some(
    (field) =>
      (!field.nation || field.nation === referenceDataEntry.nation) &&
      display(referenceDataEntry, field).toLowerCase().includes(query),
  )

const filteredReferenceDataEntries = computed(() => {
  const query = searchQuery.value.trim().toLowerCase()
  return query
    ? referenceDataEntries.value.filter((entry) => matchesSearch(entry, query))
    : referenceDataEntries.value
})

const sorting = ref<SortingState>([])

const table = useVueTable({
  data: filteredReferenceDataEntries,
  get columns() {
    return columns.value
  },
  getRowId: (row) => row.id,
  getCoreRowModel: getCoreRowModel(),
  getSortedRowModel: getSortedRowModel(),
  state: {
    get sorting() {
      return sorting.value
    },
  },
  onSortingChange: (updater) => {
    sorting.value = typeof updater === 'function' ? updater(sorting.value) : updater
  },
})
</script>

<template>
  <section class="reference-data-entries">
    <header class="reference-data-entries-header">
      <h3>Reference Data Entries</h3>
      <ButtonLink
        v-if="editable"
        component="button"
        buttonStyle="secondary"
        size="compact"
        :disabled="!version.fields.length"
        :title="!version.fields.length ? 'Add fields to this version first' : undefined"
        @click="openCreate"
      >
        New Reference Data Entry
      </ButtonLink>
    </header>

    <input
      v-if="referenceDataEntries.length"
      v-model="searchQuery"
      type="search"
      class="search-input"
      placeholder="Search by country or content"
      aria-label="Search reference data entries by country or content"
    />

    <p v-if="loading" class="empty">Loading…</p>
    <p v-else-if="!referenceDataEntries.length" class="empty">No reference data entries yet.</p>
    <p v-else-if="!filteredReferenceDataEntries.length" class="empty">
      No reference data entries match your search.
    </p>
    <VersionTable
      v-else
      animate-rows
      :version-code="version.versionCode"
      :publish-state="version.publishState"
      :colspan="table.getHeaderGroups()[0]?.headers.length ?? 1"
    >
      <template #header>
        <tr v-for="headerGroup in table.getHeaderGroups()" :key="headerGroup.id">
          <th
            v-for="header in headerGroup.headers"
            :key="header.id"
            scope="col"
            :class="{ sortable: header.column.getCanSort() }"
            @click="header.column.getToggleSortingHandler()?.($event)"
          >
            <FlexRender
              v-if="!header.isPlaceholder"
              :render="header.column.columnDef.header"
              :props="header.getContext()"
            />
            <span v-if="header.column.getIsSorted() === 'asc'" aria-hidden="true"> ▲</span>
            <span v-else-if="header.column.getIsSorted() === 'desc'" aria-hidden="true"> ▼</span>
            <span v-else-if="header.column.getCanSort()" class="sort-hint" aria-hidden="true">
              ⇅</span
            >
          </th>
        </tr>
      </template>
      <tr v-for="row in table.getRowModel().rows" :key="row.id">
        <td
          v-for="cell in row.getVisibleCells()"
          :key="cell.id"
          :class="{ 'row-actions': cell.column.id === 'actions' }"
        >
          <FlexRender :render="cell.column.columnDef.cell" :props="cell.getContext()" />
        </td>
      </tr>
    </VersionTable>

    <ModalDialog
      ref="dialog"
      size="tall"
      :title="editing ? 'Edit Reference Data Entry' : 'New Reference Data Entry'"
    >
      <ReferenceDataEntryForm
        :key="formKey"
        :object-id="id"
        :fields="version.fields"
        :reference-data-entry="editing"
        :submitting
        @submit="save"
        @cancel="dialog?.close()"
      />
    </ModalDialog>
  </section>
</template>

<style scoped>
.reference-data-entries {
  margin-block: var(--spacing-lg);
}

.reference-data-entries-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
}

.reference-data-entries-header h3 {
  margin: 0;
}

.empty {
  opacity: 0.7;
}

.search-input {
  width: 100%;
  max-width: 28rem;
  margin-block: var(--spacing-md);
  padding: var(--spacing-sm) var(--spacing-md);
  border: 1px solid var(--input-border-color);
  border-radius: var(--default-border-radius);
  box-sizing: border-box;
}

th.sortable {
  cursor: pointer;
  user-select: none;
}

th.sortable:hover {
  color: var(--teal);
}

.sort-hint {
  opacity: 0.4;
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
