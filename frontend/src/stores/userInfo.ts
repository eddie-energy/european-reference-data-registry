import { ref } from 'vue'

export const userRole = ref<'viewer' | 'ceedsParticipant' | 'ceedsEntity' | 'dataspaceFacilitator'>(
  'viewer',
)
