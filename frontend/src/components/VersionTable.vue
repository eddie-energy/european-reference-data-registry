<script setup lang="ts">
defineProps<{
  versionCode: number
  publishState: 'DRAFT' | 'PUBLISHED'
  colspan: number
}>()
</script>

<template>
  <div class="table-scroll">
    <table>
      <thead>
        <tr class="version-row">
          <th :colspan>
            <span class="version-label">
              Version {{ versionCode }}
              <span
                class="chip"
                :class="publishState === 'PUBLISHED' ? 'chip-published' : 'chip-draft'"
              >
                {{ publishState }}
              </span>
            </span>
          </th>
        </tr>
        <slot name="header" />
      </thead>
      <tbody>
        <slot />
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.table-scroll {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}

:deep(th),
:deep(td) {
  text-align: left;
  padding: var(--spacing-sm) var(--spacing-md);
  border-bottom: 1px solid var(--border-color);
  white-space: nowrap;
}

.version-row th {
  background-color: var(--surface-alt);
  font-weight: 600;
}

.version-label {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}
</style>
