<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'

import { requestsApi } from '@/api/requests'
import type { ServiceRequest } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Separator } from '@/components/ui/separator'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { labelOf, packageUnits, requestStatuses, tripStatuses } from '@/lib/enums'
import { formatDateTime, formatMoney } from '@/lib/format'

const props = defineProps<{ open: boolean; request: ServiceRequest }>()
const emit = defineEmits<{ 'update:open': [value: boolean] }>()

const { data: packages } = useQuery({
  queryKey: computed(() => ['request-packages', props.request.id]),
  queryFn: () => requestsApi.packages(props.request.id),
  enabled: computed(() => props.open),
})

const { data: trip } = useQuery({
  queryKey: computed(() => ['request-trip', props.request.id]),
  queryFn: () => requestsApi.trip(props.request.id),
  enabled: computed(() => props.open),
})

const effectiveWeight = computed(() =>
  Number(props.request.packageWeight ?? props.request.estimatedWeight ?? 0),
)

function row(label: string, value: string | null | undefined) {
  return { label, value: value ?? '-' }
}
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent class="sm:max-w-3xl">
      <DialogHeader>
        <DialogTitle class="flex items-center gap-3">
          Detalle {{ request.folio }}
          <Badge variant="outline">{{ labelOf(requestStatuses, request.status) }}</Badge>
        </DialogTitle>
      </DialogHeader>

      <div class="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
        <div v-for="item in [
          row('Cliente', request.clientName),
          row('Ruta', request.routeLabel),
          row('Recoleccion', formatDateTime(request.pickupScheduled)),
          row('Entrega', formatDateTime(request.deliveryScheduled)),
          row('Peso efectivo', `${effectiveWeight.toLocaleString('es-MX')} kg`),
          row('Tarifa acordada', request.agreedRate != null ? formatMoney(request.agreedRate) : '-'),
          row('Requiere documentacion', request.requiresDocuments ? 'Si' : 'No'),
          row('Observaciones', request.notes),
        ]" :key="item.label" class="flex justify-between gap-4 border-b py-1 last:border-0">
          <span class="text-muted-foreground">{{ item.label }}</span>
          <span class="text-right font-medium">{{ item.value }}</span>
        </div>
      </div>

      <Separator />

      <div>
        <p class="mb-2 text-sm font-medium">Paquetes</p>
        <div class="rounded-lg border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Descripcion</TableHead>
                <TableHead class="text-right">Cantidad</TableHead>
                <TableHead>Unidad</TableHead>
                <TableHead class="text-right">Peso/u</TableHead>
                <TableHead class="text-right">Recibido</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-if="!packages?.length">
                <TableCell colspan="5" class="py-4 text-center text-xs text-muted-foreground">
                  Sin paquetes
                </TableCell>
              </TableRow>
              <TableRow v-for="line in packages" :key="line.id">
                <TableCell>{{ line.description }}</TableCell>
                <TableCell class="text-right tabular-nums">{{ line.quantity ?? '' }}</TableCell>
                <TableCell>{{ labelOf(packageUnits, line.unit) }}</TableCell>
                <TableCell class="text-right tabular-nums">{{ line.unitWeight ?? '' }}</TableCell>
                <TableCell class="text-right tabular-nums">{{ line.receivedQuantity ?? '' }}</TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </div>
      </div>

      <template v-if="trip">
        <Separator />
        <div>
          <p class="mb-2 text-sm font-medium">
            Viaje
            <Badge variant="outline" class="ml-2">{{ labelOf(tripStatuses, trip.status) }}</Badge>
          </p>
          <div class="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
            <div class="flex justify-between gap-4 border-b py-1">
              <span class="text-muted-foreground">Unidad</span>
              <span class="font-medium">{{ trip.vehicleLabel }}</span>
            </div>
            <div class="flex justify-between gap-4 border-b py-1">
              <span class="text-muted-foreground">Operador</span>
              <span class="font-medium">{{ trip.employeeName }}</span>
            </div>
            <div class="flex justify-between gap-4 border-b py-1">
              <span class="text-muted-foreground">Salida</span>
              <span class="font-medium">{{ formatDateTime(trip.departure) }}</span>
            </div>
            <div class="flex justify-between gap-4 border-b py-1">
              <span class="text-muted-foreground">Llegada</span>
              <span class="font-medium">{{ formatDateTime(trip.arrival) }}</span>
            </div>
            <div class="flex justify-between gap-4 border-b py-1">
              <span class="text-muted-foreground">Km reales</span>
              <span class="font-medium">{{ trip.actualKm ?? '-' }}</span>
            </div>
          </div>
        </div>
      </template>
    </DialogContent>
  </Dialog>
</template>
