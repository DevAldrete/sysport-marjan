---
title: The web frontend
description: Vue 3, routing by permission, Pinia session, TanStack Query and the PWA.
---

`frontend/` is a Vue 3 single-page app built with Vite and TypeScript, styled with Tailwind and
shadcn-vue (Reka UI). It only displays data and collects input; every rule is enforced by the API.

## Layout

```text
frontend/src
├── api/          typed clients (clients, requests, trips, invoices, reports, users…) + client.ts
├── stores/       Pinia stores (auth)
├── router/       routes built from features.ts + guards
├── features.ts   one entry per screen (path, title, icon, permission, lazy component)
├── layouts/      AppShell (sidebar + topbar + RouterView)
├── views/        one screen per route
├── components/   FormDialog, ConfirmDialog, PageHeader, domain dialogs, ui/ (shadcn-vue)
└── lib/          enums (labels), format (money/date), utils (cn)
```

## Routing and permissions

`features.ts` is the single list that drives **both** the router and the sidebar, so they never
drift apart. Each feature declares the permission required:

```ts
export const features: Feature[] = [
  { path: '', name: 'dashboard', title: 'Inicio', icon: Home, component: () => import('@/views/DashboardView.vue') },
  { path: 'clientes', name: 'clients', title: 'Clientes', icon: Users,
    permission: 'clients.read', component: () => import('@/views/ClientsView.vue') },
  // …
]
```

A router guard sends unauthenticated users to the login (keeping the intended path) and blocks a
route when the user lacks its permission. The sidebar renders only the visible features. The API
enforces the same permissions, so hiding a button is convenience, not security.

## Session

The Pinia `auth` store keeps the **access token in memory** and the **refresh token in
`localStorage`**, so a reload can silently start a new session. The API client attaches the bearer
token and, on a 401, refreshes once and retries; if refresh fails it clears the session and returns
to the login.

## Forms and dialogs

`FormDialog` is the Vue counterpart of the old Swing `FormPanel`: fields are declared as data,
validated with a zod schema, and the server's `problems` are shown as a list while the dialog stays
open. `ConfirmDialog` covers destructive actions. Reusable domain dialogs (assignment, trip detail,
request detail, package editor) compose them.

## Server state

Lists and detail queries use **TanStack Query**: query keys are reactive (search terms, filters)
and mutations invalidate the affected keys. This gives loading/empty states and a single way to
refresh after a write.

## PWA

`vite-plugin-pwa` generates the service worker and manifest. The app shell is precached and API
GETs use a `NetworkFirst` strategy with a short cache, so the app installs and tolerates brief
network blips without going offline-first (no queued writes).

## Motion

Route changes animate with `motion-v`; the app shell wraps `RouterView` in a `motion.div` that
fades and slides content in.
