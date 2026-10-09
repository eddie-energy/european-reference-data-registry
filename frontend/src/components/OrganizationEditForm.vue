<script setup lang="ts">
import ButtonLink from '@/components/ButtonLink.vue'
import { computed, ref } from 'vue'
import type { components } from '@/schema'
import { assignResponsibility, removeResponsibility, updateOrganization } from '@/api'
import { referenceDataObjects } from '@/stores/referenceDataObject'
import { nations } from '@/constants/nations'
import { useConfirmDialog } from '@/composables/confirm-dialog'
import useToast from '@/composables/useToast'

type Nation = components['schemas']['Nation']
type Role = components['schemas']['OrganizationRole']

const { organization } = defineProps<{
  organization: components['schemas']['ManagedOrganizationDto']
}>()

const emit = defineEmits<{ changed: []; close: [] }>()

const role = ref<Role>(organization.role ?? 'NDSF')
const selectedNations = ref<Nation[]>([...organization.nations])
const newObjectId = ref(referenceDataObjects.value?.[0]?.id ?? '')
const newNation = ref<Nation>('AUT')
const saving = ref(false)
const errorMessage = ref('')
const { confirm } = useConfirmDialog()
const { success } = useToast()

const isNdsf = computed(() => organization.role === 'NDSF')

const alreadyAssigned = computed(() =>
  organization.responsibilities.some(
    (item) => item.referenceDataObjectId === newObjectId.value && item.nation === newNation.value,
  ),
)

const objectName = (id: string) =>
  referenceDataObjects.value?.find((object) => object.id === id)?.name ?? id

const run = async (action: () => Promise<{ message?: string } | undefined>, done: string) => {
  saving.value = true
  errorMessage.value = ''
  try {
    const error = await action()
    if (error) {
      errorMessage.value = error.message ?? 'Request failed'
      return false
    }
    emit('changed')
    success(done)
    return true
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Request failed'
    return false
  } finally {
    saving.value = false
  }
}

const save = async () => {
  const saved = await run(
    async () =>
      (
        await updateOrganization(organization.id, {
          role: role.value,
          nations: role.value === 'NDSF' ? selectedNations.value : [],
        })
      ).error,
    'Organization updated',
  )
  if (saved) emit('close')
}

const assign = async () => {
  if (!newObjectId.value || alreadyAssigned.value) return
  await run(
    async () =>
      (
        await assignResponsibility(newObjectId.value, {
          organizationId: organization.id,
          nation: newNation.value,
        })
      ).error,
    'Responsibility assigned',
  )
}

const remove = async (item: components['schemas']['OrganizationResponsibilityDto']) => {
  if (!(await confirm('Remove responsibility', 'Remove this object and nation assignment?'))) {
    return
  }
  await run(
    async () =>
      (await removeResponsibility(item.referenceDataObjectId, organization.id, item.nation)).error,
    'Responsibility removed',
  )
}
</script>

<template>
  <div class="organization-edit">
    <form id="organization-form" class="section" @submit.prevent="save">
      <label>
        Role
        <select v-model="role">
          <option value="NDSF">NDSF</option>
          <option value="OPERATIONAL_ENTITY">Operational Entity</option>
        </select>
      </label>
      <fieldset v-if="role === 'NDSF'">
        <legend>Nations</legend>
        <label v-for="nation in nations" :key="nation.value" class="check">
          <input v-model="selectedNations" type="checkbox" :value="nation.value" />
          {{ nation.label }}
        </label>
      </fieldset>
      <p v-else-if="organization.responsibilities.length" class="hint">
        Saving as Operational Entity removes this organization's responsibilities.
      </p>
      <div class="actions">
        <ButtonLink type="submit" button-style="primary" :disabled="saving">Save</ButtonLink>
        <ButtonLink
          type="button"
          button-style="secondary"
          :disabled="saving"
          @click="emit('close')"
        >
          Cancel
        </ButtonLink>
      </div>
    </form>

    <section class="section">
      <h2>Responsibilities</h2>
      <p v-if="!organization.responsibilities.length" class="hint">No responsibilities assigned.</p>
      <table v-else>
        <thead>
          <tr>
            <th>Object</th>
            <th>Nation</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in organization.responsibilities"
            :key="`${item.referenceDataObjectId}:${item.nation}`"
          >
            <td>{{ objectName(item.referenceDataObjectId) }}</td>
            <td>{{ item.nation }}</td>
            <td>
              <ButtonLink
                type="button"
                button-style="error-secondary"
                size="compact"
                :disabled="saving"
                @click="remove(item)"
              >
                Remove
              </ButtonLink>
            </td>
          </tr>
        </tbody>
      </table>

      <p v-if="!isNdsf" class="hint">
        Only NDSF organizations can hold responsibilities. Save the role as NDSF first.
      </p>
      <p v-else-if="!referenceDataObjects?.length" class="hint">
        No reference data objects exist yet.
      </p>
      <form v-else class="add" @submit.prevent="assign">
        <label>
          Object
          <select v-model="newObjectId" required>
            <option v-for="object in referenceDataObjects" :key="object.id" :value="object.id">
              {{ object.name }}
            </option>
          </select>
        </label>
        <label>
          Nation
          <select v-model="newNation">
            <option v-for="nation in nations" :key="nation.value" :value="nation.value">
              {{ nation.label }}
            </option>
          </select>
        </label>
        <ButtonLink type="submit" button-style="primary" :disabled="saving || alreadyAssigned">
          Assign
        </ButtonLink>
      </form>
      <p v-if="isNdsf && alreadyAssigned" class="hint">This assignment already exists.</p>
    </section>

    <p v-if="errorMessage" role="alert" class="error">{{ errorMessage }}</p>
  </div>
</template>

<style scoped>
.organization-edit {
  display: grid;
  gap: var(--spacing-xlg);
}

.section {
  display: grid;
  gap: var(--spacing-sm);
}

fieldset {
  display: grid;
  gap: var(--spacing-xs);
  margin: 0;
  padding: var(--spacing-sm) var(--spacing-md);
  border: 1px solid var(--input-border-color);
  border-radius: var(--default-border-radius);
}

label {
  display: grid;
  gap: var(--spacing-xs);
}

.check {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

h2,
p {
  margin: 0;
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

.add {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: var(--spacing-md);
}

.actions {
  display: flex;
  gap: var(--spacing-sm);
}

.hint {
  opacity: 0.7;
}

.error {
  color: var(--error);
}
</style>
