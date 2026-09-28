<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { Info, Plus, Trash2 } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { z } from 'zod'

import { problemsOf } from '@/api/errors'
import { requestsApi } from '@/api/requests'
import type { Trip } from '@/api/types'
import { advancesApi, deliveriesApi, expensesApi, incidentsApi, tripFuelApi } from '@/api/trips'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormDialog, { type FormField } from '@/components/FormDialog.vue'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  advanceStatuses,
  deliveryStatuses,
  expenseTypes,
  incidentTypes,
  labelOf,
  packageConditions,
  tripStatuses,
} from '@/lib/enums'
import { formatDate, formatDateTime, formatMoney } from '@/lib/format'
import { useAuthStore } from '@/stores/auth'

const props = defineProps<{ open: boolean; trip: Trip }>()
const emit = defineEmits<{ 'update:open': [value: boolean]; changed: [] }>()

const auth = useAuthStore()
const queryClient = useQueryClient()
const canExpenses = computed(() => auth.can('expenses.write'))
const canAdvances = computed(() => auth.can('advances.write'))
const canIncidents = computed(() => auth.can('incidents.write'))
const canDeliveries = computed(() => auth.can('deliveries.write'))

const enabled = computed(() => props.open)

const { data: expenses } = useQuery({
  queryKey: computed(() => ['trip-expenses', props.trip.id]),
  queryFn: () => expensesApi.list(props.trip.id),
  enabled,
})
const { data: fuel } = useQuery({
  queryKey: computed(() => ['trip-fuel', props.trip.id]),
  queryFn: () => tripFuelApi.list(props.trip.id),
  enabled,
})
const { data: advances } = useQuery({
  queryKey: computed(() => ['trip-advances', props.trip.id]),
  queryFn: () => advancesApi.list(props.trip.id),
  enabled,
})
const { data: balance } = useQuery({
  queryKey: computed(() => ['trip-balance', props.trip.id]),
  queryFn: () => advancesApi.balance(props.trip.id),
  enabled,
})
const { data: incidents } = useQuery({
  queryKey: computed(() => ['trip-incidents', props.trip.id]),
  queryFn: () => incidentsApi.list(props.trip.id),
  enabled,
})
const { data: packages } = useQuery({
  queryKey: computed(() => ['request-packages', props.trip.serviceRequestId]),
  queryFn: () => requestsApi.packages(props.trip.serviceRequestId),
  enabled,
})
const { data: delivery } = useQuery({
  queryKey: computed(() => ['trip-delivery', props.trip.id]),
  queryFn: () => deliveriesApi.byTrip(props.trip.id),
  enabled,
})

const expensesTotal = computed(() => (expenses.value ?? []).reduce((sum, e) => sum + Number(e.amount), 0))
const fuelTotal = computed(() => (fuel.value ?? []).reduce((sum, f) => sum + Number(f.amount), 0))
const totalCost = computed(() => expensesTotal.value + fuelTotal.value)

function invalidateAll() {
  for (const key of ['trip-expenses', 'trip-fuel', 'trip-advances', 'trip-balance', 'trip-incidents', 'trip-delivery']) {
    void queryClient.invalidateQueries({ queryKey: [key, props.trip.id] })
  }
  emit('changed')
}

// --- expense form -----------------------------------------------------------

const expenseOpen = ref(false)
const expenseProblems = ref<string[]>([])
const expenseSubmitting = ref(false)
const expenseFields: FormField[] = [
  { key: 'type', label: 'Tipo', type: 'select', options: expenseTypes },
  { key: 'amount', label: 'Importe', type: 'money' },
  { key: 'expenseDate', label: 'Fecha', type: 'date' },
  { key: 'description', label: 'Descripcion', type: 'textarea' },
]
const expenseSchema = z.object({
  type: z.string().default('other'),
  amount: z.coerce.number().min(0.01, 'El importe debe ser mayor a cero'),
  expenseDate: z.string().min(1, 'La fecha es obligatoria'),
  description: z.string().default(''),
})

