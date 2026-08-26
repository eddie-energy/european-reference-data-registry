import { ref } from 'vue'
import type { components } from '@/schema'
import { getCurrentUser } from '@/api'
import { isAuthenticated } from '@/keycloak'

export type UserRole = 'viewer' | 'participant' | 'ndsf' | 'operationalEntity'

type Nation = components['schemas']['Nation']

const ROLE_BY_API_VALUE: Record<components['schemas']['Role'], UserRole> = {
  VIEWER: 'viewer',
  PARTICIPANT: 'participant',
  NDSF: 'ndsf',
  OPERATIONAL_ENTITY: 'operationalEntity',
}

const ROLE_RANK: Record<UserRole, number> = {
  viewer: 0,
  participant: 1,
  ndsf: 2,
  operationalEntity: 3,
}

export const userRole = ref<UserRole>('viewer')
export const username = ref('')
export const ndsfNations = ref<Nation[]>([])
export const organizations = ref<string[]>([])

export const updateUserInfo = async () => {
  if (!isAuthenticated()) {
    userRole.value = 'viewer'
    username.value = ''
    ndsfNations.value = []
    organizations.value = []
    return
  }

  const { data } = await getCurrentUser()
  if (!data) {
    userRole.value = 'viewer'
    return
  }

  username.value = data.username
  ndsfNations.value = data.ndsfNations
  organizations.value = data.organizations
  userRole.value = data.roles
    .map((role) => ROLE_BY_API_VALUE[role])
    .reduce((highest, role) => (ROLE_RANK[role] > ROLE_RANK[highest] ? role : highest), 'viewer')
}
