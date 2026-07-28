import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './assets/main.css'
import { updateReferenceDataObjects } from './stores/referenceDataObject.ts'
import useToast from './composables/useToast'

const app = createApp(App)

const { error } = await updateReferenceDataObjects()

app.use(router)

app.mount('#app')

if (error) {
  useToast().danger(error.message ?? 'Failed to load reference data objects')
}
