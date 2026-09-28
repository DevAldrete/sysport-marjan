<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import {
  Ban,
  CalendarClock,
  CircleCheck,
  EllipsisVertical,
  Eye,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  ShieldCheck,
  Trash2,
  Truck,
  X,
} from '@lucide/vue'
import { toast } from 'vue-sonner'
import { z } from 'zod'

import { clientsApi } from '@/api/clients'
import { problemsOf } from '@/api/errors'
import { requestsApi, type RequestQuery } from '@/api/requests'
import { routesApi } from '@/api/routes'
import type { ServiceRequest } from '@/api/types'
import AssignmentDialog from '@/components/AssignmentDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormDialog from '@/components/FormDialog.vue'
import PageHeader from '@/components/PageHeader.vue'
import RequestDetailDialog from '@/components/RequestDetailDialog.vue'
import RequestFormDialog from '@/components/RequestFormDialog.vue'
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
import { labelOf, requestStatuses } from '@/lib/enums'
import { formatDateTime, formatMoney } from '@/lib/format'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const queryClient = useQueryClient()
const canWrite = computed(() => auth.can('requests.write'))
const canAssign = computed(() => auth.can('requests.assign'))

const searchInput = ref('')
const clientFilter = ref('')
const statusFilter = ref('')
const fromFilter = ref('')
const toFilter = ref('')
const filter = ref<RequestQuery>({})

const { data: requests, isFetching, isError, error } = useQuery({
  queryKey: computed(() => ['requests', filter.value]),
  queryFn: () => requestsApi.search(filter.value),
})

function applySearch() {
  filter.value = {
    folio: searchInput.value.trim() || undefined,
    clientId: clientFilter.value ? Number(clientFilter.value) : undefined,
    status: statusFilter.value || undefined,
    from: fromFilter.value || undefined,
    to: toFilter.value || undefined,
  }
}

function clearSearch() {
  searchInput.value = ''
  clientFilter.value = ''
  statusFilter.value = ''
  fromFilter.value = ''
  toFilter.value = ''
  filter.value = {}
}

const { data: clientList } = useQuery({ queryKey: ['clients', 'active'], queryFn: () => clientsApi.active() })
const clientOptions = computed(() =>
  (clientList.value ?? []).map((client) => ({ value: String(client.id), label: client.name })),
)
const { data: routeList } = useQuery({ queryKey: ['routes', 'all'], queryFn: () => routesApi.all() })
const routeOptions = computed(() =>
  (routeList.value ?? []).map((route) => ({
    value: String(route.id),
    label: `${route.origin} -> ${route.destination}`,
  })),
)

function effectiveWeight(request: ServiceRequest): number | null {
  return request.packageWeight ?? request.estimatedWeight
}

const canEdit = (r: ServiceRequest) =>
  canWrite.value && ['requested', 'authorized', 'scheduled', 'assigned'].includes(r.status)
const canAuthorize = (r: ServiceRequest) => canWrite.value && r.status === 'requested'
const canSchedule = (r: ServiceRequest) => canWrite.value && r.status === 'authorized'
const canCancel = (r: ServiceRequest) =>
  canWrite.value && ['requested', 'authorized', 'scheduled', 'assigned'].includes(r.status)
const canClose = (r: ServiceRequest) => canWrite.value && r.status === 'delivered'
const canAssignTrip = (r: ServiceRequest) => canAssign.value && r.status === 'scheduled'

// --- create / edit ----------------------------------------------------------

const formOpen = ref(false)
const formTarget = ref<ServiceRequest | null>(null)

function openCreate() {
  formTarget.value = null
  formOpen.value = true
}

function openEdit(request: ServiceRequest) {
  formTarget.value = request
  formOpen.value = true
}

async function refresh() {
  await queryClient.invalidateQueries({ queryKey: ['requests'] })
}

// --- authorize --------------------------------------------------------------

