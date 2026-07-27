<script lang="ts" setup>
import { referenceDataObject, updateReferenceDataObject } from '@/stores/referenceDataObject'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { userRole } from '@/stores/userInfo'
import EntryTable from '@/components/EntryTable.vue'
import ReferenceDataObjectEditor from '@/components/ReferenceDataObjectEditor.vue'

const { id } = defineProps<{ id: string }>()
const route = useRoute()

const load = () => updateReferenceDataObject(id)

onMounted(load)
watch(() => id, load)

const browseVersion = computed(() => {
  const versions = referenceDataObject.value?.versions ?? []
  const visible =
    userRole.value === 'ceedsEntity'
      ? versions
      : versions.filter((version) => version.publishState === 'PUBLISHED')
  return visible[visible.length - 1]
})

type TabKey = 'browse' | 'api' | 'process' | 'edit'

const initialTab = (): TabKey =>
  route.query.tab === 'edit' && userRole.value === 'ceedsEntity' ? 'edit' : 'browse'

const activeTab = ref<TabKey>(initialTab())

const visibleTabs = computed(() => {
  const tabs: { key: TabKey; label: string }[] = [
    { key: 'browse', label: 'Browse' },
    { key: 'api', label: 'API' },
    { key: 'process', label: 'Process' },
  ]
  if (userRole.value === 'ceedsEntity') {
    tabs.push({ key: 'edit', label: 'Edit' })
  }
  return tabs
})

watch(userRole, () => {
  if (activeTab.value === 'edit' && userRole.value !== 'ceedsEntity') {
    activeTab.value = 'browse'
  }
})
</script>

<template>
  <main class="reference-data-object">
    <template v-if="referenceDataObject">
      <p class="breadcrumb">
        <RouterLink to="/">S3 Reference Data Registry</RouterLink> →
        {{ referenceDataObject.name }}
      </p>
      <h1>{{ referenceDataObject.name }}</h1>
      <p>{{ referenceDataObject.description }}</p>

      <nav class="tabs">
        <button
          v-for="tab in visibleTabs"
          :key="tab.key"
          type="button"
          class="tab"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </nav>

      <section v-if="activeTab === 'browse'">
        <EntryTable
          v-if="browseVersion"
          :id
          :version="browseVersion"
          :editable="userRole === 'ceedsEntity'"
        />
      </section>

      <section v-else-if="activeTab === 'api' || activeTab === 'process'">
        <p>Coming soon.</p>
      </section>

      <section v-else-if="activeTab === 'edit' && userRole === 'ceedsEntity'">
        <ReferenceDataObjectEditor :id />
      </section>
    </template>
  </main>
</template>

<style scoped>
.reference-data-object {
  padding: var(--spacing-xxl);
}

.breadcrumb {
  color: var(--dark);
  font-size: 0.85rem;
  opacity: 0.7;
}

.breadcrumb a {
  color: inherit;
  text-decoration: none;
}

.tabs {
  display: flex;
  gap: var(--spacing-sm);
  border-bottom: 1px solid #e4e4e4;
  margin-bottom: var(--spacing-lg);
  padding-bottom: var(--spacing-sm);
}

.tab {
  padding: var(--spacing-sm) var(--spacing-lg);
  border: none;
  border-radius: var(--pill-radius);
  background: none;
  color: var(--dark);
  font-size: 1rem;
  font-weight: 500;
  cursor: pointer;
  transition: var(--theme-transition);
}

.tab:hover:not(.active) {
  background-color: var(--lavender-tint-bg);
  color: var(--lavender-tint-text);
}

.tab.active {
  background-color: var(--lavender);
  color: var(--light);
  font-weight: 600;
}
</style>
