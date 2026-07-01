import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './assets/main.css'
import { updateReferenceDataObjects } from './stores/referenceDataObject.ts'

const app = createApp(App)

await updateReferenceDataObjects()

app.use(router)

app.mount('#app')
