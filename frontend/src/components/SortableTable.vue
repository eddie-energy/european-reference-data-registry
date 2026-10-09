<script setup lang="ts" generic="T">
import { FlexRender } from '@tanstack/vue-table'
import type { Table } from '@tanstack/vue-table'

const { table, emptyMessage, noMatchMessage } = defineProps<{
  table: Table<T>
  emptyMessage: string
  noMatchMessage: string
}>()

const ariaSortValues = { asc: 'ascending', desc: 'descending' } as const

const ariaSort = (sorted: false | 'asc' | 'desc') => (sorted ? ariaSortValues[sorted] : 'none')
</script>

<template>
  <p v-if="!table.options.data.length" class="state">{{ emptyMessage }}</p>
  <p v-else-if="!table.getRowModel().rows.length" class="state">{{ noMatchMessage }}</p>
  <div v-else class="table-scroll">
    <table>
      <thead>
        <tr v-for="headerGroup in table.getHeaderGroups()" :key="headerGroup.id">
          <th
            v-for="header in headerGroup.headers"
            :key="header.id"
            :aria-sort="
              header.column.getCanSort() ? ariaSort(header.column.getIsSorted()) : undefined
            "
          >
            <button
              v-if="header.column.getCanSort()"
              type="button"
              class="sort-button"
              @click="header.column.getToggleSortingHandler()?.($event)"
            >
              <FlexRender
                v-if="!header.isPlaceholder"
                :render="header.column.columnDef.header"
                :props="header.getContext()"
              />
              <span v-if="header.column.getIsSorted() === 'asc'" aria-hidden="true">▲</span>
              <span v-else-if="header.column.getIsSorted() === 'desc'" aria-hidden="true">▼</span>
              <span v-else class="sort-hint" aria-hidden="true">⇅</span>
            </button>
            <FlexRender
              v-else-if="!header.isPlaceholder"
              :render="header.column.columnDef.header"
              :props="header.getContext()"
            />
          </th>
        </tr>
      </thead>
      <TransitionGroup tag="tbody" name="row">
        <tr v-for="row in table.getRowModel().rows" :key="row.id">
          <td v-for="cell in row.getVisibleCells()" :key="cell.id">
            <FlexRender :render="cell.column.columnDef.cell" :props="cell.getContext()" />
          </td>
        </tr>
      </TransitionGroup>
    </table>
  </div>
</template>

<style scoped>
.state {
  margin: 0;
}

.table-scroll {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
}

th,
td {
  padding: var(--spacing-sm) var(--spacing-md);
  border-bottom: 1px solid var(--border-color);
  vertical-align: top;
}

.row-move,
.row-enter-active,
.row-leave-active {
  transition:
    transform 0.25s ease,
    opacity 0.25s ease;
}

.row-enter-from,
.row-leave-to {
  opacity: 0;
}

.row-enter-from {
  transform: translateY(calc(-1 * var(--spacing-sm)));
}

@media (prefers-reduced-motion: reduce) {
  .row-move,
  .row-enter-active,
  .row-leave-active {
    transition: none;
  }
}

.sort-button {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: 0;
  border: none;
  background: none;
  color: inherit;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}

.sort-button:hover {
  color: var(--teal);
}

.sort-hint {
  opacity: 0.4;
}

:deep(.chips) {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-xs);
  margin: 0;
  padding: 0;
  list-style: none;
}

:deep(.chips li) {
  padding: var(--spacing-xs) var(--spacing-sm);
  border-radius: var(--chip-radius);
  background: var(--teal-tint-bg);
  color: var(--teal-tint-text);
}

:deep(.none) {
  opacity: 0.7;
}
</style>
