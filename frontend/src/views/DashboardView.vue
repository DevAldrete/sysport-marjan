<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { AlertTriangle, ClipboardList, FileWarning, RefreshCw, Wrench } from '@lucide/vue'

import { api } from '@/api/client'
import { problemsOf } from '@/api/errors'
import type { DashboardAlerts } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const { data, isLoading, isError, error, isFetching, refetch } = useQuery({
  queryKey: ['dashboard'],
  queryFn: () => api.get<DashboardAlerts>('/dashboard'),
})

const cards = computed(() => [
  {
    title: 'Licencias por vencer',
    value: data.value?.expiringLicenses,
    icon: AlertTriangle,
  },
  {
    title: 'Facturas vencidas',
    value: data.value?.overdueInvoices,
    icon: FileWarning,
  },
  {
    title: 'Mantenimiento proximo',
    value: data.value?.maintenanceDue,
    icon: Wrench,
  },
  {
    title: 'Solicitudes por asignar',
    value: data.value?.pendingAssignments,
    icon: ClipboardList,
  },
])
</script>

<template>
  <div class="flex flex-col gap-6">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1 class="text-2xl font-semibold tracking-tight">Inicio</h1>
        <p class="text-sm text-muted-foreground">
          Bienvenido, {{ auth.user?.username }} ({{ auth.user?.roleName }})
        </p>
      </div>
      <Button variant="outline" size="sm" :disabled="isFetching" @click="refetch">
        <RefreshCw :class="{ 'animate-spin': isFetching }" />
        Actualizar
      </Button>
    </div>

    <p v-if="isError" class="text-sm text-destructive">
      {{ problemsOf(error).join(' ') }}
    </p>

    <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <Card v-for="card in cards" :key="card.title">
        <CardHeader class="flex flex-row items-center justify-between gap-2 pb-2">
          <CardTitle class="text-sm font-medium text-muted-foreground">{{ card.title }}</CardTitle>
          <component :is="card.icon" class="size-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <p v-if="isLoading" class="text-3xl font-semibold text-muted-foreground">...</p>
          <p v-else class="text-3xl font-semibold tabular-nums">{{ card.value ?? 0 }}</p>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
