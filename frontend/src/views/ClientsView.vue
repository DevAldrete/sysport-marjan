<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { Coins, Pencil, Plus, RefreshCw, Search, Trash2, UserCheck, UserX } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { z } from 'zod'

import { clientsApi } from '@/api/clients'
import { problemsOf } from '@/api/errors'
import { routesApi } from '@/api/routes'
import type { Client, ClientRate } from '@/api/types'
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
import { clientStatuses, clientTypes, labelOf, paymentTerms } from '@/lib/enums'
import { formatDate, formatMoney } from '@/lib/format'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const queryClient = useQueryClient()
const canWrite = computed(() => auth.can('clients.write'))
const canRates = computed(() => auth.can('rates.read'))

const searchInput = ref('')
const term = ref('')

const { data: clients, isFetching, isError, error } = useQuery({
  queryKey: computed(() => ['clients', term.value]),
  queryFn: () => clientsApi.search(term.value),
})

function applySearch() {
  term.value = searchInput.value.trim()
}

const clientFields: FormField[] = [
  { key: 'name', label: 'Nombre o razon social', full: true },
  { key: 'rfc', label: 'RFC' },
  { key: 'contactName', label: 'Contacto' },
  { key: 'phone', label: 'Telefono' },
  { key: 'email', label: 'Correo' },
  { key: 'address', label: 'Domicilio', full: true },
  { key: 'clientType', label: 'Tipo', type: 'select', options: clientTypes },
  { key: 'paymentTerms', label: 'Condiciones', type: 'select', options: paymentTerms },
  { key: 'creditLimit', label: 'Limite de credito', type: 'money' },
  { key: 'creditDays', label: 'Dias de credito', type: 'number' },
  { key: 'status', label: 'Estado', type: 'select', options: clientStatuses },
]

const clientSchema = z.object({
  name: z.string().min(1, 'El nombre es obligatorio'),
  rfc: z.string().min(1, 'El RFC es obligatorio'),
  contactName: z.string().default(''),
  phone: z.string().default(''),
  email: z.string().default(''),
  address: z.string().default(''),
  clientType: z.string().default('occasional'),
  paymentTerms: z.string().default('cash'),
  creditLimit: z.coerce.number().min(0).default(0),
  creditDays: z.coerce.number().int().min(0).default(0),
  status: z.string().default('active'),
})

const formOpen = ref(false)
const editing = ref<Client | null>(null)
const formProblems = ref<string[]>([])
const submitting = ref(false)

const formInitial = computed(() =>
  editing.value
    ? { ...editing.value }
    : { clientType: 'occasional', paymentTerms: 'cash', creditLimit: 0, creditDays: 0, status: 'active' },
)

function openCreate() {
  editing.value = null
  formProblems.value = []
  formOpen.value = true
}

function openEdit(client: Client) {
  editing.value = client
  formProblems.value = []
  formOpen.value = true
}

async function save(values: Record<string, unknown>) {
  formProblems.value = []
  submitting.value = true
  try {
    if (editing.value) {
      await clientsApi.update(editing.value.id, values)
    } else {
      await clientsApi.create(values)
    }
    toast.success('Cliente guardado')
    formOpen.value = false
    await queryClient.invalidateQueries({ queryKey: ['clients'] })
  } catch (failure) {
    formProblems.value = problemsOf(failure)
  } finally {
    submitting.value = false
  }
}

const deleteTarget = ref<Client | null>(null)
const confirmOpen = ref(false)

function askDelete(client: Client) {
  deleteTarget.value = client
  confirmOpen.value = true
}

async function confirmDelete() {
  if (!deleteTarget.value) {
    return
  }
  try {
    await clientsApi.remove(deleteTarget.value.id)
    toast.success('Cliente eliminado')
  } catch (failure) {
    toast.error(problemsOf(failure).join(' '))
  } finally {
    confirmOpen.value = false
    await queryClient.invalidateQueries({ queryKey: ['clients'] })
  }
}

