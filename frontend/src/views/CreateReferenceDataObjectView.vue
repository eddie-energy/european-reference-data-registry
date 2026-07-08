<script lang="ts" setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { createReferenceDataObject } from '@/api'
import { updateReferenceDataObjects } from '@/stores/referenceDataObject'
import ButtonLink from '@/components/ButtonLink.vue'
import useToast from '@/composables/useToast'

const router = useRouter()
const { success } = useToast()

const name = ref('')
const description = ref('')
const errorMessage = ref('')
const submitting = ref(false)

const submit = async () => {
  errorMessage.value = ''
  submitting.value = true

  const { data: object, error: objectError } = await createReferenceDataObject({
    name: name.value,
    description: description.value,
  })

  if (!object) {
    errorMessage.value = objectError?.message ?? 'Failed to create reference data object'
    submitting.value = false
    return
  }

  updateReferenceDataObjects()
  success(`"${object.name}" created`)
  router.push({ name: 'reference-data-object', params: { id: object.id } })
}
</script>

<template>
  <main class="create-reference-data-object">
    <h1>Create Reference Data Object</h1>
    <form class="create-form" @submit.prevent="submit">
      <label>
        Name
        <input v-model="name" type="text" required />
      </label>
      <label>
        Description
        <textarea v-model="description" required></textarea>
      </label>
      <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
      <ButtonLink component="button" buttonStyle="primary" :disabled="submitting">
        Create
      </ButtonLink>
    </form>
  </main>
</template>

<style scoped>
.create-reference-data-object {
  padding: var(--spacing-xxl);
}

.create-form {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
  max-width: 32rem;
}

label {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.error {
  color: var(--error);
  margin: 0;
}
</style>
