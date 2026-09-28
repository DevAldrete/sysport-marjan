<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { Pencil, Plus, RefreshCw, Search, Trash2, Truck, Wrench } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { z } from 'zod'

import { problemsOf } from '@/api/errors'
import type { Maintenance, Vehicle } from '@/api/types'
import { vehiclesApi } from '@/api/vehicles'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormDialog, { type FormField } from '@/components/FormDialog.vue'
import PageHeader from '@/components/PageHeader.vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { labelOf, maintenanceTypes, vehicleStatuses } from '@/lib/enums'
import { formatDate, formatMoney } from '@/lib/format'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const queryClient = useQueryClient()
const canWrite = computed(() => auth.can('fleet.write'))
const canMaintenance = computed(() => auth.can('fleet.maintenance'))

const searchInput = ref('')
const term = ref('')

const { data: vehicles, isFetching, isError, error } = useQuery({
  queryKey: computed(() => ['vehicles', term.value]),
  queryFn: () => vehiclesApi.search(term.value),
})

function applySearch() {
  term.value = searchInput.value.trim()
}

const fields: FormField[] = [
  { key: 'internalCode', label: 'No. economico' },
  { key: 'plates', label: 'Placas' },
  { key: 'brand', label: 'Marca' },
  { key: 'model', label: 'Modelo' },
  { key: 'year', label: 'Anio', type: 'number' },
  { key: 'serialNumber', label: 'No. de serie' },
  { key: 'vehicleType', label: 'Tipo de unidad' },
  { key: 'loadCapacity', label: 'Capacidad de carga (kg)', type: 'number' },
  { key: 'mileage', label: 'Kilometraje', type: 'number' },
]

const schema = z.object({
  internalCode: z.string().min(1, 'El numero economico es obligatorio'),
  plates: z.string().min(1, 'Las placas son obligatorias'),
  brand: z.string().default(''),
  model: z.string().default(''),
  year: z.coerce.number().int().min(1900).max(2100).optional(),
  serialNumber: z.string().default(''),
  vehicleType: z.string().default(''),
  loadCapacity: z.coerce.number().min(0).default(0),
  mileage: z.coerce.number().min(0).default(0),
})

const formOpen = ref(false)
const editing = ref<Vehicle | null>(null)
const formProblems = ref<string[]>([])
const submitting = ref(false)

const formInitial = computed(() =>
  editing.value
    ? { ...editing.value }
    : { year: null, loadCapacity: 0, mileage: 0 },
)

function openCreate() {
  editing.value = null
  formProblems.value = []
  formOpen.value = true
}

function openEdit(vehicle: Vehicle) {
  editing.value = vehicle
  formProblems.value = []
  formOpen.value = true
}

async function save(values: Record<string, unknown>) {
  formProblems.value = []
  submitting.value = true
  const payload = { ...values, status: editing.value?.status ?? 'available' }
  try {
    if (editing.value) {
      await vehiclesApi.update(editing.value.id, payload)
    } else {
      await vehiclesApi.create(payload)
    }
    toast.success('Unidad guardada')
    formOpen.value = false
    await queryClient.invalidateQueries({ queryKey: ['vehicles'] })
  } catch (failure) {
    formProblems.value = problemsOf(failure)
  } finally {
    submitting.value = false
  }
}

const statusTarget = ref<Vehicle | null>(null)
const statusOpen = ref(false)
const statusProblems = ref<string[]>([])
const statusSubmitting = ref(false)

const statusFields: FormField[] = [
  { key: 'status', label: 'Estado', type: 'select', options: vehicleStatuses, full: true },
]

async function saveStatus(values: Record<string, unknown>) {
  if (!statusTarget.value) {
    return
  }
  statusProblems.value = []
  statusSubmitting.value = true
  try {
    await vehiclesApi.setStatus(statusTarget.value.id, values.status as string)
    toast.success('Estado actualizado')
    statusOpen.value = false
    await queryClient.invalidateQueries({ queryKey: ['vehicles'] })
  } catch (failure) {
    statusProblems.value = problemsOf(failure)
  } finally {
    statusSubmitting.value = false
  }
}

const deleteTarget = ref<Vehicle | null>(null)
const confirmOpen = ref(false)

async function confirmDelete() {
  if (!deleteTarget.value) {
    return
  }
  try {
    await vehiclesApi.remove(deleteTarget.value.id)
    toast.success('Unidad eliminada')
  } catch (failure) {
    toast.error(problemsOf(failure).join(' '))
  } finally {
    confirmOpen.value = false
    await queryClient.invalidateQueries({ queryKey: ['vehicles'] })
  }
}

// --- maintenance ------------------------------------------------------------