async function saveExpense(values: Record<string, unknown>) {
  expenseProblems.value = []
  expenseSubmitting.value = true
  try {
    await expensesApi.create({
      tripId: props.trip.id,
      type: values.type as string,
      amount: Number(values.amount),
      expenseDate: values.expenseDate as string,
      description: (values.description as string) ?? '',
    })
    toast.success('Gasto registrado')
    expenseOpen.value = false
    invalidateAll()
  } catch (failure) {
    expenseProblems.value = problemsOf(failure)
  } finally {
    expenseSubmitting.value = false
  }
}

// --- advance form -----------------------------------------------------------

const advanceOpen = ref(false)
const advanceProblems = ref<string[]>([])
const advanceSubmitting = ref(false)
const advanceFields: FormField[] = [
  { key: 'amountGiven', label: 'Monto entregado', type: 'money' },
  { key: 'deliveredDate', label: 'Fecha de entrega', type: 'date' },
]
const advanceSchema = z.object({
  amountGiven: z.coerce.number().min(0.01, 'El monto debe ser mayor a cero'),
  deliveredDate: z.string().min(1, 'La fecha es obligatoria'),
})

async function saveAdvance(values: Record<string, unknown>) {
  advanceProblems.value = []
  advanceSubmitting.value = true
  try {
    await advancesApi.create({
      tripId: props.trip.id,
      employeeId: props.trip.employeeId,
      amountGiven: Number(values.amountGiven),
      deliveredDate: values.deliveredDate as string,
    })
    toast.success('Anticipo registrado')
    advanceOpen.value = false
    invalidateAll()
  } catch (failure) {
    advanceProblems.value = problemsOf(failure)
  } finally {
    advanceSubmitting.value = false
  }
}

// --- incident form ----------------------------------------------------------

const incidentOpen = ref(false)
const incidentProblems = ref<string[]>([])
const incidentSubmitting = ref(false)
const incidentFields: FormField[] = [
  { key: 'incidentDate', label: 'Fecha', type: 'date' },
  { key: 'incidentTime', label: 'Hora (HH:mm)', type: 'text' },
  { key: 'type', label: 'Tipo', type: 'select', options: incidentTypes },
  { key: 'location', label: 'Ubicacion' },
  { key: 'description', label: 'Descripcion', type: 'textarea' },
  { key: 'actionsTaken', label: 'Acciones realizadas', type: 'textarea' },
]
const incidentSchema = z.object({
  incidentDate: z.string().min(1, 'La fecha es obligatoria'),
  incidentTime: z.string().default(''),
  type: z.string().default('other'),
  location: z.string().default(''),
  description: z.string().min(1, 'Describa la incidencia'),
  actionsTaken: z.string().default(''),
})

async function saveIncident(values: Record<string, unknown>) {
  incidentProblems.value = []
  incidentSubmitting.value = true
  try {
    await incidentsApi.create({
      tripId: props.trip.id,
      incidentDate: values.incidentDate as string,
      incidentTime: (values.incidentTime as string) || null,
      location: (values.location as string) ?? '',
      type: values.type as string,
      description: values.description as string,
      actionsTaken: (values.actionsTaken as string) ?? '',
    })
    toast.success('Incidencia registrada')
    incidentOpen.value = false
    invalidateAll()
  } catch (failure) {
    incidentProblems.value = problemsOf(failure)
  } finally {
    incidentSubmitting.value = false
  }
}

// --- delivery form ----------------------------------------------------------

const deliveryOpen = ref(false)
const deliveryProblems = ref<string[]>([])
const deliverySubmitting = ref(false)
const deliveryFields: FormField[] = [
  { key: 'actualDatetime', label: 'Fecha y hora', type: 'datetime' },
  { key: 'receivedBy', label: 'Recibio' },
  { key: 'evidenceReference', label: 'Referencia de evidencia' },
  { key: 'status', label: 'Estado', type: 'select', options: deliveryStatuses },
]
const deliverySchema = z.object({
  actualDatetime: z.string().min(1, 'La fecha es obligatoria'),
  receivedBy: z.string().default(''),
  evidenceReference: z.string().default(''),
  status: z.string().default('pending_documents'),
})

function openDelivery() {
  deliveryProblems.value = []
  deliveryOpen.value = true
}

