import { createApp } from 'vue'
import App from './App.vue'
import { initAuth } from './auth'
import { loadConfig } from './config'
import { router } from './router'
import './styles/main.css'

async function start(): Promise<void> {
  await initAuth(await loadConfig())
  createApp(App).use(router).mount('#app')
}

start().catch((error: unknown) => {
  console.error(error)
  const root = document.getElementById('app')
  if (root) {
    root.innerHTML =
      '<div class="empty"><strong>Request Portal could not start</strong>The sign-in service did not respond. Try again in a moment.</div>'
  }
})