const maintenanceVehicle = ref<Vehicle | null>(null)
const maintenanceOpen = ref(false)

const { data: maintenance } = useQuery({
  queryKey: computed(() => ['maintenance', maintenanceVehicle.value?.id]),
  queryFn: () => vehiclesApi.maintenance(maintenanceVehicle.value!.id),
  enabled: computed(() => maintenanceOpen.value && maintenanceVehicle.value !== null),
})

function openMaintenance(vehicle: Vehicle) {
  maintenanceVehicle.value = vehicle
  maintenanceOpen.value = true
}

const maintenanceFields: FormField[] = [
  { key: 'maintenanceDate', label: 'Fecha', type: 'date' },
  { key: 'odometerReading', label: 'Odometro', type: 'number' },
  { key: 'type', label: 'Tipo', type: 'select', options: maintenanceTypes },
  { key: 'provider', label: 'Proveedor / taller' },
  { key: 'workPerformed', label: 'Trabajos', type: 'textarea' },
  { key: 'cost', label: 'Costo', type: 'money' },
  { key: 'nextServiceDate', label: 'Proxima fecha', type: 'date' },
  { key: 'nextServiceKm', label: 'Proximo km', type: 'number' },
]

const maintenanceSchema = z.object({
  maintenanceDate: z.string().min(1, 'La fecha es obligatoria'),
  odometerReading: z.coerce.number().min(0).default(0),
  type: z.string().default('preventive'),
  provider: z.string().default(''),
  workPerformed: z.string().min(1, 'Describa los trabajos'),
  cost: z.coerce.number().min(0).default(0),
  nextServiceDate: z.string().default(''),
  nextServiceKm: z.coerce.number().min(0).optional(),
})

const maintenanceFormOpen = ref(false)
const maintenanceProblems = ref<string[]>([])
const maintenanceSubmitting = ref(false)

async function saveMaintenance(values: Record<string, unknown>) {
  if (!maintenanceVehicle.value) {
    return
  }
  maintenanceProblems.value = []
  maintenanceSubmitting.value = true
  const payload = {
    ...values,
    nextServiceDate: (values.nextServiceDate as string) || null,
  }
  try {
    await vehiclesApi.addMaintenance(maintenanceVehicle.value.id, payload)
    toast.success('Mantenimiento registrado')
    maintenanceFormOpen.value = false
    await queryClient.invalidateQueries({ queryKey: ['maintenance', maintenanceVehicle.value.id] })
  } catch (failure) {
    maintenanceProblems.value = problemsOf(failure)
  } finally {
    maintenanceSubmitting.value = false
  }
}

async function removeMaintenance(record: Maintenance) {
  try {
    await vehiclesApi.removeMaintenance(record.id)
    toast.success('Mantenimiento eliminado')
    if (maintenanceVehicle.value) {
      await queryClient.invalidateQueries({ queryKey: ['maintenance', maintenanceVehicle.value.id] })
    }
  } catch (failure) {
    toast.error(problemsOf(failure).join(' '))
  }
}
</script>