async function saveDelivery(values: Record<string, unknown>) {
  deliveryProblems.value = []
  deliverySubmitting.value = true
  try {
    await deliveriesApi.save({
      tripId: props.trip.id,
      actualDatetime: values.actualDatetime as string,
      receivedBy: (values.receivedBy as string) ?? '',
      evidenceReference: (values.evidenceReference as string) ?? '',
      status: values.status as string,
    })
    toast.success('Entrega registrada')
    deliveryOpen.value = false
    invalidateAll()
  } catch (failure) {
    deliveryProblems.value = problemsOf(failure)
  } finally {
    deliverySubmitting.value = false
  }
}

const deliveryInitial = computed(() => ({
  actualDatetime: delivery.value?.actualDatetime?.slice(0, 16) ?? '',
  receivedBy: delivery.value?.receivedBy ?? '',
  evidenceReference: delivery.value?.evidenceReference ?? '',
  status: delivery.value?.status ?? 'pending_documents',
}))

// --- receipts ---------------------------------------------------------------

const receiptDraft = ref<Record<number, { receivedQuantity: number | undefined; receiptCondition: string }>>({})
const receiptsSubmitting = ref(false)

watch(packages, (lines) => {
  if (!lines) {
    return
  }
  const next: Record<number, { receivedQuantity: number | undefined; receiptCondition: string }> = {}
  for (const line of lines) {
    next[line.id] = {
      receivedQuantity: line.receivedQuantity ?? undefined,
      receiptCondition: line.receiptCondition ?? 'ok',
    }
  }
  receiptDraft.value = next
})

function receiveAll() {
  for (const line of packages.value ?? []) {
    receiptDraft.value[line.id] = {
      receivedQuantity: line.quantity ?? undefined,
      receiptCondition: 'ok',
    }
  }
}

async function saveReceipts() {
  receiptsSubmitting.value = true
  try {
    const lines = Object.entries(receiptDraft.value).map(([id, value]) => ({
      id: Number(id),
      receivedQuantity: value.receivedQuantity ?? null,
      receiptCondition: value.receiptCondition || null,
    }))
    await requestsApi.saveReceipts(props.trip.serviceRequestId, lines)
    toast.success('Recepcion guardada')
    await queryClient.invalidateQueries({ queryKey: ['request-packages', props.trip.serviceRequestId] })
  } catch (failure) {
    toast.error(problemsOf(failure).join(' '))
  } finally {
    receiptsSubmitting.value = false
  }
}

// --- confirmations ----------------------------------------------------------

const confirmOpen = ref(false)
const confirmAction = ref<() => Promise<void>>(async () => {})

function ask(action: () => Promise<void>) {
  confirmAction.value = action
  confirmOpen.value = true
}

async function runConfirmed() {
  try {
    await confirmAction.value()
  } catch (failure) {
    toast.error(problemsOf(failure).join(' '))
  } finally {
    confirmOpen.value = false
  }
}

