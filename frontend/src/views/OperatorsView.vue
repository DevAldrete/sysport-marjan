<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { Pencil, Plus, RefreshCw, Search, Trash2, UserCog } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { z } from 'zod'

import { problemsOf } from '@/api/errors'
import { operatorsApi } from '@/api/operators'
import type { Employee } from '@/api/types'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormDialog, { type FormField } from '@/components/FormDialog.vue'
import PageHeader from '@/components/PageHeader.vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { employeeStatuses, labelOf } from '@/lib/enums'
import { formatDate } from '@/lib/format'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const queryClient = useQueryClient()
const canWrite = computed(() => auth.can('operators.write'))

const searchInput = ref('')
const term = ref('')

const { data: operators, isFetching, isError, error } = useQuery({
  queryKey: computed(() => ['operators', term.value]),
  queryFn: () => operatorsApi.search(term.value),
})

function applySearch() {
  term.value = searchInput.value.trim()
}

function licenseBadge(employee: Employee): { text: string; variant: 'secondary' | 'outline' | 'destructive' } {
  const date = employee.license?.expirationDate
  if (!employee.license || !date) {
    return { text: 'Sin licencia', variant: 'outline' }
  }
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const days = Math.round((new Date(`${date}T00:00:00`).getTime() - today.getTime()) / 86_400_000)
  if (days < 0) {
    return { text: `Vencida (${formatDate(date)})`, variant: 'destructive' }
  }
  if (days <= 30) {
    return { text: `Por vencer (${formatDate(date)})`, variant: 'secondary' }
  }
  return { text: formatDate(date), variant: 'outline' }
}

const fields: FormField[] = [
  { key: 'name', label: 'Nombre', full: true },
  { key: 'phone', label: 'Telefono' },
  { key: 'email', label: 'Correo' },
  { key: 'rfc', label: 'RFC' },
  { key: 'curp', label: 'CURP' },
  { key: 'address', label: 'Domicilio', full: true },
  { key: 'emergencyContactName', label: 'Contacto de emergencia' },
  { key: 'emergencyContactPhone', label: 'Telefono de emergencia' },
  { key: 'licenseNumber', label: 'No. de licencia' },
  { key: 'licenseType', label: 'Tipo de licencia' },
  { key: 'issueDate', label: 'Expedicion', type: 'date' },
  { key: 'expirationDate', label: 'Vencimiento', type: 'date' },
]

const schema = z.object({
  name: z.string().min(1, 'El nombre es obligatorio'),
  phone: z.string().min(1, 'El telefono es obligatorio'),
  email: z.string().min(1, 'El correo es obligatorio'),
  rfc: z.string().min(1, 'El RFC es obligatorio'),
  curp: z.string().min(1, 'La CURP es obligatoria'),
  address: z.string().default(''),
  emergencyContactName: z.string().default(''),
  emergencyContactPhone: z.string().default(''),
  licenseNumber: z.string().default(''),
  licenseType: z.string().default(''),
  issueDate: z.string().default(''),
  expirationDate: z.string().default(''),
})

const formOpen = ref(false)
const editing = ref<Employee | null>(null)
const formProblems = ref<string[]>([])
const submitting = ref(false)

const formInitial = computed(() =>
  editing.value
    ? {
        ...editing.value,
        licenseNumber: editing.value.license?.licenseNumber ?? '',
        licenseType: editing.value.license?.licenseType ?? '',
        issueDate: editing.value.license?.issueDate ?? '',
        expirationDate: editing.value.license?.expirationDate ?? '',
      }
    : {},
)

function openCreate() {
  editing.value = null
  formProblems.value = []
  formOpen.value = true
}

function openEdit(employee: Employee) {
  editing.value = employee
  formProblems.value = []
  formOpen.value = true
}

async function save(values: Record<string, unknown>) {
  formProblems.value = []
  submitting.value = true
  const { licenseNumber, licenseType, issueDate, expirationDate, ...rest } = values as Record<string, any>
  const payload = {
    ...rest,
    status: editing.value?.status ?? 'available',
    license: licenseNumber
      ? {
          id: editing.value?.license?.id ?? 0,
          licenseNumber,
          licenseType,
          issueDate: issueDate || null,
          expirationDate: expirationDate || null,
        }
      : null,
  }
  try {
    if (editing.value) {
      await operatorsApi.update(editing.value.id, payload)
    } else {
      await operatorsApi.create(payload)
    }
    toast.success('Operador guardado')
    formOpen.value = false
    await queryClient.invalidateQueries({ queryKey: ['operators'] })
  } catch (failure) {
    formProblems.value = problemsOf(failure)
  } finally {
    submitting.value = false
  }
}

