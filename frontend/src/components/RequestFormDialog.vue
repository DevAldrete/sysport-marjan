<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { toast } from 'vue-sonner'
import { z } from 'zod'

import { problemsOf } from '@/api/errors'
import { type PackageLine, requestsApi, type RequestWrite } from '@/api/requests'
import type { CargoPackage, ServiceRequest } from '@/api/types'
import FormDialog, { type FormField } from '@/components/FormDialog.vue'
import PackageEditor from '@/components/PackageEditor.vue'
import type { Option } from '@/lib/enums'

const props = defineProps<{
  open: boolean
  request: ServiceRequest | null
  clients: Option[]
  routes: Option[]
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  saved: []
}>()

const packages = ref<PackageLine[]>([])
const problems = ref<string[]>([])
const submitting = ref(false)

function toLocalInput(value: string | null): string {
  return value ? value.slice(0, 16) : ''
}

function toLine(cargoPackage: CargoPackage): PackageLine {
  return {
    id: cargoPackage.id,
    description: cargoPackage.description,
    quantity: cargoPackage.quantity,
    unit: cargoPackage.unit,
    unitWeight: cargoPackage.unitWeight,
  }
}

const fields = computed<FormField[]>(() => {
  const base: FormField[] = [
    { key: 'clientId', label: 'Cliente', type: 'select', options: props.clients },
    { key: 'routeId', label: 'Ruta', type: 'select', options: props.routes },
    { key: 'cargoDescription', label: 'Descripcion de la mercancia', full: true },
    { key: 'estimatedWeight', label: 'Peso manual (kg)', type: 'number' },
    { key: 'pickupScheduled', label: 'Recoleccion', type: 'datetime' },
    { key: 'deliveryScheduled', label: 'Entrega', type: 'datetime' },
    { key: 'requiresDocuments', label: 'Requiere documentacion', type: 'checkbox' },
    { key: 'notes', label: 'Observaciones', type: 'textarea' },
  ]
  if (props.request && props.request.status !== 'requested') {
    base.push({ key: 'agreedRate', label: 'Tarifa acordada', type: 'money' })
  }
  return base
})

const schema = z.object({
  clientId: z.coerce.number().int().positive('Seleccione un cliente'),
  routeId: z.coerce.number().int().positive('Seleccione una ruta'),
  cargoDescription: z.string().min(1, 'Describa la mercancia'),
  estimatedWeight: z.coerce.number().min(0).optional(),
  pickupScheduled: z.string().default(''),
  deliveryScheduled: z.string().default(''),
  agreedRate: z.coerce.number().min(0).optional(),
  requiresDocuments: z.boolean().default(true),
  notes: z.string().default(''),
})

const initial = computed(() =>
  props.request
    ? {
        clientId: String(props.request.clientId),
        routeId: String(props.request.routeId),
        cargoDescription: props.request.cargoDescription ?? '',
        estimatedWeight: props.request.estimatedWeight,
        pickupScheduled: toLocalInput(props.request.pickupScheduled),
        deliveryScheduled: toLocalInput(props.request.deliveryScheduled),
        agreedRate: props.request.agreedRate,
        requiresDocuments: props.request.requiresDocuments,
        notes: props.request.notes ?? '',
      }
    : {
        clientId: props.clients[0]?.value ?? '',
        routeId: props.routes[0]?.value ?? '',
        requiresDocuments: true,
        estimatedWeight: null,
      },
)

watch(
  [() => props.open, () => props.request],
  async () => {
    if (!props.open) {
      return
    }
    problems.value = []
    packages.value = props.request
      ? (await requestsApi.packages(props.request.id)).map(toLine)
      : []
  },
)

async function save(values: Record<string, unknown>) {
  problems.value = []
  submitting.value = true
  const payload: RequestWrite = {
    clientId: Number(values.clientId),
    routeId: Number(values.routeId),
    cargoDescription: values.cargoDescription as string,
    estimatedWeight: values.estimatedWeight == null ? null : Number(values.estimatedWeight),
    pickupScheduled: (values.pickupScheduled as string) || null,
    deliveryScheduled: (values.deliveryScheduled as string) || null,
    agreedRate:
      props.request && props.request.status !== 'requested' && values.agreedRate != null
        ? Number(values.agreedRate)
        : null,
    requiresDocuments: Boolean(values.requiresDocuments),
    notes: (values.notes as string) ?? '',
    packages: packages.value,
  }
  try {
    if (props.request) {
      await requestsApi.update(props.request.id, payload)
    } else {
      await requestsApi.create(payload)
    }
    toast.success('Solicitud guardada')
    emit('saved')
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
    :title="request ? `Editar solicitud ${request.folio}` : 'Nueva solicitud'"
    :fields="fields"
    :initial="initial"
    :schema="schema"
    :problems="problems"
    :submitting="submitting"
    @update:open="emit('update:open', $event)"
    @submit="save"
  >
    <PackageEditor v-model="packages" />
  </FormDialog>
</template>
