<script lang="ts" setup>
import { computed, ref } from 'vue'
import type { components } from '@/schema'
import ButtonLink from './ButtonLink.vue'

const { fields, entry } = defineProps<{
  fields: components['schemas']['FieldDto'][]
  entry?: components['schemas']['EntryDto']
}>()

const emit = defineEmits<{
  submit: [
    payload: {
      nation: components['schemas']['Nation']
      values: components['schemas']['EntryValueDto'][]
    },
  ]
  cancel: []
}>()

const nation = ref<components['schemas']['Nation'] | ''>(entry?.nation ?? '')

const visibleFields = computed(() =>
  fields.filter((field) => !field.nation || field.nation === nation.value),
)

const initial = (field: components['schemas']['FieldDto']) => {
  const value = entry?.values.find((candidate) => candidate.fieldId === field.id)
  switch (field.dataType) {
    case 'NUMBER':
      return value?.numberValue?.toString() ?? ''
    case 'DATE':
      return value?.dateValue ?? ''
    case 'ENUM':
      return value?.enumOptionId ?? ''
    default:
      return value?.textValue ?? ''
  }
}

const drafts = ref<Record<string, string>>(
  Object.fromEntries(fields.map((field) => [field.id, initial(field)])),
)
const errorMessage = ref('')

const toValue = (
  field: components['schemas']['FieldDto'],
): components['schemas']['EntryValueDto'] => {
  const draft = String(drafts.value[field.id] ?? '').trim()
  if (!draft) return { fieldId: field.id }
  switch (field.dataType) {
    case 'NUMBER':
      return { fieldId: field.id, numberValue: Number(draft) }
    case 'DATE':
      return { fieldId: field.id, dateValue: draft }
    case 'ENUM':
      return { fieldId: field.id, enumOptionId: draft }
    default:
      return { fieldId: field.id, textValue: draft }
  }
}

const submit = () => {
  errorMessage.value = ''
  if (!nation.value) {
    errorMessage.value = 'Select a country'
    return
  }
  const invalidNumber = visibleFields.value.find(
    (field) =>
      field.dataType === 'NUMBER' &&
      String(drafts.value[field.id] ?? '').trim() &&
      Number.isNaN(Number(drafts.value[field.id])),
  )
  if (invalidNumber) {
    errorMessage.value = `"${invalidNumber.name}" must be a number`
    return
  }
  emit('submit', { nation: nation.value, values: visibleFields.value.map(toValue) })
}
</script>

<template>
  <form class="entry-form" @submit.prevent="submit">
    <label>
      Country
      <span class="mandatory" title="Mandatory">*</span>
      <select v-model="nation">
        <option value="">—</option>
        <option value="AUT">Austria</option>
        <option value="FRA">France</option>
        <option value="ESP">Spain</option>
        <option value="GER">Germany</option>
      </select>
    </label>
    <label v-for="field in visibleFields" :key="field.id">
      {{ field.name }}
      <span v-if="field.mandatory" class="mandatory" title="Mandatory">*</span>
      <select v-if="field.dataType === 'ENUM'" v-model="drafts[field.id]">
        <option value="">—</option>
        <option v-for="option in field.options" :key="option.id" :value="option.id">
          {{ option.name }}
        </option>
      </select>
      <input
        v-else
        v-model="drafts[field.id]"
        :type="field.dataType === 'NUMBER' ? 'number' : field.dataType === 'DATE' ? 'date' : 'text'"
        :step="field.dataType === 'NUMBER' ? 'any' : undefined"
      />
    </label>
    <p v-if="!fields.length" class="hint">This version has no fields yet.</p>
    <p v-else-if="nation && !visibleFields.length" class="hint">No fields for this country yet.</p>
    <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
    <div class="actions">
      <ButtonLink
        component="button"
        type="button"
        buttonStyle="tertiary"
        size="compact"
        @click="emit('cancel')"
      >
        Cancel
      </ButtonLink>
      <ButtonLink
        component="button"
        buttonStyle="secondary"
        size="compact"
        :disabled="!fields.length"
      >
        Save entry
      </ButtonLink>
    </div>
  </form>
</template>

<style scoped>
.entry-form {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

label {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  font-size: 0.9rem;
}

.mandatory {
  color: var(--error);
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-md);
  margin-top: var(--spacing-md);
}

.error {
  color: var(--error);
  margin: 0;
}

.hint {
  margin: 0;
  opacity: 0.7;
}
</style>
