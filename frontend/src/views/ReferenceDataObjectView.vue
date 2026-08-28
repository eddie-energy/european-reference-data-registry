<script lang="ts" setup>
import { referenceDataObject, updateReferenceDataObject } from '@/stores/referenceDataObject'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { userRole } from '@/stores/userInfo'
import EntryTable from '@/components/EntryTable.vue'
import ReferenceDataObjectEditor from '@/components/ReferenceDataObjectEditor.vue'

const { id } = defineProps<{ id: string }>()
const route = useRoute()
const router = useRouter()

const loading = ref(true)
const errorMessage = ref('')

const parseVersionCode = (raw: unknown): number | undefined => {
  const parsed = typeof raw === 'string' ? Number(raw) : Number.NaN
  return Number.isFinite(parsed) ? parsed : undefined
}

const selectedVersionCode = ref<number>()

const load = async () => {
  loading.value = true
  errorMessage.value = ''
  selectedVersionCode.value = parseVersionCode(route.query.version)
  const { error } = await updateReferenceDataObject(id)
  if (error) {
    errorMessage.value = error.message ?? 'Failed to load this reference data object'
  }
  loading.value = false
}

onMounted(load)
watch(() => id, load)

const mayEditVersions = computed(
  () => userRole.value === 'operationalEntity' || userRole.value === 'ndsf',
)

const visibleVersions = computed(() => {
  const versions = referenceDataObject.value?.versions ?? []
  return mayEditVersions.value
    ? versions
    : versions.filter((version) => version.publishState === 'PUBLISHED')
})

const browseVersion = computed(() => {
  const versions = visibleVersions.value
  if (mayEditVersions.value && selectedVersionCode.value != null) {
    const match = versions.find((version) => version.versionCode === selectedVersionCode.value)
    if (match) return match
  }
  return versions[versions.length - 1]
})

const isLatestBrowseVersion = computed(
  () => browseVersion.value?.id === visibleVersions.value[visibleVersions.value.length - 1]?.id,
)

const versionSwitchModel = computed<number | undefined>({
  get: () => selectedVersionCode.value ?? browseVersion.value?.versionCode,
  set: (value) => {
    selectedVersionCode.value = value
  },
})

type TabKey = 'browse' | 'api' | 'process' | 'edit'

const initialTab = (): TabKey =>
  route.query.tab === 'edit' && mayEditVersions.value ? 'edit' : 'browse'

const activeTab = ref<TabKey>(initialTab())

const visibleTabs = computed(() => {
  const tabs: { key: TabKey; label: string }[] = [
    { key: 'browse', label: 'Browse' },
    { key: 'api', label: 'API' },
    { key: 'process', label: 'Process' },
  ]
  if (mayEditVersions.value) {
    tabs.push({ key: 'edit', label: 'Edit' })
  }
  return tabs
})

watch(userRole, () => {
  if (activeTab.value === 'edit' && !mayEditVersions.value) {
    activeTab.value = 'browse'
  }
  if (!mayEditVersions.value) {
    selectedVersionCode.value = undefined
  }
})

watch([activeTab, selectedVersionCode], ([tab, version]) => {
  const { version: _current, ...rest } = route.query
  router.replace({
    query:
      version == null
        ? { ...rest, tab }
        : {
            ...rest,
            tab,
            version: String(version),
          },
  })
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
        <div
          v-if="mayEditVersions && visibleVersions.length > 1"
          class="version-switch"
        >
          <label for="versionSelect">Version</label>
          <select id="versionSelect" v-model.number="versionSwitchModel">
            <option
              v-for="version in [...visibleVersions].reverse()"
              :key="version.id"
              :value="version.versionCode"
            >
              Version {{ version.versionCode }} ({{ version.publishState }})
            </option>
          </select>
        </div>
        <EntryTable
          v-if="browseVersion"
          :id
          :version="browseVersion"
          :editable="
            isLatestBrowseVersion &&
            (userRole === 'operationalEntity' ||
              (userRole === 'ndsf' && browseVersion.publishState === 'PUBLISHED'))
          "
        />
      </section>

      <section v-else-if="activeTab === 'api' || activeTab === 'process'">
        <p>Coming soon.</p>
      </section>

      <section v-else-if="activeTab === 'edit' && mayEditVersions">
        <ReferenceDataObjectEditor :id />
      </section>
    </template>
    <p v-else-if="loading" class="state-message">Loading…</p>
    <p v-else-if="errorMessage" class="state-message error">{{ errorMessage }}</p>
    <p v-else class="state-message">Reference data object not found.</p>
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

.state-message {
  opacity: 0.7;
}

.state-message.error {
  color: var(--error);
  opacity: 1;
}

.tabs {
  display: flex;
  gap: var(--spacing-sm);
  border-bottom: 1px solid var(--border-color);
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

.version-switch {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-lg);
  font-size: 0.9rem;
}

.version-switch select {
  width: auto;
}
</style>
