<script lang="ts" setup>
import type { components } from '@/schema'
import { computed } from 'vue'
import ButtonLink from './ButtonLink.vue'
import ArrowRightIcon from '@/assets/icons/ArrowRightIcon.svg'
import CardIcon from '@/assets/icons/CardIcon.svg'
import { userRole } from '@/stores/userInfo'
import { deleteReferenceDataObject, unlinkField } from '@/api'
import { updateReferenceDataObjects } from '@/stores/referenceDataObject'
import { useConfirmDialog } from '@/composables/confirm-dialog'
import useToast from '@/composables/useToast'

const { name, description, id, versions } =
  defineProps<components['schemas']['ReferenceDataObjectDetail']>()

const { confirm } = useConfirmDialog()
const { danger, success } = useToast()

const hasFields = computed(() => versions.some((version) => version.fields.length > 0))

const isDraftMode = computed(() => versions.every((version) => version.publishState === 'DRAFT'))

const canDeleteObject = computed(() => !hasFields.value || isDraftMode.value)

const deleteObject = async () => {
  if (!canDeleteObject.value) return
  const message = hasFields.value
    ? `Delete "${name}"? All of its fields will also be deleted. This cannot be undone.`
    : `Delete "${name}"? This cannot be undone.`
  if (!(await confirm('Delete reference data object', message))) return

  for (const version of versions) {
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
  success(`"${name}" deleted`)
  await updateReferenceDataObjects()
}

const visibleVersions = computed(() =>
  userRole.value === 'ceedsEntity'
    ? versions
    : versions.filter((version) => version.publishState === 'PUBLISHED'),
)
const latestVersion = computed(() => visibleVersions.value[visibleVersions.value.length - 1])
const latestPublishedVersion = computed(() =>
  [...visibleVersions.value].reverse().find((version) => version.publishState === 'PUBLISHED'),
)
const hasChanges = computed(
  () => latestVersion.value?.publishState === 'DRAFT' && !!latestPublishedVersion.value,
)

const iconAccentClass = computed(() => {
  if (hasChanges.value) return 'accent-lavender'
  if (latestVersion.value?.publishState === 'PUBLISHED') return 'accent-teal'
  return userRole.value === 'ceedsEntity' ? 'accent-peach' : 'accent-turquiese'
})
</script>

<template>
  <div class="reference-object-card">
    <div class="icon-chip" :class="iconAccentClass" aria-hidden="true">
      <CardIcon class="icon" />
    </div>
    <h3 class="name">{{ name }}</h3>
    <p>{{ description }}</p>
    <p v-if="userRole === 'ceedsEntity' && latestVersion" class="version-info">
      <template v-if="hasChanges">
        Version {{ latestPublishedVersion!.versionCode }} ({{ latestVersion.versionCode }})
        <span class="chip chip-changes">HAS CHANGES</span>
      </template>
      <template v-else>
        Version {{ latestVersion.versionCode }}
        <span
          class="chip"
          :class="latestVersion.publishState === 'PUBLISHED' ? 'chip-published' : 'chip-draft'"
        >
          {{ latestVersion.publishState }}
        </span>
      </template>
    </p>
    <div class="actions">
      <ButtonLink
        v-if="userRole === 'ceedsEntity'"
        component="RouterLink"
        :to="{ path: `/reference-data-objects/${id}`, query: { tab: 'edit' } }"
        class="edit-button"
      >
        Edit
      </ButtonLink>
      <ButtonLink
        v-if="userRole === 'ceedsEntity'"
        component="button"
        buttonStyle="error-secondary"
        class="delete-button"
        :disabled="!canDeleteObject"
        :title="!canDeleteObject ? 'Remove all fields before deleting this object' : undefined"
        @click="deleteObject"
      >
        Delete
      </ButtonLink>
      <ButtonLink
        component="RouterLink"
        :to="`/reference-data-objects/${id}`"
        buttonStyle="secondary"
        class="explore-button"
      >
        Explore <ArrowRightIcon />
      </ButtonLink>
    </div>
  </div>
</template>

<style scoped>
.reference-object-card {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  height: 100%;
  width: 100%;
  padding: var(--spacing-xxl) var(--spacing-xlg);
  gap: var(--spacing-md);
  background-color: var(--light);
  border-radius: var(--default-border-radius);
  box-shadow: var(--card-shadow);
  text-align: left;
  transition:
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.reference-object-card:hover {
  box-shadow: var(--card-shadow-hover);
  transform: translateY(-2px);
}

.icon-chip {
  display: flex;
  align-items: center;
  justify-content: center;
}

.name {
  margin: 0;
  font-size: 1.375rem;
  font-weight: 700;
  text-wrap: wrap;
}

p {
  margin: 0 0 var(--spacing-md);
  flex: 1;
  color: var(--dark);
}

.icon {
  color: inherit;
}

.version-info {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex: none;
  margin: 0;
  font-size: 0.85rem;
  color: var(--dark);
}

.actions {
  display: flex;
  gap: var(--spacing-sm);
  margin-top: auto;
  width: 100%;
  justify-content: flex-start;
}
</style>