const actionTarget = ref<ServiceRequest | null>(null)
const authorizeOpen = ref(false)
const authorizeInitial = ref<Record<string, unknown>>({ rate: null })
const actionProblems = ref<string[]>([])
const actionSubmitting = ref(false)

async function openAuthorize(request: ServiceRequest) {
  actionTarget.value = request
  actionProblems.value = []
  let suggested = request.agreedRate
  if (suggested == null) {
    try {
      suggested = await clientsApi.suggestRate(
        request.clientId,
        request.routeId,
        new Date().toISOString().slice(0, 10),
      )
    } catch {
      suggested = null
    }
  }
  authorizeInitial.value = { rate: suggested ?? 0 }
  authorizeOpen.value = true
}

const rateSchema = z.object({ rate: z.coerce.number().min(0.01, 'La tarifa debe ser mayor a cero') })

async function submitAuthorize(values: Record<string, unknown>) {
  if (!actionTarget.value) {
    return
  }
  actionProblems.value = []
  actionSubmitting.value = true
  try {
    await requestsApi.authorize(actionTarget.value.id, Number(values.rate))
    toast.success('Solicitud autorizada')
    authorizeOpen.value = false
    await refresh()
  } catch (failure) {
    actionProblems.value = problemsOf(failure)
  } finally {
    actionSubmitting.value = false
  }
}

// --- schedule ---------------------------------------------------------------

const scheduleOpen = ref(false)
const scheduleInitial = ref<Record<string, unknown>>({})

function openSchedule(request: ServiceRequest) {
  actionTarget.value = request
  actionProblems.value = []
  scheduleInitial.value = {
    pickup: request.pickupScheduled?.slice(0, 16) ?? '',
    delivery: request.deliveryScheduled?.slice(0, 16) ?? '',
  }
  scheduleOpen.value = true
}

const scheduleSchema = z.object({
  pickup: z.string().min(1, 'La recoleccion es obligatoria'),
  delivery: z.string().min(1, 'La entrega es obligatoria'),
})

async function submitSchedule(values: Record<string, unknown>) {
  if (!actionTarget.value) {
    return
  }
  actionProblems.value = []
  actionSubmitting.value = true
  try {
    await requestsApi.schedule(actionTarget.value.id, values.pickup as string, values.delivery as string)
    toast.success('Solicitud programada')
    scheduleOpen.value = false
    await refresh()
  } catch (failure) {
    actionProblems.value = problemsOf(failure)
  } finally {
    actionSubmitting.value = false
  }
}

// --- cancel -----------------------------------------------------------------

const cancelOpen = ref(false)

function openCancel(request: ServiceRequest) {
  actionTarget.value = request
  actionProblems.value = []
  cancelOpen.value = true
}

const cancelSchema = z.object({ reason: z.string().min(1, 'Indique el motivo') })

async function submitCancel(values: Record<string, unknown>) {
  if (!actionTarget.value) {
    return
  }
  actionProblems.value = []
  actionSubmitting.value = true
  try {
    await requestsApi.cancel(actionTarget.value.id, values.reason as string)
    toast.success('Solicitud cancelada')
    cancelOpen.value = false
    await refresh()
  } catch (failure) {
    actionProblems.value = problemsOf(failure)
  } finally {
    actionSubmitting.value = false
  }
}

// --- detail / assignment / close / delete -----------------------------------

const detailOpen = ref(false)
const detailTarget = ref<ServiceRequest | null>(null)

function openDetail(request: ServiceRequest) {
  detailTarget.value = request
  detailOpen.value = true
}

const assignmentOpen = ref(false)
const assignmentTarget = ref<ServiceRequest | null>(null)

function openAssign(request: ServiceRequest) {
  assignmentTarget.value = request
  assignmentOpen.value = true
}

const confirmMode = ref<'close' | 'delete'>('delete')
const confirmOpen = ref(false)

