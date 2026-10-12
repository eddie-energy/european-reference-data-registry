<script setup lang="ts">
import ButtonLink from '@/components/ButtonLink.vue'
import { ref } from 'vue'
import type { components } from '@/schema'
import { createManagementUser, updateManagementUser } from '@/api'
import { nations } from '@/constants/nations'
import useToast from '@/composables/useToast'

type Nation = components['schemas']['Nation']

const { user, organizations } = defineProps<{
  user?: components['schemas']['ManagementUserDto']
  organizations: components['schemas']['ManagedOrganizationDto'][]
}>()

const emit = defineEmits<{ changed: []; close: [] }>()

const username = ref('')
const temporaryPassword = ref('')
const country = ref<Nation | ''>(user?.country ?? '')
const organizationId = ref(user?.organization?.id ?? '')
const saving = ref(false)
const errorMessage = ref('')
const { success } = useToast()

const save = async () => {
  saving.value = true
  errorMessage.value = ''
  try {
    const shared = {
      country: country.value || null,
      organizationId: organizationId.value || null,
    }
    const { error } = user
      ? await updateManagementUser(user.id, shared)
      : await createManagementUser({
          username: username.value.trim(),
          temporaryPassword: temporaryPassword.value,
          ...shared,
        })
    if (error) {
      errorMessage.value = error.message ?? 'Request failed'
      return
    }
    emit('changed')
    success(user ? 'User updated' : 'User created')
    emit('close')
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Request failed'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <form class="user-edit" @submit.prevent="save">
    <template v-if="!user">
      <label>
        Username
        <input v-model="username" required minlength="3" maxlength="255" autocomplete="off" />
      </label>
      <label>
        Temporary password
        <input
          v-model="temporaryPassword"
          type="password"
          required
          minlength="8"
          autocomplete="new-password"
        />
      </label>
      <p class="hint">The user must choose a new password at first sign-in.</p>
    </template>

    <label>
      Country
      <select v-model="country">
        <option value="">Not set</option>
        <option v-for="nation in nations" :key="nation.value" :value="nation.value">
          {{ nation.label }}
        </option>
      </select>
    </label>

    <label>
      Organization
      <select v-model="organizationId">
        <option value="">No organization</option>
        <option
          v-for="organization in organizations"
          :key="organization.id"
          :value="organization.id"
        >
          {{ organization.name }} ({{ organization.alias }})
        </option>
      </select>
    </label>

    <div class="actions">
      <ButtonLink type="submit" button-style="primary" :disabled="saving">
        {{ user ? 'Save' : 'Create' }}
      </ButtonLink>
      <ButtonLink type="button" button-style="secondary" :disabled="saving" @click="emit('close')">
        Cancel
      </ButtonLink>
    </div>
    <p v-if="errorMessage" role="alert" class="error">{{ errorMessage }}</p>
  </form>
</template>

<style scoped>
.user-edit {
  display: grid;
  gap: var(--spacing-sm);
}

label {
  display: grid;
  gap: var(--spacing-xs);
}

p {
  margin: 0;
}

.actions {
  display: flex;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-sm);
}

.hint {
  opacity: 0.7;
}

.error {
  color: var(--error);
}
</style>
