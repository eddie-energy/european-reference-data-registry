<script lang="ts" setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { createReferenceDataObject } from '@/api'
import { updateReferenceDataObjects } from '@/stores/referenceDataObject'
import ButtonLink from '@/components/ButtonLink.vue'
import useToast from '@/composables/useToast'
import CrossIcon from '@/assets/icons/CrossIcon.svg'

const router = useRouter()
const { success } = useToast()

const name = ref('')
const description = ref('')
const errorMessage = ref('')
const submitting = ref(false)

const submit = async () => {
  errorMessage.value = ''
  submitting.value = true

  const { data: referenceDataObject, error: referenceDataObjectError } =
    await createReferenceDataObject({
      name: name.value,
      description: description.value,
    })

  if (!referenceDataObject) {
    errorMessage.value =
      referenceDataObjectError?.message ?? 'Failed to create reference data object'
    submitting.value = false
    return
  }

  updateReferenceDataObjects()
  success(`"${referenceDataObject.name}" created`)
  router.push({
    name: 'reference-data-object',
    params: { id: referenceDataObject.id },
    query: { tab: 'edit' },
  })
}
</script>

<template>
  <main class="create-reference-data-object">
    <p class="breadcrumb">
      <RouterLink to="/">S3 Reference Data Registry</RouterLink> → Create Reference Data Object
    </p>
    <div class="section-heading">
      <span class="badge badge-teal">New Reference Data Object</span>
      <h1>Create Reference Data Object</h1>
    </div>
    <form class="create-form" @submit.prevent="submit">
      <label>
        Name
        <input v-model="name" type="text" required />
      </label>
      <label>
        Description
        <textarea v-model="description" required></textarea>
      </label>
      <p v-if="errorMessage" class="error-banner">
        <CrossIcon class="error-icon" />
        {{ errorMessage }}
      </p>
      <div class="actions">
        <ButtonLink component="RouterLink" to="/" buttonStyle="tertiary">Cancel</ButtonLink>
        <ButtonLink component="button" buttonStyle="primary" :disabled="submitting">
          {{ submitting ? 'Creating…' : 'Create' }}
        </ButtonLink>
      </div>
    </form>
  </main>
</template>

<style scoped>
.create-reference-data-object {
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

.section-heading {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  margin-block: var(--spacing-lg) var(--spacing-xxl);
}

.section-heading h1 {
  margin: 0;
}

.create-form {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
  max-width: 32rem;
  padding: var(--spacing-xxl);
  background: var(--light);
  border-radius: var(--default-border-radius);
  box-shadow: var(--card-shadow);
}

label {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.error-banner {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin: 0;
  padding: var(--spacing-sm) var(--spacing-md);
  border-radius: var(--spacing-sm);
  background-color: color-mix(in srgb, var(--error) 10%, white);
  color: color-mix(in srgb, var(--error) 85%, black);
  font-size: 0.9rem;
}

.error-icon {
  flex-shrink: 0;
  color: var(--error);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--spacing-md);
}
</style>
