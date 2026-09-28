<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { toast } from 'vue-sonner'
import { z } from 'zod'

import { problemsOf } from '@/api/errors'
import { requestsApi } from '@/api/requests'
import { tripsApi } from '@/api/trips'
import type { Trip } from '@/api/types'
import FormDialog, { type FormField } from '@/components/FormDialog.vue'

const props = defineProps<{ open: boolean; trip: Trip }>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  reassigned: []
}>()

const problems = ref<string[]>([])
const submitting = ref(false)

const { data: options } = useQuery({
  queryKey: computed(() => ['assignment', props.trip.serviceRequestId]),
  queryFn: () => requestsApi.assignment(props.trip.serviceRequestId),
  enabled: computed(() => props.open),
})

const vehicleOptions = computed(() =>
  (options.value?.vehicles ?? []).map((vehicle) => ({
    value: String(vehicle.id),
    label: `${vehicle.internalCode} (${vehicle.plates})`,
  })),
)
const operatorOptions = computed(() =>
  (options.value?.operators ?? []).map((operator) => ({ value: String(operator.id), label: operator.name })),
)

const fields = computed<FormField[]>(() => [
  { key: 'vehicleId', label: 'Unidad', type: 'select', options: vehicleOptions.value, full: true },
  { key: 'operatorId', label: 'Operador', type: 'select', options: operatorOptions.value, full: true },
])

const schema = z.object({
  vehicleId: z.coerce.number().int().positive('Seleccione una unidad'),
  operatorId: z.coerce.number().int().positive('Seleccione un operador'),
})

watch(
  () => props.open,
  () => {
    if (props.open) {
      problems.value = []
    }
  },
)

async function reassign(values: Record<string, unknown>) {
  problems.value = []
  submitting.value = true
  try {
    await tripsApi.reassign(props.trip.id, Number(values.vehicleId), Number(values.operatorId))
    toast.success('Viaje reasignado')
    emit('reassigned')
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
    :title="`Reasignar ${trip.folio}`"
    :fields="fields"
    :initial="{ vehicleId: String(trip.vehicleId), operatorId: String(trip.employeeId) }"
    :schema="schema"
    :problems="problems"
    :submitting="submitting"
    submit-label="Reasignar"
    @update:open="emit('update:open', $event)"
    @submit="reassign"
  />
</template>
