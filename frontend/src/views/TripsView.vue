<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import {
  Ban,
  EllipsisVertical,
  Eye,
  Flag,
  RefreshCw,
  Search,
  Trash2,
  UserCog,
} from '@lucide/vue'
import { toast } from 'vue-sonner'
import { z } from 'zod'

import { problemsOf } from '@/api/errors'
import { tripsApi } from '@/api/trips'
import type { Trip } from '@/api/types'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormDialog from '@/components/FormDialog.vue'
import PageHeader from '@/components/PageHeader.vue'
import TripDetailDialog from '@/components/TripDetailDialog.vue'
import TripReassignDialog from '@/components/TripReassignDialog.vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { labelOf, tripStatuses } from '@/lib/enums'
import { formatDateTime } from '@/lib/format'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const queryClient = useQueryClient()
const canWrite = computed(() => auth.can('trips.write'))
const canAssign = computed(() => auth.can('trips.assign'))

const searchInput = ref('')
const statusFilter = ref('')
const term = ref('')

const { data: trips, isFetching, isError, error } = useQuery({
  queryKey: computed(() => ['trips', term.value, statusFilter.value]),
  queryFn: () => tripsApi.search(term.value, statusFilter.value),
})

function applySearch() {
  term.value = searchInput.value.trim()
}

async function refresh() {
  await queryClient.invalidateQueries({ queryKey: ['trips'] })
}

const detailOpen = ref(false)
const detailTarget = ref<Trip | null>(null)

function openDetail(trip: Trip) {
  detailTarget.value = trip
  detailOpen.value = true
}

const confirmOpen = ref(false)
const confirmMode = ref<'depart' | 'cancel' | 'delete'>('depart')
const actionTarget = ref<Trip | null>(null)
const actionProblems = ref<string[]>([])
const actionSubmitting = ref(false)

function askDepart(trip: Trip) {
  actionTarget.value = trip
  confirmMode.value = 'depart'
  confirmOpen.value = true
}

function askCancel(trip: Trip) {
  actionTarget.value = trip
  actionProblems.value = []
  confirmMode.value = 'cancel'
  cancelOpen.value = true
}

function askDelete(trip: Trip) {
  actionTarget.value = trip
  confirmMode.value = 'delete'
  confirmOpen.value = true
}

async function confirmAction() {
  if (!actionTarget.value) {
    return
  }
  const trip = actionTarget.value
  try {
    if (confirmMode.value === 'depart') {
      await tripsApi.depart(trip.id)
      toast.success('Salida registrada')
    } else {
      await tripsApi.remove(trip.id)
      toast.success('Viaje eliminado')
    }
  } catch (failure) {
    toast.error(problemsOf(failure).join(' '))
  } finally {
    confirmOpen.value = false
    await refresh()
  }
}

// --- arrival ----------------------------------------------------------------

const arriveOpen = ref(false)
const arriveInitial = ref<Record<string, unknown>>({})

function openArrive(trip: Trip) {
  actionTarget.value = trip
  actionProblems.value = []
  arriveInitial.value = { actualKm: trip.estimatedKm ?? null }
  arriveOpen.value = true
}

const arriveSchema = z.object({ actualKm: z.coerce.number().min(0, 'Indique los kilometros') })

async function submitArrive(values: Record<string, unknown>) {
  if (!actionTarget.value) {
    return
  }
  actionProblems.value = []
  actionSubmitting.value = true
  try {
    await tripsApi.arrive(actionTarget.value.id, Number(values.actualKm))
    toast.success('Llegada registrada')
    arriveOpen.value = false
    await refresh()
  } catch (failure) {
    actionProblems.value = problemsOf(failure)
  } finally {
    actionSubmitting.value = false
  }
}

// --- cancel -----------------------------------------------------------------

const cancelOpen = ref(false)
const cancelSchema = z.object({ reason: z.string().min(1, 'Indique el motivo') })

async function submitCancel(values: Record<string, unknown>) {
  if (!actionTarget.value) {
    return
  }
  actionProblems.value = []
  actionSubmitting.value = true
  try {
    await tripsApi.cancel(actionTarget.value.id, values.reason as string)
    toast.success('Viaje cancelado')
    cancelOpen.value = false
    await refresh()
  } catch (failure) {
    actionProblems.value = problemsOf(failure)
  } finally {
    actionSubmitting.value = false
  }
}

// --- reassign ---------------------------------------------------------------

const reassignOpen = ref(false)
const reassignTarget = ref<Trip | null>(null)

function openReassign(trip: Trip) {
  reassignTarget.value = trip
  reassignOpen.value = true
}
</script>

