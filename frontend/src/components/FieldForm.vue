<script lang="ts" setup>
import { ref } from 'vue'
import { createField } from '@/api'
import type { components } from '@/schema'
import ButtonLink from './ButtonLink.vue'
import useToast from '@/composables/useToast'

const { id, versionId } = defineProps<{
  id: components['parameters']['ReferenceDataObjectId']
  versionId: components['parameters']['VersionId']
}>()

const { success } = useToast()

const emit = defineEmits<{
  created: [field: components['schemas']['FieldDto']]
}>()

const name = ref('')
const dataType = ref<components['schemas']['DataType']>('TEXT')
const mandatory = ref(false)
const nation = ref<components['schemas']['Nation'] | ''>('')
const errorMessage = ref('')

const submit = async () => {
  errorMessage.value = ''
  const { data, error } = await createField(id, versionId, {
    name: name.value,
    dataType: dataType.value,
    mandatory: mandatory.value,
    ...(nation.value && { nation: nation.value }),
  })

  if (!data) {
    errorMessage.value = error?.message ?? 'Failed to add field'
    return
  }

  emit('created', data)
  success(`Field "${data.name}" added`)
  name.value = ''
  dataType.value = 'TEXT'
  mandatory.value = false
  nation.value = ''
}
</script>

<template>
  <form class="field-form" @submit.prevent="submit">
    <label>
      Field name
      <input v-model="name" type="text" required />
    </label>
    <label>
      Data type
      <select v-model="dataType">
        <option value="TEXT">Text</option>
        <option value="NUMBER">Number</option>
        <option value="DATE">Date</option>
      </select>
    </label>
    <label>
      Nation
      <select v-model="nation">
        <option value="">—</option>
        <option value="AUT">Austria</option>
        <option value="FRA">France</option>
        <option value="ESP">Spain</option>
        <option value="GER">Germany</option>
      </select>
    </label>
    <label class="checkbox">
      <input v-model="mandatory" type="checkbox" />
      Mandatory
    </label>
    <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
    <ButtonLink component="button" buttonStyle="secondary" size="compact">Add field</ButtonLink>
  </form>
</template>

<style scoped>
.field-form {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: var(--spacing-md);
  margin-block: var(--spacing-md);
}

label {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  font-size: 0.9rem;
}

.checkbox {
  flex-direction: row;
  align-items: center;
}

.error {
  color: var(--error);
  margin: 0;
  flex-basis: 100%;
}
</style>
