<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { accounts } from './api'
import { useAuth, type Role } from './auth'
import ToastHost from './components/ToastHost.vue'
import { label } from './format'

interface NavItem {
  to: string
  text: string
}

interface NavGroup {
  title: string
  roles: Role[]
  items: NavItem[]
}

const { user, hasRole, logout } = useAuth()
const route = useRoute()
const menuOpen = ref(false)

const groups: NavGroup[] = [
  {
    title: 'My requests',
    roles: ['STUDENT', 'LECTURER'],
    items: [
      { to: '/my-requests', text: 'My requests' },
      { to: '/new', text: 'New request' },
      { to: '/forms', text: 'Forms' },
    ],
  },
  {
    title: 'Department',
    roles: ['STAFF', 'ADMIN'],
    items: [
      { to: '/queue', text: 'Requests' },
      { to: '/statistics', text: 'Statistics' },
    ],
  },
  {
    title: 'Administration',
    roles: ['ADMIN'],
    items: [
      { to: '/admin/templates', text: 'Request templates' },
      { to: '/admin/forms', text: 'Forms' },
      { to: '/admin/accounts', text: 'Accounts' },
      { to: '/admin/departments', text: 'Departments' },
      { to: '/admin/terms', text: 'Academic terms' },
    ],
  },
]

const visibleGroups = computed(() => groups.filter((group) => hasRole(...group.roles)))
const roleText = computed(() =>
  [user.value.roles.map(label).join(', '), user.value.department].filter(Boolean).join(' · '),
)
const initials = computed(() => {
  const words = user.value.name.split(/\s+/).filter(Boolean)
  return [words[0], words.length > 1 ? words.at(-1) : undefined]
    .map((word) => word?.charAt(0) ?? '')
    .join('')
    .toUpperCase()
})

watch(() => route.fullPath, () => (menuOpen.value = false))

// Creates the account in org-service on the first visit, and keeps "last seen" current.
void accounts.me().catch(() => undefined)
</script>

<template>
  <div class="shell">
    <aside class="sidebar" :class="{ open: menuOpen }">
      <div class="brand">
        <RouterLink to="/" class="brand-link">
          <img src="/favicon.svg" alt="" width="26" height="26" />
          <span>Request Portal</span>
        </RouterLink>
        <button class="button ghost small menu-toggle" :aria-expanded="menuOpen" aria-controls="navigation" @click="menuOpen = !menuOpen">
          Menu
        </button>
      </div>
      <nav id="navigation" class="nav">
        <div v-for="group in visibleGroups" :key="group.title" class="nav-group">
          <p class="nav-title">{{ group.title }}</p>
          <RouterLink v-for="item in group.items" :key="item.to" :to="item.to" class="nav-link">{{ item.text }}</RouterLink>
        </div>
      </nav>
      <div class="user">
        <RouterLink to="/profile" class="user-link">
          <span class="avatar" aria-hidden="true">{{ initials }}</span>
          <span class="user-text">
            <strong>{{ user.name }}</strong>
            <span class="muted small">{{ roleText }}</span>
          </span>
        </RouterLink>
        <button class="button ghost small" @click="logout">Sign out</button>
      </div>
    </aside>
    <main class="main">
      <RouterView />
    </main>
    <ToastHost />
  </div>
</template>

<style scoped>
.shell {
  display: grid;
  grid-template-columns: var(--sidebar-width) minmax(0, 1fr);
  min-height: 100vh;
  background: linear-gradient(
    to right,
    var(--surface) calc(var(--sidebar-width) - 1px),
    var(--border) calc(var(--sidebar-width) - 1px) var(--sidebar-width),
    transparent var(--sidebar-width)
  );
}

.sidebar {
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  height: 100vh;
  border-right: 1px solid var(--border);
  background: var(--surface);
}

.brand {
  display: flex;
  align-items: center;
  padding: 16px 16px 12px;
}

.brand-link {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 10px;
  color: var(--text);
  font-size: 15px;
  font-weight: 700;
}

.brand-link:hover {
  text-decoration: none;
}

.menu-toggle {
  display: none;
}

.nav {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 18px;
  padding: 8px 10px;
  overflow-y: auto;
}

.nav-group {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-title {
  padding: 0 10px 4px;
  color: var(--text-faint);
  font-size: 11.5px;
  font-weight: 650;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.nav-link {
  padding: 7px 10px;
  border-radius: var(--radius-small);
  color: var(--text-muted);
  font-weight: 550;
}

.nav-link:hover {
  background: var(--surface-sunken);
  color: var(--text);
  text-decoration: none;
}

.nav-link.router-link-active {
  background: var(--accent-soft);
  color: var(--accent);
}

.user {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px;
  border-top: 1px solid var(--border);
}

.user-link {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px;
  border-radius: var(--radius-small);
  color: var(--text);
}

.user-link:hover {
  background: var(--surface-sunken);
  text-decoration: none;
}

.avatar {
  display: grid;
  flex: none;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 12px;
  font-weight: 700;
}

.user-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.3;
}

.user .button {
  align-self: flex-start;
}

.main {
  width: 100%;
  max-width: 1200px;
  padding: 28px 32px 48px;
}

@media (max-width: 900px) {
  .shell {
    grid-template-columns: 1fr;
    background: none;
  }

  .sidebar {
    position: sticky;
    z-index: 20;
    height: auto;
    border-right: 0;
    border-bottom: 1px solid var(--border);
  }

  .brand {
    padding: 10px 16px;
  }

  .menu-toggle {
    display: inline-flex;
  }

  .nav,
  .user {
    display: none;
  }

  .sidebar.open .nav,
  .sidebar.open .user {
    display: flex;
  }

  .main {
    padding: 20px 16px 40px;
  }
}
</style>
