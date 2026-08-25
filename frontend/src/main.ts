import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './assets/main.css'
import { updateReferenceDataObjects } from './stores/referenceDataObject.ts'
import useToast from './composables/useToast'
import { keycloak, login } from './keycloak.ts'

const showAuthenticationFailed = () => {
  const root = document.querySelector('#app')
  if (!root) {
    return
  }
  root.innerHTML = `
    <div class="auth-failed">
      <h1>Authentication failed</h1>
      <p>Could not sign you in. Check that the identity provider is reachable, then try again.</p>
      <button type="button" id="auth-retry">Retry</button>
    </div>
  `
  document.querySelector('#auth-retry')?.addEventListener('click', () => {
    globalThis.location.reload()
  })
}

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
    showAuthenticationFailed()
  }
} catch (e) {
  console.error('Authentication failed', e)
  showAuthenticationFailed()
}
