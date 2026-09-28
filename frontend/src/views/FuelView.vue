<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { Plus, RefreshCw, Trash2 } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { z } from 'zod'

import { problemsOf } from '@/api/errors'
import { fuelApi } from '@/api/fuel'
import type { FuelLoad } from '@/api/types'
import { vehiclesApi } from '@/api/vehicles'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormDialog, { type FormField } from '@/components/FormDialog.vue'
import PageHeader from '@/components/PageHeader.vue'
import { Button } from '@/components/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { formatDateTime, formatMoney } from '@/lib/format'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const queryClient = useQueryClient()
const canWrite = computed(() => auth.can('fuel.write'))

const { data: loads, isFetching, isError, error } = useQuery({
  queryKey: ['fuel'],
  queryFn: () => fuelApi.list(),
})

const { data: vehicleList } = useQuery({ queryKey: ['vehicles', 'all'], queryFn: () => vehiclesApi.search('') })
const vehicleOptions = computed(() =>
  (vehicleList.value ?? []).map((vehicle) => ({
    value: String(vehicle.id),
    label: `${vehicle.internalCode} (${vehicle.plates})`,
  })),
)

const fields = computed<FormField[]>(() => [
  { key: 'vehicleId', label: 'Unidad', type: 'select', options: vehicleOptions.value, full: true },
  { key: 'fuelStation', label: 'Estacion de servicio' },
  { key: 'loadDate', label: 'Fecha y hora', type: 'datetime' },
  { key: 'liters', label: 'Litros', type: 'number' },
  { key: 'pricePerLiter', label: 'Precio por litro', type: 'money' },
  { key: 'odometerReading', label: 'Odometro', type: 'number' },
])

const schema = z.object({
  vehicleId: z.coerce.number().int().positive('Seleccione una unidad'),
  fuelStation: z.string().min(1, 'La estacion es obligatoria'),
  loadDate: z.string().min(1, 'La fecha es obligatoria'),
  liters: z.coerce.number().min(0.01, 'Los litros deben ser mayores a cero'),
  pricePerLiter: z.coerce.number().min(0.01, 'El precio debe ser mayor a cero'),
  odometerReading: z.coerce.number().min(0).default(0),
})

const formOpen = ref(false)
const formProblems = ref<string[]>([])
const submitting = ref(false)

async function save(values: Record<string, unknown>) {
  formProblems.value = []
  submitting.value = true
  const liters = Number(values.liters)
  const price = Number(values.pricePerLiter)
  const amount = Math.round(liters * price * 100) / 100
  try {
    await fuelApi.create({
      vehicleId: Number(values.vehicleId),
      fuelStation: values.fuelStation as string,
      loadDate: values.loadDate as string,
      liters,
      pricePerLiter: price,
      amount,
      odometerReading: Number(values.odometerReading),
    })
    toast.success('Carga registrada')
    formOpen.value = false
    await queryClient.invalidateQueries({ queryKey: ['fuel'] })
  } catch (failure) {
    formProblems.value = problemsOf(failure)
  } finally {
    submitting.value = false
  }
}

const deleteTarget = ref<FuelLoad | null>(null)
const confirmOpen = ref(false)

async function confirmDelete() {
  if (!deleteTarget.value) {
    return
  }
  try {
    await fuelApi.remove(deleteTarget.value.id)
    toast.success('Carga eliminada')
  } catch (failure) {
    toast.error(problemsOf(failure).join(' '))
  } finally {
    confirmOpen.value = false
    await queryClient.invalidateQueries({ queryKey: ['fuel'] })
  }
}
</script>

<template>
  <div class="flex flex-col gap-6">
    <PageHeader title="Combustible" description="Cargas de combustible por unidad">
      <Button variant="outline" size="sm" :disabled="isFetching" @click="queryClient.invalidateQueries({ queryKey: ['fuel'] })">
        <RefreshCw :class="{ 'animate-spin': isFetching }" />
        Recargar
      </Button>
      <Button v-if="canWrite" size="sm" @click="((formProblems = []), (formOpen = true))">
        <Plus />
        Nueva carga
      </Button>
    </PageHeader>

    <p v-if="isError" class="text-sm text-destructive">{{ problemsOf(error).join(' ') }}</p>

    <div class="rounded-lg border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Unidad</TableHead>
            <TableHead>Viaje</TableHead>
            <TableHead>Fecha</TableHead>
            <TableHead>Estacion</TableHead>
            <TableHead class="text-right">Litros</TableHead>
            <TableHead class="text-right">Precio/L</TableHead>
            <TableHead class="text-right">Importe</TableHead>
            <TableHead class="text-right">Odometro</TableHead>
            <TableHead class="text-right">Acciones</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-if="!loads?.length">
            <TableCell colspan="9" class="py-8 text-center text-sm text-muted-foreground">
              {{ isFetching ? 'Cargando...' : 'Sin resultados' }}
            </TableCell>
          </TableRow>
          <TableRow v-for="load in loads" :key="load.id">
            <TableCell class="font-medium">{{ load.vehicleLabel }}</TableCell>
            <TableCell>{{ load.tripLabel ?? '' }}</TableCell>
            <TableCell>{{ formatDateTime(load.loadDate) }}</TableCell>
            <TableCell>{{ load.fuelStation }}</TableCell>
            <TableCell class="text-right tabular-nums">{{ load.liters }}</TableCell>
            <TableCell class="text-right tabular-nums">{{ formatMoney(load.pricePerLiter) }}</TableCell>
            <TableCell class="text-right tabular-nums">{{ formatMoney(load.amount) }}</TableCell>
            <TableCell class="text-right tabular-nums">{{ load.odometerReading }}</TableCell>
            <TableCell class="text-right">
              <Button v-if="canWrite" variant="ghost" size="icon-sm" title="Eliminar" @click="((deleteTarget = load), (confirmOpen = true))">
                <Trash2 class="text-destructive" />
              </Button>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>

    <FormDialog
      v-model:open="formOpen"
      title="Nueva carga de combustible"
      :fields="fields"
      :initial="{ vehicleId: vehicleOptions[0]?.value ?? '', loadDate: '' }"
      :schema="schema"
      :problems="formProblems"
      :submitting="submitting"
      @submit="save"
    />

    <ConfirmDialog
      v-model:open="confirmOpen"
      title="Eliminar carga"
      :description="deleteTarget ? `Se eliminara la carga de ${deleteTarget.vehicleLabel}.` : ''"
      @confirm="confirmDelete"
    />
  </div>
</template>
