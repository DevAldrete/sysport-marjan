import type { Component } from 'vue'
import type { RouteComponent } from 'vue-router'
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

/**
 * One entry per screen: the router builds its routes from this list and the
 * sidebar builds its menu from it too, so they never drift apart. A feature
 * without permission is visible to everyone authenticated (the dashboard).
 * Screens are lazy so each becomes its own chunk.
 */
export interface Feature {
  path: string
  name: string
  title: string
  icon: Component
  component: RouteComponent
  permission?: string
}

export const features: Feature[] = [
  {
    path: '',
    name: 'dashboard',
    title: 'Inicio',
    icon: Home,
    component: () => import('@/views/DashboardView.vue'),
  },
  {
    path: 'clientes',
    name: 'clients',
    title: 'Clientes',
    icon: Users,
    permission: 'clients.read',
    component: () => import('@/views/ClientsView.vue'),
  },
  {
    path: 'rutas',
    name: 'routes',
    title: 'Rutas',
    icon: MapPin,
    permission: 'routes.read',
    component: () => import('@/views/RoutesView.vue'),
  },
  {
    path: 'operadores',
    name: 'operators',
    title: 'Operadores',
    icon: UserCog,
    permission: 'operators.read',
    component: () => import('@/views/OperatorsView.vue'),
  },
  {
    path: 'unidades',
    name: 'vehicles',
    title: 'Unidades',
    icon: Truck,
    permission: 'fleet.read',
    component: () => import('@/views/VehiclesView.vue'),
  },
  {
    path: 'combustible',
    name: 'fuel',
    title: 'Combustible',
    icon: Fuel,
    permission: 'fuel.read',
    component: () => import('@/views/FuelView.vue'),
  },
  {
    path: 'solicitudes',
    name: 'requests',
    title: 'Solicitudes',
    icon: ClipboardList,
    permission: 'requests.read',
    component: () => import('@/views/RequestsView.vue'),
  },
  {
    path: 'viajes',
    name: 'trips',
    title: 'Viajes',
    icon: Navigation,
    permission: 'trips.read',
    component: () => import('@/views/TripsView.vue'),
  },
  {
    path: 'facturas',
    name: 'invoices',
    title: 'Facturas',
    icon: Receipt,
    permission: 'invoices.read',
    component: () => import('@/views/InvoicesView.vue'),
  },
  {
    path: 'reportes',
    name: 'reports',
    title: 'Reportes',
    icon: BarChart3,
    permission: 'reports.view',
    component: () => import('@/views/ReportsView.vue'),
  },
  {
    path: 'usuarios',
    name: 'users',
    title: 'Usuarios',
    icon: ShieldCheck,
    permission: 'security.users',
    component: () => import('@/views/UsersView.vue'),
  },
]