<template>
  <div class="flex flex-col gap-6">
    <PageHeader title="Viajes" description="Ejecucion de los viajes asignados">
      <Button variant="outline" size="sm" :disabled="isFetching" @click="refresh">
        <RefreshCw :class="{ 'animate-spin': isFetching }" />
        Recargar
      </Button>
    </PageHeader>

    <form class="flex flex-wrap items-center gap-2" @submit.prevent="applySearch">
      <Input v-model="searchInput" placeholder="Buscar por folio o unidad" class="max-w-xs" />
      <Select v-model="statusFilter">
        <SelectTrigger class="w-40"><SelectValue placeholder="Estado" /></SelectTrigger>
        <SelectContent>
          <SelectItem v-for="option in tripStatuses" :key="option.value" :value="option.value">
            {{ option.label }}
          </SelectItem>
        </SelectContent>
      </Select>
      <Button type="submit" variant="outline" size="sm">
        <Search />
        Buscar
      </Button>
    </form>

    <p v-if="isError" class="text-sm text-destructive">{{ problemsOf(error).join(' ') }}</p>

    <div class="rounded-lg border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Folio</TableHead>
            <TableHead>Cliente</TableHead>
            <TableHead>Ruta</TableHead>
            <TableHead>Unidad</TableHead>
            <TableHead>Operador</TableHead>
            <TableHead>Inicio</TableHead>
            <TableHead>Fin</TableHead>
            <TableHead>Estado</TableHead>
            <TableHead class="text-right">Acciones</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-if="!trips?.length">
            <TableCell colspan="9" class="py-8 text-center text-sm text-muted-foreground">
              {{ isFetching ? 'Cargando...' : 'Sin resultados' }}
            </TableCell>
          </TableRow>
          <TableRow v-for="trip in trips" :key="trip.id" @dblclick="openDetail(trip)">
            <TableCell class="font-medium">{{ trip.folio }}</TableCell>
            <TableCell>{{ trip.clientName }}</TableCell>
            <TableCell class="max-w-xs truncate">{{ trip.routeLabel }}</TableCell>
            <TableCell>{{ trip.vehicleLabel }}</TableCell>
            <TableCell>{{ trip.employeeName }}</TableCell>
            <TableCell>{{ formatDateTime(trip.departure ?? trip.plannedStart) }}</TableCell>
            <TableCell>{{ formatDateTime(trip.arrival ?? trip.plannedEnd) }}</TableCell>
            <TableCell><Badge variant="outline">{{ labelOf(tripStatuses, trip.status) }}</Badge></TableCell>
            <TableCell class="text-right">
              <DropdownMenu>
                <DropdownMenuTrigger as-child>
                  <Button variant="ghost" size="icon-sm"><EllipsisVertical /></Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" class="w-48">
                  <DropdownMenuItem @select="openDetail(trip)"><Eye /> Detalle</DropdownMenuItem>
                  <DropdownMenuItem v-if="canWrite && trip.status === 'scheduled'" @select="askDepart(trip)">
                    <Flag /> Salida
                  </DropdownMenuItem>
                  <DropdownMenuItem v-if="canWrite && trip.status === 'in_transit'" @select="openArrive(trip)">
                    <Flag /> Llegada
                  </DropdownMenuItem>
                  <DropdownMenuItem v-if="canAssign && trip.status === 'scheduled'" @select="openReassign(trip)">
                    <UserCog /> Reasignar
                  </DropdownMenuItem>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem v-if="canWrite && trip.status === 'scheduled'" @select="askCancel(trip)">
                    <Ban /> Cancelar
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    v-if="canWrite && (trip.status === 'scheduled' || trip.status === 'cancelled')"
                    class="text-destructive"
                    @select="askDelete(trip)"
                  >
                    <Trash2 /> Eliminar
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>

    <TripDetailDialog v-if="detailTarget" v-model:open="detailOpen" :trip="detailTarget" @changed="refresh" />
    <TripReassignDialog v-if="reassignTarget" v-model:open="reassignOpen" :trip="reassignTarget" @reassigned="refresh" />

    <FormDialog
      v-model:open="arriveOpen"
      :title="`Llegada ${actionTarget?.folio ?? ''}`"
      :fields="[{ key: 'actualKm', label: 'Kilometros reales', type: 'number', full: true }]"
      :initial="arriveInitial"
      :schema="arriveSchema"
      :problems="actionProblems"
      :submitting="actionSubmitting"
      submit-label="Registrar llegada"
      @submit="submitArrive"
    />

    <FormDialog
      v-model:open="cancelOpen"
      :title="`Cancelar ${actionTarget?.folio ?? ''}`"
      :fields="[{ key: 'reason', label: 'Motivo', type: 'textarea' }]"
      :schema="cancelSchema"
      :problems="actionProblems"
      :submitting="actionSubmitting"
      submit-label="Cancelar viaje"
      @submit="submitCancel"
    />

    <ConfirmDialog
      v-model:open="confirmOpen"
      :title="confirmMode === 'depart' ? 'Registrar salida' : 'Eliminar viaje'"
      :description="confirmMode === 'depart' ? `Se registrara la salida de ${actionTarget?.folio ?? ''}.` : `Se eliminara ${actionTarget?.folio ?? ''} con sus costos.`"
      :confirm-label="confirmMode === 'depart' ? 'Salida' : 'Eliminar'"
      @confirm="confirmAction"
    />
  </div>
</template>
