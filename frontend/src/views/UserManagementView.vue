<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { updateReferenceDataObjects } from '@/stores/referenceDataObject'
import { updateManagementUsers } from '@/stores/managementUsers'
import { updateManagementOrganizations } from '@/stores/managementOrganizations'
import TabBar from '@/components/TabBar.vue'
import UsersPanel from '@/components/UsersPanel.vue'
import OrganizationsPanel from '@/components/OrganizationsPanel.vue'

const tabs = [
  { id: 'users', label: 'Users' },
  { id: 'organizations', label: 'Organizations' },
]

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const errorMessage = ref('')

const activeTab = computed({
  get: () => (route.query.tab === 'organizations' ? 'organizations' : 'users'),
  set: (tab: string) => {
    router.replace({ query: tab === 'users' ? {} : { tab } })
  },
})

const load = async () => {
  try {
    const results = await Promise.all([
      updateReferenceDataObjects(),
      updateManagementUsers(),
      updateManagementOrganizations(),
    ])
    errorMessage.value = results.find((result) => result.error)?.error?.message ?? ''
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Failed to load management data'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <main class="management">
    <h1>User management</h1>
    <TabBar v-model="activeTab" :tabs />

    <p v-if="loading">Loading…</p>
    <template v-else>
      <UsersPanel v-if="activeTab === 'users'" />
      <OrganizationsPanel v-else />
    </template>
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
