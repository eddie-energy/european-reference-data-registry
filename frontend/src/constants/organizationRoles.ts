import type { components } from '@/schema'

export const organizationRoleLabels: Record<components['schemas']['OrganizationRole'], string> = {
  NDSF: 'NDSF',
  COORDINATING_NDSF: 'Coordinating NDSF',
  OPERATIONAL_ENTITY: 'Operational Entity',
}
