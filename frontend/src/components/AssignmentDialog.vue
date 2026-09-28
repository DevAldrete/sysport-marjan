<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { toast } from 'vue-sonner'
import { z } from 'zod'

import { problemsOf } from '@/api/errors'
import { requestsApi } from '@/api/requests'
import type { ServiceRequest } from '@/api/types'
import FormDialog, { type FormField } from '@/components/FormDialog.vue'

const props = defineProps<{ open: boolean; request: ServiceRequest }>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  assigned: []
}>()

const problems = ref<string[]>([])
const submitting = ref(false)
const selectedVehicleId = ref<string>('')

const { data: options } = useQuery({
  queryKey: computed(() => ['assignment', props.request.id]),
  queryFn: () => requestsApi.assignment(props.request.id),
  enabled: computed(() => props.open),
})

const vehicleOptions = computed(() =>
  (options.value?.vehicles ?? []).map((vehicle) => ({
    value: String(vehicle.id),
    label: `${vehicle.internalCode} (${vehicle.plates}) - ${vehicle.loadCapacity} kg`,
  })),
)

const operatorOptions = computed(() =>
  (options.value?.operators ?? []).map((operator) => ({
    value: String(operator.id),
    label: operator.name,
  })),
)

const fields = computed<FormField[]>(() => [
  { key: 'vehicleId', label: 'Unidad', type: 'select', options: vehicleOptions.value, full: true },
  { key: 'operatorId', label: 'Operador', type: 'select', options: operatorOptions.value, full: true },
])

const schema = z.object({
  vehicleId: z.coerce.number().int().positive('Seleccione una unidad'),
  operatorId: z.coerce.number().int().positive('Seleccione un operador'),
})

const effectiveWeight = computed(
  () => Number(props.request.packageWeight ?? props.request.estimatedWeight ?? 0),
)
const selectedVehicle = computed(() =>
  (options.value?.vehicles ?? []).find((vehicle) => String(vehicle.id) === selectedVehicleId.value),
)
const capacityHint = computed(() => {
  if (!selectedVehicle.value) {
    return ''
  }
  const capacity = Number(selectedVehicle.value.loadCapacity)
  const fits = capacity >= effectiveWeight.value
  return `Capacidad ${capacity.toLocaleString('es-MX')} kg vs ${effectiveWeight.value.toLocaleString('es-MX')} kg - ${fits ? 'suficiente' : 'EXCEDE LA CAPACIDAD'}`
})

watch(
  () => props.open,
  () => {
    if (props.open) {
      problems.value = []
      selectedVehicleId.value = ''
    }
  },
)

async function assign(values: Record<string, unknown>) {
  problems.value = []
  submitting.value = true
  try {
    await requestsApi.assign(props.request.id, Number(values.vehicleId), Number(values.operatorId))
    toast.success('Viaje asignado')
    emit('assigned')
    emit('update:open', false)
  } catch (failure) {
    problems.value = problemsOf(failure)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <FormDialog
    :open="open"
    :title="`Asignar viaje ${request.folio}`"
    :fields="fields"
    :initial="{ vehicleId: vehicleOptions[0]?.value ?? '', operatorId: operatorOptions[0]?.value ?? '' }"
    :schema="schema"
    :problems="problems"
    :submitting="submitting"
    submit-label="Asignar"
    @update:open="emit('update:open', $event)"
    @change="selectedVehicleId = String($event.vehicleId ?? '')"
    @submit="assign"
  >
    <p v-if="capacityHint" class="rounded-md bg-muted px-3 py-2 text-sm text-muted-foreground">
      {{ capacityHint }}
    </p>
  </FormDialog>
</template>
