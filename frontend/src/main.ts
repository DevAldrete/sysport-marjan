import { VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia } from 'pinia'
import { createApp } from 'vue'

import App from './App.vue'
import { onUnauthorized } from './api/client'
import { router } from './router'
import { useAuthStore } from './stores/auth'
import './assets/main.css'

const app = createApp(App)
app.use(createPinia())

onUnauthorized(() => {
  void router.push({ name: 'login' })
})

// Restore a session from the stored refresh token before the first navigation.
const auth = useAuthStore()
await auth.bootstrap()

app.use(router)
app.use(VueQueryPlugin)
app.mount('#app')
