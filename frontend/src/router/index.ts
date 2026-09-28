import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import { features } from '@/features'
import AppShell from '@/layouts/AppShell.vue'
import LoginView from '@/views/LoginView.vue'
import NotFoundView from '@/views/NotFoundView.vue'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    permission?: string
    title?: string
  }
}

const children: RouteRecordRaw[] = features.map((feature) => ({
  path: feature.path,
  name: feature.name,
  component: feature.component,
  meta: { title: feature.title, permission: feature.permission },
}))

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { public: true, title: 'Acceso' },
    },
    {
      path: '/',
      component: AppShell,
      children: [
        ...children,
        {
          path: ':pathMatch(.*)*',
          name: 'not-found',
          component: NotFoundView,
          meta: { title: 'No encontrado' },
        },
      ],
    },
  ],
})

// Guard: unauthenticated users go to login; permission-gated screens are only
// reachable with the matching read permission (the backend enforces it too).
router.beforeEach((to) => {
  const auth = useAuthStore()

  if (to.meta.public) {
    return auth.isAuthenticated ? { name: 'dashboard' } : true
  }
  if (!auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.permission && !auth.can(to.meta.permission)) {
    return { name: 'dashboard' }
  }
  return true
})