async function toggleStatus(client: Client) {
  try {
    await clientsApi.setStatus(client.id, client.status === 'active' ? 'inactive' : 'active')
    await queryClient.invalidateQueries({ queryKey: ['clients'] })
  } catch (failure) {
    toast.error(problemsOf(failure).join(' '))
  }
}

// --- negotiated rates -------------------------------------------------------

const ratesClient = ref<Client | null>(null)
const ratesOpen = ref(false)

const { data: rates } = useQuery({
  queryKey: computed(() => ['client-rates', ratesClient.value?.id]),
  queryFn: () => clientsApi.rates(ratesClient.value!.id),
  enabled: computed(() => ratesOpen.value && ratesClient.value !== null),
})

const { data: routeList } = useQuery({ queryKey: ['routes', 'all'], queryFn: () => routesApi.all() })
const routeOptions = computed(() =>
  (routeList.value ?? []).map((route) => ({
    value: String(route.id),
    label: `${route.origin} -> ${route.destination}`,
  })),
)

function openRates(client: Client) {
  ratesClient.value = client
  ratesOpen.value = true
}

const rateFormOpen = ref(false)
const rateProblems = ref<string[]>([])
const rateSubmitting = ref(false)

const rateFields = computed<FormField[]>(() => [
  { key: 'routeId', label: 'Ruta', type: 'select', options: routeOptions.value, full: true },
  { key: 'rate', label: 'Tarifa', type: 'money' },
  { key: 'validFrom', label: 'Vigente desde', type: 'date' },
  { key: 'validTo', label: 'Vigente hasta', type: 'date' },
])

const rateSchema = z.object({
  routeId: z.coerce.number().int().positive('Seleccione una ruta'),
  rate: z.coerce.number().min(0.01, 'La tarifa debe ser mayor a cero'),
  validFrom: z.string().min(1, 'La fecha inicial es obligatoria'),
  validTo: z.string().optional().default(''),
})

async function saveRate(values: Record<string, unknown>) {
  if (!ratesClient.value) {
    return
  }
  rateProblems.value = []
  rateSubmitting.value = true
  try {
    await clientsApi.saveRate(ratesClient.value.id, {
      routeId: values.routeId as number,
      rate: values.rate as number,
      validFrom: values.validFrom as string,
      validTo: (values.validTo as string) || null,
    })
    toast.success('Tarifa guardada')
    rateFormOpen.value = false
    await queryClient.invalidateQueries({ queryKey: ['client-rates', ratesClient.value.id] })
  } catch (failure) {
    rateProblems.value = problemsOf(failure)
  } finally {
    rateSubmitting.value = false
  }
}

async function removeRate(rate: ClientRate) {
  try {
    await clientsApi.removeRate(rate.id)
    toast.success('Tarifa eliminada')
    if (ratesClient.value) {
      await queryClient.invalidateQueries({ queryKey: ['client-rates', ratesClient.value.id] })
    }
  } catch (failure) {
    toast.error(problemsOf(failure).join(' '))
  }
}
</script>

