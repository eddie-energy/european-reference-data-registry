<script lang="ts" setup>
import {
  referenceDataObject,
  updateReferenceDataObject,
  updateReferenceDataObjects,
} from '@/stores/referenceDataObject'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { userRole } from '@/stores/userInfo'
import { createVersion, publishVersion, deleteReferenceDataObject, unlinkField } from '@/api'
import FieldForm from '@/components/FieldForm.vue'
import ButtonLink from '@/components/ButtonLink.vue'
import { useConfirmDialog } from '@/composables/confirm-dialog'
import useToast from '@/composables/useToast'

const { id } = defineProps<{ id: string }>()
const route = useRoute()
const router = useRouter()
const { confirm } = useConfirmDialog()
const { danger, success } = useToast()

const load = () => updateReferenceDataObject(id)

onMounted(load)
watch(() => id, load)

const draftVersion = computed(() => {
  const versions = referenceDataObject.value?.versions
  const lastVersion = versions?.[versions.length - 1]
  return lastVersion?.publishState === 'DRAFT' ? lastVersion : undefined
})

const browseVersions = computed(() => {
  const versions = referenceDataObject.value?.versions ?? []
  return userRole.value === 'ceedsEntity'
    ? versions
    : versions.filter((version) => version.publishState === 'PUBLISHED')
})

const startNewVersion = async () => {
  await createVersion(id)
  await load()
}

const publish = async () => {
  if (!draftVersion.value) return
  await publishVersion(id, draftVersion.value.id)
  await load()
}

const hasFields = computed(() =>
  (referenceDataObject.value?.versions ?? []).some((version) => version.fields.length > 0),
)

const isDraftMode = computed(() =>
  (referenceDataObject.value?.versions ?? []).every((version) => version.publishState === 'DRAFT'),
)

const canDeleteObject = computed(() => !hasFields.value || isDraftMode.value)

const fieldUsedElsewhere = (fieldId: string) =>
  (referenceDataObject.value?.versions ?? []).some(
    (version) =>
      version.id !== draftVersion.value?.id && version.fields.some((field) => field.id === fieldId),
  )

const deleteField = async (fieldId: string, fieldName: string) => {
  if (!draftVersion.value || fieldUsedElsewhere(fieldId)) return
  if (!(await confirm('Delete field', `Delete "${fieldName}"? This cannot be undone.`))) return
  const { error } = await unlinkField(id, draftVersion.value.id, fieldId)
  if (error) {
    danger(error.message ?? 'Failed to delete field')
    return
  }
  success(`Field "${fieldName}" deleted`)
  await load()
}

const deleteObject = async () => {
  if (!canDeleteObject.value) return
  const objectName = referenceDataObject.value?.name
  const message = hasFields.value
    ? 'Delete this reference data object? All of its fields will also be deleted. This cannot be undone.'
    : 'Delete this reference data object? This cannot be undone.'
  if (!(await confirm('Delete reference data object', message))) return

  for (const version of referenceDataObject.value?.versions ?? []) {
    for (const field of version.fields) {
      const { error } = await unlinkField(id, version.id, field.id)
      if (error) {
        danger(error.message ?? 'Failed to delete field')
        return
      }
    }
  }

  const { error } = await deleteReferenceDataObject(id)
  if (error) {
    danger(error.message ?? 'Failed to delete — remove all fields first')
    return
  }
  success(`"${objectName}" deleted`)
  await updateReferenceDataObjects()
  router.push({ name: 'dashboard' })
}

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
        <section v-for="version in browseVersions" :key="version.id">
          <h2 class="version-heading">
            Version {{ version.versionCode }}
            <span
              class="chip"
              :class="version.publishState === 'PUBLISHED' ? 'chip-published' : 'chip-draft'"
            >
              {{ version.publishState }}
            </span>
          </h2>
          <ul>
            <li v-for="field in version.fields" :key="field.id">
              {{ field.name }} — {{ field.dataType }}
              <template v-if="field.mandatory">(mandatory)</template>
              <template v-if="field.nation">— {{ field.nation }}</template>
            </li>
          </ul>
        </section>
      </section>

      <section v-else-if="activeTab === 'api' || activeTab === 'process'">
        <p>Coming soon.</p>
      </section>

      <section v-else-if="activeTab === 'edit' && userRole === 'ceedsEntity'">
        <h2>Edit</h2>
        <ButtonLink
          component="button"
          buttonStyle="error-secondary"
          size="compact"
          :disabled="!canDeleteObject"
          :title="!canDeleteObject ? 'Remove all fields before deleting this object' : undefined"
          @click="deleteObject"
        >
          Delete reference data object
        </ButtonLink>
        <template v-if="draftVersion">
          <ul class="draft-fields">
            <li v-for="field in draftVersion.fields" :key="field.id">
              {{ field.name }} — {{ field.dataType }}
              <ButtonLink
                component="button"
                buttonStyle="error-secondary"
                size="compact"
                :disabled="fieldUsedElsewhere(field.id)"
                :title="fieldUsedElsewhere(field.id) ? 'Field is used by another version' : undefined"
                @click="deleteField(field.id, field.name)"
              >
                Delete
              </ButtonLink>
            </li>
          </ul>
          <FieldForm :id :version-id="draftVersion.id" @created="load" />
          <ButtonLink component="button" buttonStyle="secondary" size="compact" @click="publish">
            Publish version {{ draftVersion.versionCode }}
          </ButtonLink>
        </template>
        <template v-else>
          <ButtonLink component="button" buttonStyle="secondary" size="compact" @click="startNewVersion">
            Start new version to add fields
          </ButtonLink>
        </template>
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

.version-heading {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.draft-fields li {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}
</style>
