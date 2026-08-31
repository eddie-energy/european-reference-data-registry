import { getAllReferenceDataObjects, getReferenceDataObject } from '@/api'
import type { components } from '@/schema'
import { ref } from 'vue'

export const referenceDataObjects = ref<components['schemas']['ReferenceDataObjectDetail'][]>()

export const updateReferenceDataObjects = async () => {
  const { data, error } = await getAllReferenceDataObjects()
  referenceDataObjects.value = data
  return { error }
}

export const referenceDataObject = ref<components['schemas']['ReferenceDataObjectDetail']>()

let requestSeq = 0

export const updateReferenceDataObject = async (
  id: components['parameters']['ReferenceDataObjectId'],
) => {
  const seq = ++requestSeq
  const { data, error } = await getReferenceDataObject(id)
  if (seq !== requestSeq) return { error }
  referenceDataObject.value = data
  return { error }
}
