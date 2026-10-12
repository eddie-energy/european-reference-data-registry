import { listAllOrganizations } from '@/api'
import type { components } from '@/schema'
import { ref } from 'vue'

export const managementOrganizations = ref<components['schemas']['ManagedOrganizationDto'][]>([])

export const updateManagementOrganizations = async () => {
  const { data, error } = await listAllOrganizations()
  if (data) managementOrganizations.value = data
  return { error }
}
