import { listManagementUsers } from '@/api'
import type { components } from '@/schema'
import { ref } from 'vue'

export const managementUsers = ref<components['schemas']['ManagementUserDto'][]>([])

export const updateManagementUsers = async () => {
  const { data, error } = await listManagementUsers()
  if (data) managementUsers.value = data
  return { error }
}
