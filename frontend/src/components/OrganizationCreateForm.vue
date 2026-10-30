<script setup lang="ts">
import ButtonLink from '@/components/ButtonLink.vue'
import CustomSelect from '@/components/CustomSelect.vue'
import { computed, ref } from 'vue'
import type { components } from '@/schema'
import { createOrganization } from '@/api'
import { nations } from '@/constants/nations'
import useToast from '@/composables/useToast'
import { organizationRoleLabels } from '@/constants/organizationRoles'

type Nation = components['schemas']['Nation']
type Role = components['schemas']['OrganizationRole']

const emit = defineEmits<{
  created: [organization: components['schemas']['ManagedOrganizationDto']]
}>()

const name = ref('')
const roles = ref<Role[]>(['NDSF'])
const hasNdsfRole = computed(() => roles.value.some((role) => role !== 'OPERATIONAL_ENTITY'))
const selectedNations = ref<Nation[]>([])
const saving = ref(false)
const errorMessage = ref('')
const { success } = useToast()

const submit = async () => {
  saving.value = true
  errorMessage.value = ''
  try {
    const { data, error } = await createOrganization({
      name: name.value.trim(),
      roles: roles.value,
      nations: hasNdsfRole.value ? selectedNations.value : [],
    })
    if (!data) {
      errorMessage.value = error?.message ?? 'Failed to create organization'
      return
    }
    success('Organization created')
    emit('created', data)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Failed to create organization'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <fieldset class="org-create" aria-label="New organization">
    <label>
      Organization name
      <input v-model="name" required minlength="2" maxlength="255" autocomplete="off" />
    </label>
    <fieldset>
      <legend>Roles</legend>
      <label v-for="(label, role) in organizationRoleLabels" :key="role" class="check">
        <input v-model="roles" type="checkbox" :value="role" />
        {{ label }}
      </label>
    </fieldset>
    <div v-if="hasNdsfRole" class="field">
      <span>Nations</span>
      <CustomSelect
        v-model="selectedNations"
        :options="nations"
        placeholder="Select nations"
        multiple
      />
    </div>
    <div class="actions">
      <ButtonLink
        type="button"
        button-style="primary"
        :disabled="saving || name.trim().length < 2 || !roles.length"
        @click="submit"
      >
        Create organization
      </ButtonLink>
    </div>
    <p v-if="errorMessage" role="alert" class="error">{{ errorMessage }}</p>
  </fieldset>
</template>

<style scoped>
.org-create {
  min-width: 0;
  margin: 0;
  display: grid;
  gap: var(--spacing-sm);
  padding: var(--spacing-md);
  border: 1px dashed var(--input-border-color);
  border-radius: var(--default-border-radius);
}

label,
.field {
  display: grid;
  gap: var(--spacing-xs);
}

fieldset {
  display: grid;
  gap: var(--spacing-xs);
  margin: 0;
  padding: var(--spacing-sm) var(--spacing-md);
  border: 1px solid var(--input-border-color);
  border-radius: var(--default-border-radius);
}

.check {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

p {
  margin: 0;
}

.error {
  color: var(--error);
}
</style>
