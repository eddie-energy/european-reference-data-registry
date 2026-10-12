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
import { managementUsers, updateManagementUsers } from '@/stores/managementUsers'
import { managementOrganizations } from '@/stores/managementOrganizations'
import { nations } from '@/constants/nations'
import ModalDialog from '@/components/ModalDialog.vue'
import SortableTable from '@/components/SortableTable.vue'
import UserEditForm from '@/components/UserEditForm.vue'

type ManagementUser = components['schemas']['ManagementUserDto']

const errorMessage = ref('')
const search = ref('')
const sorting = ref<SortingState>([{ id: 'username', desc: false }])
const dialog = ref<InstanceType<typeof ModalDialog>>()
const editingId = ref('')
const formKey = ref(0)

const editingUser = computed(() =>
  managementUsers.value.find((user) => user.id === editingId.value),
)

const dialogTitle = computed(() =>
  editingId.value ? `Edit ${editingUser.value?.username ?? 'user'}` : 'Create user',
)

const countryLabel = (country?: components['schemas']['Nation'] | null) =>
  nations.find((nation) => nation.value === country)?.label

const refresh = async () => {
  const { error } = await updateManagementUsers()
  errorMessage.value = error?.message ?? ''
}

const openEdit = (userId: string) => {
  editingId.value = userId
  formKey.value++
  dialog.value?.showModal()
}

const openCreate = () => {
  editingId.value = ''
  formKey.value++
  dialog.value?.showModal()
}

const columnHelper = createColumnHelper<ManagementUser>()

const columns: ColumnDef<ManagementUser, any>[] = [
  columnHelper.accessor('username', { header: 'Username' }),
  columnHelper.accessor((user) => countryLabel(user.country), {
    id: 'country',
    header: 'Country',
    sortUndefined: 'last',
    cell: (ctx) => ctx.getValue() ?? h('span', { class: 'none' }, '—'),
  }),
  columnHelper.accessor((user) => user.organization?.name, {
    id: 'organization',
    header: 'Organization',
    sortUndefined: 'last',
    cell: (ctx) =>
      ctx.getValue()
        ? h('ul', { class: 'chips' }, [h('li', ctx.getValue())])
        : h('span', { class: 'none' }, '—'),
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

const matchesSearch = (user: ManagementUser, query: string) =>
  [
    user.username,
    countryLabel(user.country) ?? '',
    user.country ?? '',
    user.organization?.name ?? '',
    user.organization?.alias ?? '',
  ].some((value) => value.toLowerCase().includes(query))

const table = useVueTable({
  data: managementUsers,
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
  <section id="panel-users" role="tabpanel" aria-labelledby="tab-users" class="panel">
    <div class="toolbar">
      <input
        v-model="search"
        type="search"
        placeholder="Search by username, country or organization"
        aria-label="Search users"
      />
      <ButtonLink type="button" button-style="primary" size="compact" @click="openCreate">
        Create user
      </ButtonLink>
    </div>

    <SortableTable
      :table
      empty-message="No users found."
      no-match-message="No users match your search."
    />
    <p v-if="errorMessage" role="alert" class="error">{{ errorMessage }}</p>

    <ModalDialog ref="dialog" :title="dialogTitle" size="large">
      <UserEditForm
        v-if="!editingId || editingUser"
        :key="formKey"
        :user="editingUser"
        :organizations="managementOrganizations"
        @changed="refresh"
        @close="dialog?.close()"
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
