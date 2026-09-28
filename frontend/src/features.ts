import type { Component } from 'vue'
import {
  BarChart3,
  ClipboardList,
  Fuel,
  Home,
  MapPin,
  Navigation,
  Receipt,
  ShieldCheck,
  Truck,
  UserCog,
  Users,
} from '@lucide/vue'

import ComingSoonView from '@/views/ComingSoonView.vue'
import DashboardView from '@/views/DashboardView.vue'

/**
 * One entry per screen: the router builds its routes from this list and the
 * sidebar builds its menu from it too, so they never drift apart. A feature
 * without permission is visible to everyone authenticated (the dashboard).
 */
export interface Feature {
  path: string
  name: string
  title: string
  icon: Component
  component: Component
  permission?: string
}

export const features: Feature[] = [
  { path: '', name: 'dashboard', title: 'Inicio', icon: Home, component: DashboardView },
  {
    path: 'clientes',
    name: 'clients',
    title: 'Clientes',
    icon: Users,
    permission: 'clients.read',
    component: ComingSoonView,
  },
  {
    path: 'rutas',
    name: 'routes',
    title: 'Rutas',
    icon: MapPin,
    permission: 'routes.read',
    component: ComingSoonView,
  },
  {
    path: 'operadores',
    name: 'operators',
    title: 'Operadores',
    icon: UserCog,
    permission: 'operators.read',
    component: ComingSoonView,
  },
  {
    path: 'unidades',
    name: 'vehicles',
    title: 'Unidades',
    icon: Truck,
    permission: 'fleet.read',
    component: ComingSoonView,
  },
  {
    path: 'combustible',
    name: 'fuel',
    title: 'Combustible',
    icon: Fuel,
    permission: 'fuel.read',
    component: ComingSoonView,
  },
  {
    path: 'solicitudes',
    name: 'requests',
    title: 'Solicitudes',
    icon: ClipboardList,
    permission: 'requests.read',
    component: ComingSoonView,
  },
  {
    path: 'viajes',
    name: 'trips',
    title: 'Viajes',
    icon: Navigation,
    permission: 'trips.read',
    component: ComingSoonView,
  },
  {
    path: 'facturas',
    name: 'invoices',
    title: 'Facturas',
    icon: Receipt,
    permission: 'invoices.read',
    component: ComingSoonView,
  },
  {
    path: 'reportes',
    name: 'reports',
    title: 'Reportes',
    icon: BarChart3,
    permission: 'reports.view',
    component: ComingSoonView,
  },
  {
    path: 'usuarios',
    name: 'users',
    title: 'Usuarios',
    icon: ShieldCheck,
    permission: 'security.users',
    component: ComingSoonView,
  },
]
