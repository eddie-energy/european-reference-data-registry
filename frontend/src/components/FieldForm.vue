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
const options = ref<string[]>([])
const optionDraft = ref('')
const errorMessage = ref('')

const addOption = () => {
  const option = optionDraft.value.trim()
  if (!option) return
  if (options.value.includes(option)) {
    errorMessage.value = `Option "${option}" already exists`
    return
  }
  errorMessage.value = ''
  options.value.push(option)
  optionDraft.value = ''
}

const removeOption = (option: string) => {
  options.value = options.value.filter((existing) => existing !== option)
}

const submit = async () => {
  errorMessage.value = ''
  if (dataType.value === 'ENUM' && options.value.length === 0) {
    errorMessage.value = 'Enum fields require at least one option'
    return
  }

  const { data, error } = await createField(id, versionId, {
    name: name.value,
    dataType: dataType.value,
    mandatory: mandatory.value,
    ...(nation.value && { nation: nation.value }),
    ...(dataType.value === 'ENUM' && { options: options.value }),
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
  options.value = []
  optionDraft.value = ''
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
        <option value="ENUM">Enum</option>
      </select>
    </label>
    <div v-if="dataType === 'ENUM'" class="options">
      <span class="option-input">
        <label>
          Options
          <input v-model="optionDraft" type="text" @keydown.enter.prevent="addOption" />
        </label>
        <ButtonLink
          component="button"
          type="button"
          buttonStyle="secondary"
          size="compact"
          @click="addOption"
        >
          Add option
        </ButtonLink>
      </span>
      <ul v-if="options.length" class="option-list">
        <li v-for="option in options" :key="option">
          {{ option }}
          <ButtonLink
            component="button"
            type="button"
            buttonStyle="error-secondary"
            size="compact"
            @click="removeOption(option)"
          >
            Remove
          </ButtonLink>
        </li>
      </ul>
    </div>
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

.options {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  flex-basis: 100%;
}

.option-input {
  display: flex;
  align-items: end;
  gap: var(--spacing-md);
}

.option-list {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--spacing-md);
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: 0.9rem;
}

.option-list li {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.error {
  color: var(--error);
  margin: 0;
  flex-basis: 100%;
}
</style>
