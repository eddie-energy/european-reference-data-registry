import { getAllReferenceDataObjects, getReferenceDataObject } from '@/api'
import type { components } from '@/schema'
import { ref } from 'vue'

export const referenceDataObjects = ref<components['schemas']['ReferenceDataObjectDetail'][]>()

export const updateReferenceDataObjects = async () => {
  referenceDataObjects.value = (await getAllReferenceDataObjects()).data
}

export const referenceDataObject = ref<components['schemas']['ReferenceDataObjectDetail']>()

export const updateReferenceDataObject = async (
  id: components['parameters']['ReferenceDataObjectId'],
) => {
  referenceDataObject.value = (await getReferenceDataObject(id)).data
}