const statusTarget = ref<Employee | null>(null)
const statusOpen = ref(false)
const statusProblems = ref<string[]>([])
const statusSubmitting = ref(false)

const statusFields: FormField[] = [
  { key: 'status', label: 'Estado', type: 'select', options: employeeStatuses, full: true },
]

async function saveStatus(values: Record<string, unknown>) {
  if (!statusTarget.value) {
    return
  }
  statusProblems.value = []
  statusSubmitting.value = true
  try {
    await operatorsApi.setStatus(statusTarget.value.id, values.status as string)
    toast.success('Estado actualizado')
    statusOpen.value = false
    await queryClient.invalidateQueries({ queryKey: ['operators'] })
  } catch (failure) {
    statusProblems.value = problemsOf(failure)
  } finally {
    statusSubmitting.value = false
  }
}

const deleteTarget = ref<Employee | null>(null)
const confirmOpen = ref(false)

async function confirmDelete() {
  if (!deleteTarget.value) {
    return
  }
  try {
    await operatorsApi.remove(deleteTarget.value.id)
    toast.success('Operador eliminado')
  } catch (failure) {
    toast.error(problemsOf(failure).join(' '))
  } finally {
    confirmOpen.value = false
    await queryClient.invalidateQueries({ queryKey: ['operators'] })
  }
}
</script>

<template>
  <div class="flex flex-col gap-6">
    <PageHeader title="Operadores" description="Empleados, licencias y disponibilidad">
      <Button variant="outline" size="sm" :disabled="isFetching" @click="queryClient.invalidateQueries({ queryKey: ['operators'] })">
        <RefreshCw :class="{ 'animate-spin': isFetching }" />
        Recargar
      </Button>
      <Button v-if="canWrite" size="sm" @click="openCreate">
        <Plus />
        Nuevo
      </Button>
    </PageHeader>

    <form class="flex items-center gap-2" @submit.prevent="applySearch">
      <Input v-model="searchInput" placeholder="Buscar por nombre o licencia" class="max-w-xs" />
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
            <TableHead>Telefono</TableHead>
            <TableHead>Licencia</TableHead>
            <TableHead>Vence</TableHead>
            <TableHead>Estado</TableHead>
            <TableHead class="text-right">Acciones</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-if="!operators?.length">
            <TableCell colspan="6" class="py-8 text-center text-sm text-muted-foreground">
              {{ isFetching ? 'Cargando...' : 'Sin resultados' }}
            </TableCell>
          </TableRow>
          <TableRow v-for="employee in operators" :key="employee.id" @dblclick="canWrite && openEdit(employee)">
            <TableCell class="font-medium">{{ employee.name }}</TableCell>
            <TableCell>{{ employee.phone }}</TableCell>
            <TableCell>{{ employee.license?.licenseNumber ?? 'Sin licencia' }}</TableCell>
            <TableCell>
              <Badge :variant="licenseBadge(employee).variant">{{ licenseBadge(employee).text }}</Badge>
            </TableCell>
            <TableCell>{{ labelOf(employeeStatuses, employee.status) }}</TableCell>
            <TableCell>
              <div class="flex justify-end gap-1">
                <Button v-if="canWrite" variant="ghost" size="icon-sm" title="Cambiar estado" @click="((statusTarget = employee), (statusOpen = true), (statusProblems = []))">
                  <UserCog />
                </Button>
                <Button v-if="canWrite" variant="ghost" size="icon-sm" title="Editar" @click="openEdit(employee)">
                  <Pencil />
                </Button>
                <Button v-if="canWrite" variant="ghost" size="icon-sm" title="Eliminar" @click="((deleteTarget = employee), (confirmOpen = true))">
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
      :title="editing ? 'Editar operador' : 'Nuevo operador'"
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

    <ConfirmDialog
      v-model:open="confirmOpen"
      title="Eliminar operador"
      :description="deleteTarget ? `Se eliminara ${deleteTarget.name}.` : ''"
      @confirm="confirmDelete"
    />
  </div>
</template>
