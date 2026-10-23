import { createRouter, createWebHistory, type RouteLocationRaw, type RouteRecordRaw } from 'vue-router'
import { useAuth, type Role } from './auth'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    /** Roles allowed to open the page; any signed-in user when absent. */
    roles?: Role[]
  }
}

const REQUESTER: Role[] = ['STUDENT', 'LECTURER']
const STAFF: Role[] = ['STAFF', 'ADMIN']
const ADMIN: Role[] = ['ADMIN']

export const routes: RouteRecordRaw[] = [
  { path: '/', name: 'home', redirect: () => homeFor() },
  {
    path: '/my-requests',
    name: 'my-requests',
    component: () => import('./views/requester/MyRequestsView.vue'),
    meta: { title: 'My requests', roles: REQUESTER },
  },
  {
    path: '/new',
    name: 'new-request',
    component: () => import('./views/requester/NewRequestView.vue'),
    meta: { title: 'New request', roles: REQUESTER },
  },
  {
    path: '/new/:templateId',
    name: 'submit-request',
    component: () => import('./views/requester/SubmitRequestView.vue'),
    props: (route) => ({ templateId: Number(route.params.templateId) }),
    meta: { title: 'New request', roles: REQUESTER },
  },
  {
    path: '/requests/:id',
    name: 'request',
    component: () => import('./views/RequestDetailView.vue'),
    props: (route) => ({ id: Number(route.params.id) }),
    meta: { title: 'Request' },
  },
  {
    path: '/forms',
    name: 'forms',
    component: () => import('./views/requester/FormsView.vue'),
    meta: { title: 'Forms' },
  },
  {
    path: '/forms/:id',
    name: 'fill-form',
    component: () => import('./views/requester/FillFormView.vue'),
    props: (route) => ({ id: Number(route.params.id) }),
    meta: { title: 'Fill in a form' },
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('./views/ProfileView.vue'),
    meta: { title: 'Profile' },
  },
  {
    path: '/queue',
    name: 'queue',
    component: () => import('./views/staff/QueueView.vue'),
    meta: { title: 'Requests', roles: STAFF },
  },
  {
    path: '/statistics',
    name: 'statistics',
    component: () => import('./views/staff/StatisticsView.vue'),
    meta: { title: 'Statistics', roles: STAFF },
  },
  {
    path: '/admin/templates',
    name: 'templates',
    component: () => import('./views/admin/TemplatesView.vue'),
    meta: { title: 'Request templates', roles: ADMIN },
  },
  {
    path: '/admin/templates/:id',
    name: 'template',
    component: () => import('./views/admin/TemplateEditorView.vue'),
    props: (route) => ({ id: route.params.id === 'new' ? null : Number(route.params.id) }),
    meta: { title: 'Request template', roles: ADMIN },
  },
  {
    path: '/admin/forms',
    name: 'admin-forms',
    component: () => import('./views/admin/FormsAdminView.vue'),
    meta: { title: 'Forms', roles: ADMIN },
  },
  {
    path: '/admin/forms/:id',
    name: 'admin-form',
    component: () => import('./views/admin/FormEditorView.vue'),
    props: (route) => ({ id: route.params.id === 'new' ? null : Number(route.params.id) }),
    meta: { title: 'Form', roles: ADMIN },
  },
  {
    path: '/admin/accounts',
    name: 'accounts',
    component: () => import('./views/admin/AccountsView.vue'),
    meta: { title: 'Accounts', roles: ADMIN },
  },
  {
    path: '/admin/departments',
    name: 'departments',
    component: () => import('./views/admin/DepartmentsView.vue'),
    meta: { title: 'Departments', roles: ADMIN },
  },
  {
    path: '/admin/terms',
    name: 'terms',
    component: () => import('./views/admin/TermsView.vue'),
    meta: { title: 'Academic terms', roles: ADMIN },
  },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('./views/NotFoundView.vue') },
]

/** Staff and admins start at the request queue, requesters at their own requests. */
export function homeFor(): RouteLocationRaw {
  const { hasRole } = useAuth()
  if (hasRole(...STAFF)) {
    return { name: 'queue' }
  }
  return hasRole(...REQUESTER) ? { name: 'my-requests' } : { name: 'profile' }
}

export const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => {
  const roles = to.meta.roles
  if (roles && !useAuth().hasRole(...roles)) {
    return homeFor()
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · Request Portal` : 'Request Portal'
})
