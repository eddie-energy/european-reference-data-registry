<script setup lang="ts">
import ButtonLink from '@/components/ButtonLink.vue'
import { computed, h, ref } from 'vue'
import {
  createColumnHelper,
  getCoreRowModel,
  getFilteredRowModel,
  getSortedRowModel,
  useVueTable,
} from '@tanstack/vue-table'
import type { ColumnDef, SortingState } from '@tanstack/vue-table'
import type { components } from '@/schema'
import {
  managementOrganizations,
  updateManagementOrganizations,
} from '@/stores/managementOrganizations'
import { updateManagementUsers } from '@/stores/managementUsers'
import { referenceDataObjects } from '@/stores/referenceDataObject'
import ModalDialog from '@/components/ModalDialog.vue'
import OrganizationCreateForm from '@/components/OrganizationCreateForm.vue'
import OrganizationEditForm from '@/components/OrganizationEditForm.vue'
import SortableTable from '@/components/SortableTable.vue'

type ManagedOrganization = components['schemas']['ManagedOrganizationDto']

const errorMessage = ref('')
const search = ref('')
const sorting = ref<SortingState>([{ id: 'name', desc: false }])
const editDialog = ref<InstanceType<typeof ModalDialog>>()
const createDialog = ref<InstanceType<typeof ModalDialog>>()
const editingId = ref('')
const formKey = ref(0)

const editingOrganization = computed(() =>
  managementOrganizations.value.find((organization) => organization.id === editingId.value),
)

const roleLabels: Record<components['schemas']['OrganizationRole'], string> = {
  NDSF: 'NDSF',
  OPERATIONAL_ENTITY: 'Operational Entity',
}

const roleLabel = (role?: components['schemas']['OrganizationRole'] | null) =>
  role ? roleLabels[role] : '—'

const objectName = (id: string) =>
  referenceDataObjects.value?.find((object) => object.id === id)?.name ?? id

const refresh = async () => {
  const [organizations] = await Promise.all([
    updateManagementOrganizations(),
    updateManagementUsers(),
  ])
  errorMessage.value = organizations.error?.message ?? ''
}

const openEdit = (organizationId: string) => {
  editingId.value = organizationId
  formKey.value++
  editDialog.value?.showModal()
}

const openCreate = () => {
  formKey.value++
  createDialog.value?.showModal()
}

const created = async () => {
  createDialog.value?.close()
  await refresh()
}

const chips = (labels: string[]) =>
  labels.length
    ? h(
        'ul',
        { class: 'chips' },
        labels.map((label) => h('li', { key: label }, label)),
      )
    : h('span', { class: 'none' }, '—')

const columnHelper = createColumnHelper<ManagedOrganization>()

const columns: ColumnDef<ManagedOrganization, any>[] = [
  columnHelper.accessor('name', {
    header: 'Organization',
    cell: (ctx) => [
      ctx.getValue(),
      ' ',
      h('span', { class: 'none' }, `(${ctx.row.original.alias})`),
    ],
  }),
  columnHelper.accessor((organization) => roleLabel(organization.role), {
    id: 'role',
    header: 'Role',
  }),
  columnHelper.accessor(
    (organization) =>
      organization.nations.length ? [...organization.nations].sort().join(', ') : undefined,
    {
      id: 'nations',
      header: 'Nations',
      sortUndefined: 'last',
      cell: (ctx) => chips([...ctx.row.original.nations].sort()),
    },
  ),
  columnHelper.accessor((organization) => organization.responsibilities.length, {
    id: 'responsibilities',
    header: 'Responsibilities',
    cell: (ctx) =>
      chips(
        ctx.row.original.responsibilities.map(
          (item) => `${objectName(item.referenceDataObjectId)} · ${item.nation}`,
        ),
      ),
  }),
  columnHelper.display({
    id: 'actions',
    header: 'Edit',
    enableSorting: false,
    cell: (ctx) =>
      ctx.row.original.editable
        ? h(
            ButtonLink,
            {
              component: 'button',
              type: 'button',
              buttonStyle: 'tertiary',
              size: 'compact',
              onClick: () => openEdit(ctx.row.original.id),
            },
            () => 'Edit',
          )
        : h('span', { class: 'none' }, 'Protected'),
  }),
]

const matchesSearch = (organization: ManagedOrganization, query: string) =>
  [
    organization.name,
    organization.alias,
    roleLabel(organization.role),
    ...organization.nations,
  ].some((value) => value.toLowerCase().includes(query))

const table = useVueTable({
  data: managementOrganizations,
  columns,
  defaultColumn: { sortDescFirst: false },
  getRowId: (row) => row.id,
  getCoreRowModel: getCoreRowModel(),
  getSortedRowModel: getSortedRowModel(),
  getFilteredRowModel: getFilteredRowModel(),
  globalFilterFn: (row, _columnId, filterValue: string) =>
    matchesSearch(row.original, filterValue.trim().toLowerCase()),
  state: {
    get sorting() {
      return sorting.value
    },
    get globalFilter() {
      return search.value
    },
  },
  onSortingChange: (updater) => {
    sorting.value = typeof updater === 'function' ? updater(sorting.value) : updater
  },
})
</script>

<template>
  <section
    id="panel-organizations"
    role="tabpanel"
    aria-labelledby="tab-organizations"
    class="panel"
  >
    <div class="toolbar">
      <input
        v-model="search"
        type="search"
        placeholder="Search by name, role or nation"
        aria-label="Search organizations"
      />
      <ButtonLink type="button" button-style="primary" size="compact" @click="openCreate">
        Create organization
      </ButtonLink>
    </div>

    <SortableTable
      :table
      empty-message="No organizations found."
      no-match-message="No organizations match your search."
    />
    <p v-if="errorMessage" role="alert" class="error">{{ errorMessage }}</p>

    <ModalDialog ref="createDialog" title="Create organization" size="large">
      <OrganizationCreateForm :key="formKey" @created="created" />
    </ModalDialog>

    <ModalDialog
      ref="editDialog"
      :title="`Edit ${editingOrganization?.name ?? 'organization'}`"
      size="large"
    >
      <OrganizationEditForm
        v-if="editingOrganization"
        :key="formKey"
        :organization="editingOrganization"
        @changed="refresh"
        @close="editDialog?.close()"
      />
    </ModalDialog>
  </section>
</template>

<style scoped>
.panel {
  display: grid;
  gap: var(--spacing-md);
  align-content: start;
}

p {
  margin: 0;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-md);
}

.toolbar input {
  flex: 1 1 18rem;
  max-width: 28rem;
  padding: var(--spacing-sm) var(--spacing-md);
  border: 1px solid var(--input-border-color);
  border-radius: var(--default-border-radius);
}

.error {
  color: var(--error);
}
</style>
