<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import type { components } from '@/schema'
import {
  assignResponsibility,
  listEligibleOrganizations,
  listResponsibilities,
  removeResponsibility,
} from '@/api'
import { referenceDataObjects, updateReferenceDataObjects } from '@/stores/referenceDataObject'
import { nations } from '@/constants/nations'
import { useConfirmDialog } from '@/composables/confirm-dialog'
import useToast from '@/composables/useToast'

type Organization = components['schemas']['EligibleOrganizationDto']
type Responsibility = components['schemas']['ResponsibilityDto']
type Nation = components['schemas']['Nation']

const organizations = ref<Organization[]>([])
const responsibilities = ref<Responsibility[]>([])
const selectedObjectId = ref('')
const selectedOrganizationId = ref('')
const selectedNation = ref<Nation>('AUT')
const loading = ref(true)
const saving = ref(false)
const errorMessage = ref('')
const { confirm } = useConfirmDialog()
const { success } = useToast()

const selectedOrganization = computed(() =>
  organizations.value.find((organization) => organization.id === selectedOrganizationId.value),
)

const alreadyAssigned = computed(() =>
  responsibilities.value.some(
    (item) =>
      item.organizationId === selectedOrganizationId.value && item.nation === selectedNation.value,
  ),
)

const organizationName = (id: string) =>
  organizations.value.find((organization) => organization.id === id)?.name ?? id

const loadAssignments = async () => {
  responsibilities.value = []
  const objectId = selectedObjectId.value
  if (!objectId) return
  try {
    const { data, error } = await listResponsibilities(objectId)
    if (objectId !== selectedObjectId.value) return
    if (!data) {
      errorMessage.value = error?.message ?? 'Failed to load responsibilities'
      return
    }
    responsibilities.value = data
  } catch (error) {
    if (objectId === selectedObjectId.value) {
      errorMessage.value =
        error instanceof Error ? error.message : 'Failed to load responsibilities'
    }
  }
}

const load = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const [objectsResult, organizationsResult] = await Promise.all([
      updateReferenceDataObjects(),
      listEligibleOrganizations(),
    ])
    if (organizationsResult.data) {
      organizations.value = organizationsResult.data
      selectedOrganizationId.value = organizations.value[0]?.id ?? ''
    }
    errorMessage.value = objectsResult.error?.message ?? organizationsResult.error?.message ?? ''
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Failed to load management data'
  } finally {
    selectedObjectId.value = referenceDataObjects.value?.[0]?.id ?? ''
    loading.value = false
  }
}

const assign = async () => {
  if (!selectedObjectId.value || !selectedOrganizationId.value || alreadyAssigned.value) return
  saving.value = true
  errorMessage.value = ''
  try {
    const { error } = await assignResponsibility(selectedObjectId.value, {
      organizationId: selectedOrganizationId.value,
      nation: selectedNation.value,
    })
    if (error) {
      errorMessage.value = error.message ?? 'Failed to assign responsibility'
      return
    }
    await loadAssignments()
    success('Responsibility assigned')
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Failed to assign responsibility'
  } finally {
    saving.value = false
  }
}

const remove = async (item: Responsibility) => {
  if (
    !(await confirm('Remove responsibility', 'Remove this organization and nation assignment?'))
  ) {
    return
  }
  saving.value = true
  errorMessage.value = ''
  try {
    const { error } = await removeResponsibility(
      selectedObjectId.value,
      item.organizationId,
      item.nation,
    )
    if (error) {
      errorMessage.value = error.message ?? 'Failed to remove responsibility'
      return
    }
    await loadAssignments()
    success('Responsibility removed')
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Failed to remove responsibility'
  } finally {
    saving.value = false
  }
}

watch(selectedObjectId, loadAssignments)
onMounted(load)
</script>

<template>
  <main class="management">
    <h1>Manage responsibilities</h1>
    <p>Assign NDSF organizations to maintain reference data for one nation on each object.</p>

    <p v-if="loading">Loading…</p>
    <template v-else>
      <label>
        Reference data object
        <select v-model="selectedObjectId">
          <option v-for="object in referenceDataObjects" :key="object.id" :value="object.id">
            {{ object.name }}
          </option>
        </select>
      </label>

      <p v-if="!selectedObjectId">No reference data objects available.</p>
      <template v-else>
        <h2>Assignments</h2>
        <p v-if="!responsibilities.length">No organizations assigned yet.</p>
        <table v-else>
          <thead>
            <tr>
              <th>Organization</th>
              <th>Assignment nation</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in responsibilities" :key="`${item.organizationId}:${item.nation}`">
              <td>{{ organizationName(item.organizationId) }}</td>
              <td>{{ nations.find((nation) => nation.value === item.nation)?.label }}</td>
              <td>
                <button type="button" :disabled="saving" @click="remove(item)">Remove</button>
              </td>
            </tr>
          </tbody>
        </table>

        <h2>Add assignment</h2>
        <p v-if="!organizations.length">No NDSF organizations available in Keycloak.</p>
        <form v-else @submit.prevent="assign">
          <label>
            Organization
            <select v-model="selectedOrganizationId" required>
              <option
                v-for="organization in organizations"
                :key="organization.id"
                :value="organization.id"
              >
                {{ organization.name }} ({{ organization.alias }})
              </option>
            </select>
          </label>
          <p v-if="selectedOrganization" class="hint">
            Keycloak nation:
            {{ selectedOrganization.keycloakNations.join(', ') || 'None set' }}
          </p>
          <label>
            Assignment nation
            <select v-model="selectedNation">
              <option v-for="nation in nations" :key="nation.value" :value="nation.value">
                {{ nation.label }}
              </option>
            </select>
          </label>
          <button type="submit" :disabled="saving || alreadyAssigned">Assign</button>
          <p v-if="alreadyAssigned">This assignment already exists.</p>
        </form>
      </template>
    </template>
    <p v-if="errorMessage" role="alert" class="error">{{ errorMessage }}</p>
  </main>
</template>

<style scoped>
.management {
  display: grid;
  gap: var(--spacing-md);
  max-width: 55rem;
  padding: var(--spacing-xxl);
}

h1,
h2,
p {
  margin: 0;
}

h2 {
  margin-top: var(--spacing-lg);
}

form,
label {
  display: grid;
  gap: var(--spacing-sm);
}

form {
  max-width: 28rem;
}

table {
  border-collapse: collapse;
  text-align: left;
}

th,
td {
  padding: var(--spacing-sm);
  border-bottom: 1px solid var(--border-color);
}

button {
  justify-self: start;
  padding: var(--spacing-sm) var(--spacing-md);
  cursor: pointer;
}

.hint {
  opacity: 0.7;
}

.error {
  color: var(--error);
}
</style>
