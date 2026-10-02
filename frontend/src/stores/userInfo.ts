import { ref } from 'vue'
import type { components } from '@/schema'
import { getCurrentUser, getMyResponsibilities } from '@/api'
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
export const responsibilityNations = ref<Record<string, Nation[]>>({})

export const maintainableNations = (objectId: string): Nation[] =>
  responsibilityNations.value[objectId] ?? []

export const updateUserInfo = async (): Promise<{ error?: string }> => {
  if (!isAuthenticated()) {
    userRole.value = 'viewer'
    username.value = ''
    ndsfNations.value = []
    organizations.value = []
    responsibilityNations.value = {}
    return {}
  }

  const { data, error } = await getCurrentUser().catch((e: unknown) => ({
    data: undefined,
    error: { message: e instanceof Error ? e.message : String(e) },
  }))
  if (!data) {
    userRole.value = 'viewer'
    responsibilityNations.value = {}
    return { error: error?.message ?? 'Failed to load your user information' }
  }

  username.value = data.username
  ndsfNations.value = data.ndsfNations
  organizations.value = data.organizations
  userRole.value = data.roles
    .map((role) => ROLE_BY_API_VALUE[role])
    .reduce((highest, role) => (ROLE_RANK[role] > ROLE_RANK[highest] ? role : highest), 'viewer')
  const { data: responsibilities, error: responsibilityError } =
    await getMyResponsibilities().catch((e: unknown) => ({
      data: undefined,
      error: { message: e instanceof Error ? e.message : String(e) },
    }))
  responsibilityNations.value = Object.fromEntries(
    (responsibilities ?? []).map((item) => [item.referenceDataObjectId, item.nations]),
  )
  if (!responsibilities) {
    return { error: responsibilityError?.message ?? 'Failed to load your responsibilities' }
  }
  return {}
}
