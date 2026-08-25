import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './assets/main.css'
import { updateReferenceDataObjects } from './stores/referenceDataObject.ts'
import useToast from './composables/useToast'
import { keycloak, login } from './keycloak.ts'

try {
  await login()
  if (keycloak.value?.authenticated) {
    const app = createApp(App)

    const { error } = await updateReferenceDataObjects()

    app.use(router)

    if (error) {
      useToast().danger(error.message ?? 'Failed to load reference data objects')
    }

    app.mount('#app')
  } else {
    globalThis.location.reload()
  }
} catch {
  console.log('Authentication failed')
}
