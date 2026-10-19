import type { components } from '@/schema'

export type FunctionalBuildingBlock = components['schemas']['FunctionalBuildingBlock']

export const functionalBuildingBlockLabels: Record<FunctionalBuildingBlock, string> = {
  ACCESS_TO_METERING_AND_CONSUMPTION_DATA: 'Access to Metering and Consumption Data',
  ACCESS_TO_BASIC_MASTER_DATA: 'Access to Basic Master Data',
  ACCESS_TO_IMPLICIT_FLEXIBILITY_SIGNALS: 'Access to Implicit Flexibility Signals',
  CUSTOMER_SWITCHING: 'Customer Switching',
  COLLECTIVE_SELF_CONSUMPTION_AND_ENERGY_COMMUNITIES:
    'Collective Self-Consumption & Energy Communities',
  FLEXIBLE_CONNECTION_AGREEMENTS: 'Flexible Connection Agreements',
  DEMAND_RESPONSE_PROCEDURES: 'Demand Response Procedures',
  FORECAST_AND_PLANNING_DATA: 'Forecast & Planning Data',
}