function removeExpense(id: number) {
  return ask(async () => {
    await expensesApi.remove(id)
    toast.success('Gasto eliminado')
    invalidateAll()
  })
}
function settleAdvance(id: number) {
  return ask(async () => {
    await advancesApi.settle(id)
    toast.success('Anticipo comprobado')
    invalidateAll()
  })
}
function removeAdvance(id: number) {
  return ask(async () => {
    await advancesApi.remove(id)
    toast.success('Anticipo eliminado')
    invalidateAll()
  })
}
function removeIncident(id: number) {
  return ask(async () => {
    await incidentsApi.remove(id)
    toast.success('Incidencia eliminada')
    invalidateAll()
  })
}
function removeDelivery() {
  const current = delivery.value
  if (!current) {
    return
  }
  return ask(async () => {
    await deliveriesApi.remove(current.id)
    toast.success('Entrega eliminada')
    invalidateAll()
  })
}
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent class="sm:max-w-4xl">
      <DialogHeader>
        <DialogTitle>Viaje {{ trip.folio }} - {{ trip.vehicleLabel }}</DialogTitle>
      </DialogHeader>

      <Tabs default-value="summary">
        <TabsList class="flex-wrap">
          <TabsTrigger value="summary">Resumen</TabsTrigger>
          <TabsTrigger value="expenses">Gastos</TabsTrigger>
          <TabsTrigger value="fuel">Combustible</TabsTrigger>
          <TabsTrigger value="advances">Anticipos</TabsTrigger>
          <TabsTrigger value="incidents">Incidencias</TabsTrigger>
          <TabsTrigger value="packages">Paquetes</TabsTrigger>
          <TabsTrigger value="delivery">Entrega</TabsTrigger>
        </TabsList>

        <TabsContent value="summary" class="pt-4">
          <div class="grid gap-6 sm:grid-cols-2">
            <div class="flex flex-col gap-1 text-sm">
              <p class="font-medium">Viaje</p>
              <div class="flex justify-between border-b py-1"><span class="text-muted-foreground">Unidad</span><span>{{ trip.vehicleLabel }}</span></div>
              <div class="flex justify-between border-b py-1"><span class="text-muted-foreground">Operador</span><span>{{ trip.employeeName }}</span></div>
              <div class="flex justify-between border-b py-1"><span class="text-muted-foreground">Ruta</span><span>{{ trip.routeLabel }}</span></div>
              <div class="flex justify-between border-b py-1"><span class="text-muted-foreground">Estado</span><span>{{ labelOf(tripStatuses, trip.status) }}</span></div>
              <div class="flex justify-between border-b py-1"><span class="text-muted-foreground">Salida</span><span>{{ formatDateTime(trip.departure) || '-' }}</span></div>
              <div class="flex justify-between border-b py-1"><span class="text-muted-foreground">Llegada</span><span>{{ formatDateTime(trip.arrival) || '-' }}</span></div>
            </div>
            <div class="flex flex-col gap-1 text-sm">
              <p class="font-medium">Costos</p>
              <div class="flex justify-between border-b py-1"><span class="text-muted-foreground">Gastos</span><span class="tabular-nums">{{ formatMoney(expensesTotal) }}</span></div>
              <div class="flex justify-between border-b py-1"><span class="text-muted-foreground">Combustible</span><span class="tabular-nums">{{ formatMoney(fuelTotal) }}</span></div>
              <div class="flex justify-between border-b py-1 font-medium"><span>Total</span><span class="tabular-nums">{{ formatMoney(totalCost) }}</span></div>
              <div v-if="balance" class="flex items-start justify-between gap-4 border-b py-1">
                <span class="text-muted-foreground">Anticipo</span>
                <span class="text-right">{{ balance.label }}</span>
              </div>
            </div>
          </div>
        </TabsContent>

        <TabsContent value="expenses" class="pt-4">
          <div class="mb-2 flex justify-end">
            <Button v-if="canExpenses" size="sm" @click="((expenseProblems = []), (expenseOpen = true))">
              <Plus /> Nuevo gasto
            </Button>
          </div>
          <div class="rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Fecha</TableHead><TableHead>Tipo</TableHead>
                  <TableHead class="text-right">Importe</TableHead><TableHead>Descripcion</TableHead>
                  <TableHead class="text-right">Acciones</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                <TableRow v-if="!expenses?.length"><TableCell colspan="5" class="py-6 text-center text-sm text-muted-foreground">Sin gastos</TableCell></TableRow>
                <TableRow v-for="expense in expenses" :key="expense.id">
                  <TableCell>{{ formatDate(expense.expenseDate) }}</TableCell>
                  <TableCell>{{ labelOf(expenseTypes, expense.type) }}</TableCell>
                  <TableCell class="text-right tabular-nums">{{ formatMoney(expense.amount) }}</TableCell>
                  <TableCell class="max-w-xs truncate">{{ expense.description }}</TableCell>
                  <TableCell class="text-right"><Button v-if="canExpenses" variant="ghost" size="icon-sm" @click="removeExpense(expense.id)"><Trash2 class="text-destructive" /></Button></TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </div>
        </TabsContent>

        <TabsContent value="fuel" class="pt-4">
          <div class="rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Fecha</TableHead><TableHead>Estacion</TableHead>
                  <TableHead class="text-right">Litros</TableHead><TableHead class="text-right">Precio/L</TableHead>
                  <TableHead class="text-right">Importe</TableHead><TableHead class="text-right">Odometro</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                <TableRow v-if="!fuel?.length"><TableCell colspan="6" class="py-6 text-center text-sm text-muted-foreground">Sin cargas</TableCell></TableRow>
                <TableRow v-for="load in fuel" :key="load.id">
                  <TableCell>{{ formatDateTime(load.loadDate) }}</TableCell>
                  <TableCell>{{ load.fuelStation }}</TableCell>
                  <TableCell class="text-right tabular-nums">{{ load.liters }}</TableCell>
                  <TableCell class="text-right tabular-nums">{{ formatMoney(load.pricePerLiter) }}</TableCell>
                  <TableCell class="text-right tabular-nums">{{ formatMoney(load.amount) }}</TableCell>
                  <TableCell class="text-right tabular-nums">{{ load.odometerReading }}</TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </div>
        </TabsContent>

        <TabsContent value="advances" class="pt-4">
          <div class="mb-2 flex items-center justify-between">
            <p v-if="balance" class="text-sm text-muted-foreground">
              Entregado {{ formatMoney(balance.given) }} / Comprobado {{ formatMoney(balance.proven) }}
            </p>
            <Button v-if="canAdvances" size="sm" @click="((advanceProblems = []), (advanceOpen = true))">
              <Plus /> Registrar anticipo
            </Button>
          </div>
          <div class="rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Operador</TableHead><TableHead class="text-right">Monto</TableHead>
                  <TableHead>Fecha</TableHead><TableHead>Estado</TableHead>
                  <TableHead class="text-right">Acciones</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                <TableRow v-if="!advances?.length"><TableCell colspan="5" class="py-6 text-center text-sm text-muted-foreground">Sin anticipos</TableCell></TableRow>
                <TableRow v-for="advance in advances" :key="advance.id">
                  <TableCell>{{ advance.employeeName }}</TableCell>
                  <TableCell class="text-right tabular-nums">{{ formatMoney(advance.amountGiven) }}</TableCell>
                  <TableCell>{{ formatDate(advance.deliveredDate) }}</TableCell>
                  <TableCell>{{ labelOf(advanceStatuses, advance.status) }}</TableCell>
                  <TableCell class="text-right">
                    <div class="flex justify-end gap-1">
                      <Button v-if="canAdvances && advance.status === 'pending'" variant="outline" size="xs" @click="settleAdvance(advance.id)">Comprobar</Button>
                      <Button v-if="canAdvances" variant="ghost" size="icon-sm" @click="removeAdvance(advance.id)"><Trash2 class="text-destructive" /></Button>
                    </div>
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </div>
        </TabsContent>

        <TabsContent value="incidents" class="pt-4">
          <div class="mb-2 flex justify-end">
            <Button v-if="canIncidents" size="sm" @click="((incidentProblems = []), (incidentOpen = true))">
              <Plus /> Nueva incidencia
            </Button>
          </div>
          <div class="rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Fecha</TableHead><TableHead>Tipo</TableHead><TableHead>Ubicacion</TableHead>
                  <TableHead>Descripcion</TableHead><TableHead>Acciones</TableHead>
                  <TableHead class="text-right" />
                </TableRow>
              </TableHeader>
              <TableBody>
                <TableRow v-if="!incidents?.length"><TableCell colspan="6" class="py-6 text-center text-sm text-muted-foreground">Sin incidencias</TableCell></TableRow>
                <TableRow v-for="incident in incidents" :key="incident.id">
                  <TableCell>{{ formatDate(incident.incidentDate) }}</TableCell>
                  <TableCell>{{ labelOf(incidentTypes, incident.type) }}</TableCell>
                  <TableCell class="max-w-[10rem] truncate">{{ incident.location }}</TableCell>
                  <TableCell class="max-w-xs truncate">{{ incident.description }}</TableCell>
                  <TableCell class="max-w-xs truncate">{{ incident.actionsTaken }}</TableCell>
                  <TableCell class="text-right"><Button v-if="canIncidents" variant="ghost" size="icon-sm" @click="removeIncident(incident.id)"><Trash2 class="text-destructive" /></Button></TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </div>
        </TabsContent>

        <TabsContent value="packages" class="pt-4">
          <div class="mb-2 flex justify-end gap-2">
            <Button v-if="canDeliveries" variant="outline" size="sm" @click="receiveAll">Recibir todo</Button>
            <Button v-if="canDeliveries" size="sm" :disabled="receiptsSubmitting" @click="saveReceipts">
              Guardar recepcion
            </Button>
          </div>
          <div class="rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Descripcion</TableHead><TableHead class="text-right">Cantidad</TableHead>
                  <TableHead>Unidad</TableHead><TableHead class="text-right">Recibido</TableHead>
                  <TableHead>Condicion</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                <TableRow v-if="!packages?.length"><TableCell colspan="5" class="py-6 text-center text-sm text-muted-foreground">Sin paquetes</TableCell></TableRow>
                <TableRow v-for="line in packages" :key="line.id">
                  <TableCell>{{ line.description }}</TableCell>
                  <TableCell class="text-right tabular-nums">{{ line.quantity ?? '' }}</TableCell>
                  <TableCell>{{ line.unit }}</TableCell>
                  <TableCell class="text-right">
                    <Input v-model="receiptDraft[line.id].receivedQuantity" type="number" step="any" class="h-7 w-24 text-right" :disabled="!canDeliveries" />
                  </TableCell>
                  <TableCell>
                    <Select v-model="receiptDraft[line.id].receiptCondition" :disabled="!canDeliveries">
                      <SelectTrigger class="h-7 w-40"><SelectValue /></SelectTrigger>
                      <SelectContent>
                        <SelectItem v-for="condition in packageConditions" :key="condition.value" :value="condition.value">{{ condition.label }}</SelectItem>
                      </SelectContent>
                    </Select>
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </div>
        </TabsContent>

        <TabsContent value="delivery" class="pt-4">
          <div class="mb-2 flex justify-end gap-2">
            <Button v-if="canDeliveries" size="sm" @click="openDelivery">
              {{ delivery ? 'Actualizar entrega' : 'Registrar entrega' }}
            </Button>
            <Button v-if="canDeliveries && delivery" variant="outline" size="sm" @click="removeDelivery">Eliminar</Button>
          </div>
          <div v-if="delivery" class="grid gap-1 text-sm sm:grid-cols-2">
            <div class="flex justify-between border-b py-1"><span class="text-muted-foreground">Fecha</span><span>{{ formatDateTime(delivery.actualDatetime) }}</span></div>
            <div class="flex justify-between border-b py-1"><span class="text-muted-foreground">Recibio</span><span>{{ delivery.receivedBy }}</span></div>
            <div class="flex justify-between border-b py-1"><span class="text-muted-foreground">Evidencia</span><span>{{ delivery.evidenceReference }}</span></div>
            <div class="flex justify-between border-b py-1"><span class="text-muted-foreground">Estado</span><span>{{ labelOf(deliveryStatuses, delivery.status) }}</span></div>
          </div>
          <p v-else class="flex items-center gap-2 py-6 text-sm text-muted-foreground">
            <Info class="size-4" /> Sin entrega registrada
          </p>
        </TabsContent>
      </Tabs>
    </DialogContent>
  </Dialog>

  <FormDialog v-model:open="expenseOpen" title="Nuevo gasto" :fields="expenseFields" :schema="expenseSchema" :problems="expenseProblems" :submitting="expenseSubmitting" @submit="saveExpense" />
  <FormDialog v-model:open="advanceOpen" title="Registrar anticipo" :fields="advanceFields" :schema="advanceSchema" :problems="advanceProblems" :submitting="advanceSubmitting" @submit="saveAdvance" />
  <FormDialog v-model:open="incidentOpen" title="Nueva incidencia" :fields="incidentFields" :schema="incidentSchema" :problems="incidentProblems" :submitting="incidentSubmitting" @submit="saveIncident" />
  <FormDialog v-model:open="deliveryOpen" :title="delivery ? 'Actualizar entrega' : 'Registrar entrega'" :fields="deliveryFields" :initial="deliveryInitial" :schema="deliverySchema" :problems="deliveryProblems" :submitting="deliverySubmitting" @submit="saveDelivery" />

  <ConfirmDialog v-model:open="confirmOpen" title="Confirmar" description="Esta seguro de continuar?" confirm-label="Confirmar" @confirm="runConfirmed" />
</template>