<template>
  <div class="flex flex-col gap-6">
    <PageHeader title="Unidades" description="Flota, capacidades y mantenimiento">
      <Button variant="outline" size="sm" :disabled="isFetching" @click="queryClient.invalidateQueries({ queryKey: ['vehicles'] })">
        <RefreshCw :class="{ 'animate-spin': isFetching }" />
        Recargar
      </Button>
      <Button v-if="canWrite" size="sm" @click="openCreate">
        <Plus />
        Nueva
      </Button>
    </PageHeader>

    <form class="flex items-center gap-2" @submit.prevent="applySearch">
      <Input v-model="searchInput" placeholder="Buscar por economico o placas" class="max-w-xs" />
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
            <TableHead>Economico</TableHead>
            <TableHead>Placas</TableHead>
            <TableHead>Marca</TableHead>
            <TableHead>Modelo</TableHead>
            <TableHead class="text-right">Anio</TableHead>
            <TableHead class="text-right">Capacidad</TableHead>
            <TableHead class="text-right">Kilometraje</TableHead>
            <TableHead>Estado</TableHead>
            <TableHead class="text-right">Acciones</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-if="!vehicles?.length">
            <TableCell colspan="9" class="py-8 text-center text-sm text-muted-foreground">
              {{ isFetching ? 'Cargando...' : 'Sin resultados' }}
            </TableCell>
          </TableRow>
          <TableRow v-for="vehicle in vehicles" :key="vehicle.id" @dblclick="canWrite && openEdit(vehicle)">
            <TableCell class="font-medium">{{ vehicle.internalCode }}</TableCell>
            <TableCell>{{ vehicle.plates }}</TableCell>
            <TableCell>{{ vehicle.brand }}</TableCell>
            <TableCell>{{ vehicle.model }}</TableCell>
            <TableCell class="text-right tabular-nums">{{ vehicle.year ?? '' }}</TableCell>
            <TableCell class="text-right tabular-nums">{{ vehicle.loadCapacity }} kg</TableCell>
            <TableCell class="text-right tabular-nums">{{ vehicle.mileage }} km</TableCell>
            <TableCell>
              <Badge variant="outline">{{ labelOf(vehicleStatuses, vehicle.status) }}</Badge>
            </TableCell>
            <TableCell>
              <div class="flex justify-end gap-1">
                <Button variant="ghost" size="icon-sm" title="Mantenimiento" @click="openMaintenance(vehicle)">
                  <Wrench />
                </Button>
                <Button v-if="canWrite" variant="ghost" size="icon-sm" title="Cambiar estado" @click="((statusTarget = vehicle), (statusOpen = true), (statusProblems = []))">
                  <Truck />
                </Button>
                <Button v-if="canWrite" variant="ghost" size="icon-sm" title="Editar" @click="openEdit(vehicle)">
                  <Pencil />
                </Button>
                <Button v-if="canWrite" variant="ghost" size="icon-sm" title="Eliminar" @click="((deleteTarget = vehicle), (confirmOpen = true))">
                  <Trash2 class="text-destructive" />
                </Button>
              </div>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>

    <FormDialog
      v-model:open="formOpen"
      :title="editing ? 'Editar unidad' : 'Nueva unidad'"
      :fields="fields"
      :initial="formInitial"
      :schema="schema"
      :problems="formProblems"
      :submitting="submitting"
      @submit="save"
    />

    <FormDialog
      v-model:open="statusOpen"
      title="Cambiar estado"
      :fields="statusFields"
      :initial="{ status: statusTarget?.status ?? 'available' }"
      :problems="statusProblems"
      :submitting="statusSubmitting"
      @submit="saveStatus"
    />

    <Dialog v-model:open="maintenanceOpen">
      <DialogContent class="sm:max-w-4xl">
        <DialogHeader>
          <DialogTitle>Mantenimiento de {{ maintenanceVehicle?.internalCode }}</DialogTitle>
        </DialogHeader>

        <div class="flex justify-end">
          <Button v-if="canMaintenance" size="sm" @click="((maintenanceProblems = []), (maintenanceFormOpen = true))">
            <Plus />
            Registrar mantenimiento
          </Button>
        </div>

        <div class="rounded-lg border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Fecha</TableHead>
                <TableHead>Tipo</TableHead>
                <TableHead>Trabajos</TableHead>
                <TableHead>Proveedor</TableHead>
                <TableHead class="text-right">Costo</TableHead>
                <TableHead>Prox. fecha</TableHead>
                <TableHead class="text-right">Prox. km</TableHead>
                <TableHead class="text-right">Acciones</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-if="!maintenance?.length">
                <TableCell colspan="8" class="py-8 text-center text-sm text-muted-foreground">
                  Sin mantenimientos registrados
                </TableCell>
              </TableRow>
              <TableRow v-for="record in maintenance" :key="record.id">
                <TableCell>{{ formatDate(record.maintenanceDate) }}</TableCell>
                <TableCell>{{ labelOf(maintenanceTypes, record.type) }}</TableCell>
                <TableCell class="max-w-xs truncate">{{ record.workPerformed }}</TableCell>
                <TableCell>{{ record.provider }}</TableCell>
                <TableCell class="text-right tabular-nums">{{ formatMoney(record.cost) }}</TableCell>
                <TableCell>{{ formatDate(record.nextServiceDate) }}</TableCell>
                <TableCell class="text-right tabular-nums">{{ record.nextServiceKm ?? '' }}</TableCell>
                <TableCell class="text-right">
                  <Button v-if="canMaintenance" variant="ghost" size="icon-sm" title="Eliminar" @click="removeMaintenance(record)">
                    <Trash2 class="text-destructive" />
                  </Button>
                </TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </div>
      </DialogContent>
    </Dialog>

    <FormDialog
      v-model:open="maintenanceFormOpen"
      title="Registrar mantenimiento"
      :fields="maintenanceFields"
      :initial="{ type: 'preventive', level: null }"
      :schema="maintenanceSchema"
      :problems="maintenanceProblems"
      :submitting="maintenanceSubmitting"
      @submit="saveMaintenance"
    />

    <ConfirmDialog
      v-model:open="confirmOpen"
      title="Eliminar unidad"
      :description="deleteTarget ? `Se eliminara la unidad ${deleteTarget.internalCode}.` : ''"
      @confirm="confirmDelete"
    />
  </div>
</template>
