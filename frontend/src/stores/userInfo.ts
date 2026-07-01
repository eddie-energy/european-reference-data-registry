import { ref } from 'vue'

export const userRole = ref<
  'viewer' | 'ceedsParticipant' | 'ceedsEntitiy' | 'dataspaceFacilitator'
>('viewer')