function askClose(request: ServiceRequest) {
  actionTarget.value = request
  confirmMode.value = 'close'
  confirmOpen.value = true
}

function askDelete(request: ServiceRequest) {
  actionTarget.value = request
  confirmMode.value = 'delete'
  confirmOpen.value = true
}

async function confirmAction() {
  if (!actionTarget.value) {
    return
  }
  const request = actionTarget.value
  try {
    if (confirmMode.value === 'close') {
      await requestsApi.close(request.id)
      toast.success('Solicitud cerrada')
    } else {
      await requestsApi.remove(request.id)
      toast.success('Solicitud eliminada')
    }
  } catch (failure) {
    toast.error(problemsOf(failure).join(' '))
  } finally {
    confirmOpen.value = false
    await refresh()
  }
}
</script>

<template>
  <div class="flex flex-col gap-6">
    <PageHeader title="Solicitudes" description="Ciclo de vida de las solicitudes de servicio">
      <Button variant="outline" size="sm" :disabled="isFetching" @click="refresh">
        <RefreshCw :class="{ 'animate-spin': isFetching }" />
        Recargar
      </Button>
      <Button v-if="canWrite" size="sm" @click="openCreate">
        <Plus />
        Nueva
      </Button>
    </PageHeader>

    <form class="flex flex-wrap items-end gap-2" @submit.prevent="applySearch">
      <Input v-model="searchInput" placeholder="Folio" class="w-32" />
      <Select v-model="clientFilter">
        <SelectTrigger class="w-48">
          <SelectValue placeholder="Cliente" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem v-for="option in clientOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </SelectItem>
        </SelectContent>
      </Select>
      <Select v-model="statusFilter">
        <SelectTrigger class="w-40">
          <SelectValue placeholder="Estado" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem v-for="option in requestStatuses" :key="option.value" :value="option.value">
            {{ option.label }}
          </SelectItem>
        </SelectContent>
      </Select>
      <Input v-model="fromFilter" type="date" class="w-40" />
      <Input v-model="toFilter" type="date" class="w-40" />
      <Button type="submit" variant="outline" size="sm">
        <Search />
        Buscar
      </Button>
      <Button type="button" variant="ghost" size="sm" @click="clearSearch">
        <X />
        Limpiar
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
            <TableHead class="text-right">Paquetes</TableHead>
            <TableHead class="text-right">Peso</TableHead>
            <TableHead>Recoleccion</TableHead>
            <TableHead>Entrega</TableHead>
            <TableHead class="text-right">Tarifa</TableHead>
            <TableHead>Estado</TableHead>
            <TableHead class="text-right">Acciones</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-if="!requests?.length">
            <TableCell colspan="10" class="py-8 text-center text-sm text-muted-foreground">
              {{ isFetching ? 'Cargando...' : 'Sin resultados' }}
            </TableCell>
          </TableRow>
          <TableRow v-for="request in requests" :key="request.id" @dblclick="openDetail(request)">
            <TableCell class="font-medium">{{ request.folio }}</TableCell>
            <TableCell>{{ request.clientName }}</TableCell>
            <TableCell class="max-w-xs truncate">{{ request.routeLabel }}</TableCell>
            <TableCell class="text-right tabular-nums">{{ request.packageCount || '' }}</TableCell>
            <TableCell class="text-right tabular-nums">
              {{ effectiveWeight(request) != null ? `${effectiveWeight(request)} kg` : '' }}
            </TableCell>
            <TableCell>{{ formatDateTime(request.pickupScheduled) }}</TableCell>
            <TableCell>{{ formatDateTime(request.deliveryScheduled) }}</TableCell>
            <TableCell class="text-right tabular-nums">
              {{ request.agreedRate != null ? formatMoney(request.agreedRate) : '' }}
            </TableCell>
            <TableCell>
              <Badge variant="outline">{{ labelOf(requestStatuses, request.status) }}</Badge>
            </TableCell>
            <TableCell class="text-right">
              <DropdownMenu>
                <DropdownMenuTrigger as-child>
                  <Button variant="ghost" size="icon-sm">
                    <EllipsisVertical />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" class="w-48">
                  <DropdownMenuItem @select="openDetail(request)">
                    <Eye /> Detalle
                  </DropdownMenuItem>
                  <DropdownMenuItem v-if="canEdit(request)" @select="openEdit(request)">
                    <Pencil /> Editar
                  </DropdownMenuItem>
                  <DropdownMenuItem v-if="canAuthorize(request)" @select="openAuthorize(request)">
                    <ShieldCheck /> Autorizar
                  </DropdownMenuItem>
                  <DropdownMenuItem v-if="canSchedule(request)" @select="openSchedule(request)">
                    <CalendarClock /> Programar
                  </DropdownMenuItem>
                  <DropdownMenuItem v-if="canAssignTrip(request)" @select="openAssign(request)">
                    <Truck /> Asignar viaje
                  </DropdownMenuItem>
                  <DropdownMenuItem v-if="canClose(request)" @select="askClose(request)">
                    <CircleCheck /> Cerrar
                  </DropdownMenuItem>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem v-if="canCancel(request)" @select="openCancel(request)">
                    <Ban /> Cancelar
                  </DropdownMenuItem>
                  <DropdownMenuItem v-if="canWrite" class="text-destructive" @select="askDelete(request)">
                    <Trash2 /> Eliminar
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>

    <RequestFormDialog
      v-model:open="formOpen"
      :request="formTarget"
      :clients="clientOptions"
      :routes="routeOptions"
      @saved="refresh"
    />

    <FormDialog
      v-model:open="authorizeOpen"
      :title="`Autorizar ${actionTarget?.folio ?? ''}`"
      :fields="[{ key: 'rate', label: 'Tarifa acordada', type: 'money', full: true }]"
      :initial="authorizeInitial"
      :schema="rateSchema"
      :problems="actionProblems"
      :submitting="actionSubmitting"
      submit-label="Autorizar"
      @submit="submitAuthorize"
    />

    <FormDialog
      v-model:open="scheduleOpen"
      :title="`Programar ${actionTarget?.folio ?? ''}`"
      :fields="[
        { key: 'pickup', label: 'Recoleccion', type: 'datetime' },
        { key: 'delivery', label: 'Entrega', type: 'datetime' },
      ]"
      :initial="scheduleInitial"
      :schema="scheduleSchema"
      :problems="actionProblems"
      :submitting="actionSubmitting"
      submit-label="Programar"
      @submit="submitSchedule"
    />

    <FormDialog
      v-model:open="cancelOpen"
      :title="`Cancelar ${actionTarget?.folio ?? ''}`"
      :fields="[{ key: 'reason', label: 'Motivo', type: 'textarea' }]"
      :schema="cancelSchema"
      :problems="actionProblems"
      :submitting="actionSubmitting"
      submit-label="Cancelar solicitud"
      @submit="submitCancel"
    />

    <AssignmentDialog
      v-if="assignmentTarget"
      v-model:open="assignmentOpen"
      :request="assignmentTarget"
      @assigned="refresh"
    />

    <RequestDetailDialog
      v-if="detailTarget"
      v-model:open="detailOpen"
      :request="detailTarget"
    />

    <ConfirmDialog
      v-model:open="confirmOpen"
      :title="confirmMode === 'close' ? 'Cerrar solicitud' : 'Eliminar solicitud'"
      :description="
        confirmMode === 'close'
          ? `Se cerrara ${actionTarget?.folio ?? ''}.`
          : `Se eliminara ${actionTarget?.folio ?? ''} con su viaje, gastos y facturas.`
      "
      :confirm-label="confirmMode === 'close' ? 'Cerrar' : 'Eliminar'"
      @confirm="confirmAction"
    />
  </div>
</template>