<template>
  <div class="flex flex-col gap-6">
    <PageHeader title="Clientes" description="Clientes, condiciones comerciales y tarifas negociadas">
      <Button variant="outline" size="sm" :disabled="isFetching" @click="queryClient.invalidateQueries({ queryKey: ['clients'] })">
        <RefreshCw :class="{ 'animate-spin': isFetching }" />
        Recargar
      </Button>
      <Button v-if="canWrite" size="sm" @click="openCreate">
        <Plus />
        Nuevo
      </Button>
    </PageHeader>

    <form class="flex items-center gap-2" @submit.prevent="applySearch">
      <Input v-model="searchInput" placeholder="Buscar por nombre o RFC" class="max-w-xs" />
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
            <TableHead>Nombre</TableHead>
            <TableHead>RFC</TableHead>
            <TableHead>Contacto</TableHead>
            <TableHead>Tipo</TableHead>
            <TableHead>Pago</TableHead>
            <TableHead class="text-right">Credito</TableHead>
            <TableHead>Estado</TableHead>
            <TableHead class="text-right">Acciones</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-if="!clients?.length">
            <TableCell colspan="8" class="py-8 text-center text-sm text-muted-foreground">
              {{ isFetching ? 'Cargando...' : 'Sin resultados' }}
            </TableCell>
          </TableRow>
          <TableRow v-for="client in clients" :key="client.id" @dblclick="canWrite && openEdit(client)">
            <TableCell class="font-medium">{{ client.name }}</TableCell>
            <TableCell>{{ client.rfc }}</TableCell>
            <TableCell>{{ client.contactName }}</TableCell>
            <TableCell>{{ labelOf(clientTypes, client.clientType) }}</TableCell>
            <TableCell>{{ labelOf(paymentTerms, client.paymentTerms) }}</TableCell>
            <TableCell class="text-right tabular-nums">{{ formatMoney(client.creditLimit) }}</TableCell>
            <TableCell>
              <Badge :variant="client.status === 'active' ? 'secondary' : 'outline'">
                {{ labelOf(clientStatuses, client.status) }}
              </Badge>
            </TableCell>
            <TableCell>
              <div class="flex justify-end gap-1">
                <Button v-if="canRates" variant="ghost" size="icon-sm" title="Tarifas" @click="openRates(client)">
                  <Coins />
                </Button>
                <Button v-if="canWrite" variant="ghost" size="icon-sm" title="Editar" @click="openEdit(client)">
                  <Pencil />
                </Button>
                <Button v-if="canWrite" variant="ghost" size="icon-sm" :title="client.status === 'active' ? 'Desactivar' : 'Activar'" @click="toggleStatus(client)">
                  <UserX v-if="client.status === 'active'" />
                  <UserCheck v-else />
                </Button>
                <Button v-if="canWrite" variant="ghost" size="icon-sm" title="Eliminar" @click="askDelete(client)">
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
      :title="editing ? 'Editar cliente' : 'Nuevo cliente'"
      :fields="clientFields"
      :initial="formInitial"
      :schema="clientSchema"
      :problems="formProblems"
      :submitting="submitting"
      @submit="save"
    />

    <ConfirmDialog
      v-model:open="confirmOpen"
      title="Eliminar cliente"
      :description="deleteTarget ? `Se eliminara ${deleteTarget.name}. Esta accion no se puede deshacer.` : ''"
      @confirm="confirmDelete"
    />

    <Dialog v-model:open="ratesOpen">
      <DialogContent class="sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>Tarifas de {{ ratesClient?.name }}</DialogTitle>
        </DialogHeader>

        <div class="flex justify-end">
          <Button v-if="canWrite" size="sm" @click="rateFormOpen = true">
            <Plus />
            Nueva tarifa
          </Button>
        </div>

        <div class="rounded-lg border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Ruta</TableHead>
                <TableHead class="text-right">Tarifa</TableHead>
                <TableHead>Desde</TableHead>
                <TableHead>Hasta</TableHead>
                <TableHead class="text-right">Acciones</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-if="!rates?.length">
                <TableCell colspan="5" class="py-8 text-center text-sm text-muted-foreground">
                  Sin tarifas registradas
                </TableCell>
              </TableRow>
              <TableRow v-for="rate in rates" :key="rate.id">
                <TableCell>{{ rate.routeLabel }}</TableCell>
                <TableCell class="text-right tabular-nums">{{ formatMoney(rate.rate) }}</TableCell>
                <TableCell>{{ formatDate(rate.validFrom) }}</TableCell>
                <TableCell>{{ formatDate(rate.validTo) }}</TableCell>
                <TableCell class="text-right">
                  <Button v-if="canWrite" variant="ghost" size="icon-sm" title="Eliminar" @click="removeRate(rate)">
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
      v-model:open="rateFormOpen"
      title="Nueva tarifa"
      :fields="rateFields"
      :initial="{ routeId: routeOptions[0]?.value ?? '', rate: null, validFrom: '', validTo: '' }"
      :schema="rateSchema"
      :problems="rateProblems"
      :submitting="rateSubmitting"
      @submit="saveRate"
    />
  </div>
</template>
