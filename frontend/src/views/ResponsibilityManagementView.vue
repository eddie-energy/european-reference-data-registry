<script setup lang="ts">
import { onMounted, ref } from 'vue'
import OrganizationsPanel from '@/components/OrganizationsPanel.vue'
import { updateReferenceDataObjects } from '@/stores/referenceDataObject'
import { updateManagementOrganizations } from '@/stores/managementOrganizations'

const loading = ref(true)
const errorMessage = ref('')

onMounted(async () => {
  try {
    const results = await Promise.all([
      updateReferenceDataObjects(),
      updateManagementOrganizations(),
    ])
    errorMessage.value = results.find((result) => result.error)?.error?.message ?? ''
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Failed to load responsibilities'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <main class="management">
    <h1>Responsibilities</h1>
    <p>Assign organizations to reference data objects and nations.</p>
    <p v-if="loading">Loading…</p>
    <OrganizationsPanel v-else />
    <p v-if="errorMessage" role="alert" class="error">{{ errorMessage }}</p>
  </main>
</template>

<style scoped>
.management {
  display: grid;
  gap: var(--spacing-md);
  align-content: start;
  padding: var(--spacing-xxl);
}

h1,
p {
  margin: 0;
}

.error {
  color: var(--error);
}
</style>
