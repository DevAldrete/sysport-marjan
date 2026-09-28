<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { Ban, EllipsisVertical, Plus, Receipt, RefreshCw, Trash2, Wallet } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { z } from 'zod'

import { clientsApi } from '@/api/clients'
import { problemsOf } from '@/api/errors'
import { invoicesApi } from '@/api/invoices'
import { requestsApi } from '@/api/requests'
import type { Invoice } from '@/api/types'
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
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
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
import { invoiceStatuses, labelOf, paymentMethods } from '@/lib/enums'
import { formatDate, formatMoney } from '@/lib/format'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const queryClient = useQueryClient()
const canWrite = computed(() => auth.can('invoices.write'))
const canPay = computed(() => auth.can('payments.write'))

const statusFilter = ref('')
const clientFilter = ref('')

const { data: invoices, isFetching, isError, error } = useQuery({
  queryKey: computed(() => ['invoices', statusFilter.value, clientFilter.value]),
  queryFn: () => invoicesApi.search(statusFilter.value, clientFilter.value),
})

const { data: clientList } = useQuery({ queryKey: ['clients', 'active'], queryFn: () => clientsApi.active() })
const clientOptions = computed(() =>
  (clientList.value ?? []).map((client) => ({ value: String(client.id), label: client.name })),
)

const { data: pending } = useQuery({ queryKey: ['pending-billing'], queryFn: () => requestsApi.pendingBilling() })

async function refresh() {
  await queryClient.invalidateQueries({ queryKey: ['invoices'] })
}

// --- create invoice ---------------------------------------------------------

const billOpen = ref(false)
const billProblems = ref<string[]>([])
const billSubmitting = ref(false)

const pendingOptions = computed(() =>
  (pending.value ?? []).map((request) => ({
    value: String(request.id),
    label: `${request.folio} - ${request.clientName} (${formatMoney(request.agreedRate ?? 0)})`,
  })),
)

const billFields = computed<FormField[]>(() => [
  { key: 'requestId', label: 'Solicitud', type: 'select', options: pendingOptions.value, full: true },
  { key: 'issueDate', label: 'Fecha de emision', type: 'date' },
  { key: 'amount', label: 'Importe (vacio = tarifa acordada)', hint: 'Deje vacio para usar la tarifa acordada' },
])

const billSchema = z.object({
  requestId: z.coerce.number().int().positive('Seleccione una solicitud'),
  issueDate: z.string().min(1, 'La fecha es obligatoria'),
  amount: z.string().default(''),
})

function openBill() {
  billProblems.value = []
  billOpen.value = true
}

async function saveBill(values: Record<string, unknown>) {
  billProblems.value = []
  billSubmitting.value = true
  const text = String(values.amount ?? '').trim()
  try {
    await invoicesApi.create(
      Number(values.requestId),
      values.issueDate as string,
      text ? Number(text) : null,
    )
    toast.success('Factura creada')
    billOpen.value = false
    await refresh()
    await queryClient.invalidateQueries({ queryKey: ['pending-billing'] })
  } catch (failure) {
    billProblems.value = problemsOf(failure)
  } finally {
    billSubmitting.value = false
  }
}

// --- payments ---------------------------------------------------------------

const paymentsOpen = ref(false)
const paymentsTarget = ref<Invoice | null>(null)

const { data: payments } = useQuery({
  queryKey: computed(() => ['invoice-payments', paymentsTarget.value?.id]),
  queryFn: () => invoicesApi.payments(paymentsTarget.value!.id),
  enabled: computed(() => paymentsOpen.value && paymentsTarget.value !== null),
})

function openPayments(invoice: Invoice) {
  paymentsTarget.value = invoice
  paymentsOpen.value = true
}

const paymentFormOpen = ref(false)
const paymentProblems = ref<string[]>([])
const paymentSubmitting = ref(false)

const paymentFields: FormField[] = [
  { key: 'amount', label: 'Monto del pago', type: 'money' },
  { key: 'date', label: 'Fecha', type: 'date' },
  { key: 'method', label: 'Forma de pago', type: 'select', options: paymentMethods },
]

const paymentSchema = z.object({
  amount: z.coerce.number().min(0.01, 'El monto debe ser mayor a cero'),
  date: z.string().min(1, 'La fecha es obligatoria'),
  method: z.string().default('cash'),
})

async function savePayment(values: Record<string, unknown>) {
  if (!paymentsTarget.value) {
    return
  }
  paymentProblems.value = []
  paymentSubmitting.value = true
  try {
    await invoicesApi.registerPayment(
      paymentsTarget.value.id,
      Number(values.amount),
      values.date as string,
      values.method as string,
    )
    toast.success('Pago registrado')
    paymentFormOpen.value = false
    await queryClient.invalidateQueries({ queryKey: ['invoice-payments', paymentsTarget.value.id] })
    await refresh()
  } catch (failure) {
    paymentProblems.value = problemsOf(failure)
  } finally {
    paymentSubmitting.value = false
  }
}

async function removePayment(id: number) {
  if (!paymentsTarget.value) {
    return
  }
  try {
    await invoicesApi.deletePayment(id)
    toast.success('Pago eliminado')
    await queryClient.invalidateQueries({ queryKey: ['invoice-payments', paymentsTarget.value.id] })
    await refresh()
  } catch (failure) {
    toast.error(problemsOf(failure).join(' '))
  }
}

// --- confirmations ----------------------------------------------------------

const confirmOpen = ref(false)
const confirmMode = ref<'cancel' | 'delete'>('delete')
const actionTarget = ref<Invoice | null>(null)

function askCancel(invoice: Invoice) {
  actionTarget.value = invoice
  confirmMode.value = 'cancel'
  confirmOpen.value = true
}

function askDelete(invoice: Invoice) {
  actionTarget.value = invoice
  confirmMode.value = 'delete'
  confirmOpen.value = true
}

async function confirmAction() {
  if (!actionTarget.value) {
    return
  }
  try {
    if (confirmMode.value === 'cancel') {
      await invoicesApi.cancel(actionTarget.value.id)
      toast.success('Factura cancelada')
    } else {
      await invoicesApi.remove(actionTarget.value.id)
      toast.success('Factura eliminada')
    }
  } catch (failure) {
    toast.error(problemsOf(failure).join(' '))
  } finally {
    confirmOpen.value = false
    await refresh()
  }
}

async function refreshStatuses() {
  try {
    const result = await invoicesApi.refreshStatuses()
    toast.success(`Estatus actualizados: ${result.updated}`)
    await refresh()
  } catch (failure) {
    toast.error(problemsOf(failure).join(' '))
  }
}

function balance(invoice: Invoice): number {
  return Number(invoice.amount) - Number(invoice.paid ?? 0)
}
</script>

<template>
  <div class="flex flex-col gap-6">
    <PageHeader title="Facturas" description="Facturacion y cobranza">
      <Button variant="outline" size="sm" :disabled="isFetching" @click="refresh">
        <RefreshCw :class="{ 'animate-spin': isFetching }" />
        Recargar
      </Button>
      <Button v-if="canWrite" variant="outline" size="sm" @click="refreshStatuses">Actualizar estatus</Button>
      <Button v-if="canWrite" size="sm" @click="openBill">
        <Plus />
        Facturar
      </Button>
    </PageHeader>

    <div class="flex flex-wrap items-center gap-2">
      <Select v-model="statusFilter">
        <SelectTrigger class="w-40"><SelectValue placeholder="Estado" /></SelectTrigger>
        <SelectContent>
          <SelectItem v-for="option in invoiceStatuses" :key="option.value" :value="option.value">
            {{ option.label }}
          </SelectItem>
        </SelectContent>
      </Select>
      <Select v-model="clientFilter">
        <SelectTrigger class="w-56"><SelectValue placeholder="Cliente" /></SelectTrigger>
        <SelectContent>
          <SelectItem v-for="option in clientOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </SelectItem>
        </SelectContent>
      </Select>
    </div>

    <p v-if="isError" class="text-sm text-destructive">{{ problemsOf(error).join(' ') }}</p>

    <div class="rounded-lg border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Factura</TableHead>
            <TableHead>Cliente</TableHead>
            <TableHead>Solicitud</TableHead>
            <TableHead class="text-right">Importe</TableHead>
            <TableHead class="text-right">Pagado</TableHead>
            <TableHead class="text-right">Saldo</TableHead>
            <TableHead>Vence</TableHead>
            <TableHead>Estado</TableHead>
            <TableHead class="text-right">Acciones</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-if="!invoices?.length">
            <TableCell colspan="9" class="py-8 text-center text-sm text-muted-foreground">
              {{ isFetching ? 'Cargando...' : 'Sin resultados' }}
            </TableCell>
          </TableRow>
          <TableRow v-for="invoice in invoices" :key="invoice.id">
            <TableCell class="font-medium">{{ invoice.invoiceNumber }}</TableCell>
            <TableCell>{{ invoice.clientName }}</TableCell>
            <TableCell>{{ invoice.requestFolio }}</TableCell>
            <TableCell class="text-right tabular-nums">{{ formatMoney(invoice.amount) }}</TableCell>
            <TableCell class="text-right tabular-nums">{{ formatMoney(invoice.paid) }}</TableCell>
            <TableCell class="text-right tabular-nums">{{ formatMoney(balance(invoice)) }}</TableCell>
            <TableCell>{{ formatDate(invoice.dueDate) }}</TableCell>
            <TableCell><Badge variant="outline">{{ labelOf(invoiceStatuses, invoice.status) }}</Badge></TableCell>
            <TableCell class="text-right">
              <DropdownMenu>
                <DropdownMenuTrigger as-child>
                  <Button variant="ghost" size="icon-sm"><EllipsisVertical /></Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" class="w-44">
                  <DropdownMenuItem @select="openPayments(invoice)"><Receipt /> Pagos</DropdownMenuItem>
                  <DropdownMenuItem v-if="canWrite && invoice.status !== 'cancelled'" @select="askCancel(invoice)">
                    <Ban /> Cancelar
                  </DropdownMenuItem>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem v-if="canWrite" class="text-destructive" @select="askDelete(invoice)">
                    <Trash2 /> Eliminar
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>

    <div v-if="pending?.length" class="flex flex-col gap-2">
      <p class="text-sm font-medium">Por facturar (tarifa autorizada sin factura)</p>
      <div class="rounded-lg border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Folio</TableHead>
              <TableHead>Cliente</TableHead>
              <TableHead>Ruta</TableHead>
              <TableHead>Estado</TableHead>
              <TableHead class="text-right">Tarifa</TableHead>
              <TableHead class="text-right">Acciones</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="request in pending" :key="request.id">
              <TableCell class="font-medium">{{ request.folio }}</TableCell>
              <TableCell>{{ request.clientName }}</TableCell>
              <TableCell class="max-w-xs truncate">{{ request.routeLabel }}</TableCell>
              <TableCell>{{ labelOf(invoiceStatuses, request.status) }}</TableCell>
              <TableCell class="text-right tabular-nums">{{ formatMoney(request.agreedRate) }}</TableCell>
              <TableCell class="text-right">
                <Button v-if="canWrite" variant="outline" size="xs" @click="openBill">
                  <Wallet /> Facturar
                </Button>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </div>
    </div>

    <FormDialog
      v-model:open="billOpen"
      title="Facturar"
      :fields="billFields"
      :initial="{ requestId: pendingOptions[0]?.value ?? '', issueDate: new Date().toISOString().slice(0, 10), amount: '' }"
      :schema="billSchema"
      :problems="billProblems"
      :submitting="billSubmitting"
      @submit="saveBill"
    />

    <Dialog v-model:open="paymentsOpen">
      <DialogContent class="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Pagos de {{ paymentsTarget?.invoiceNumber }}</DialogTitle>
        </DialogHeader>
        <div class="flex justify-end">
          <Button v-if="canPay" size="sm" @click="((paymentProblems = []), (paymentFormOpen = true))">
            <Plus /> Registrar pago
          </Button>
        </div>
        <div class="rounded-lg border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Fecha</TableHead>
                <TableHead class="text-right">Monto</TableHead>
                <TableHead>Forma</TableHead>
                <TableHead class="text-right">Acciones</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-if="!payments?.length">
                <TableCell colspan="4" class="py-6 text-center text-sm text-muted-foreground">Sin pagos</TableCell>
              </TableRow>
              <TableRow v-for="payment in payments" :key="payment.id">
                <TableCell>{{ formatDate(payment.paymentDate) }}</TableCell>
                <TableCell class="text-right tabular-nums">{{ formatMoney(payment.amount) }}</TableCell>
                <TableCell>{{ labelOf(paymentMethods, payment.method) }}</TableCell>
                <TableCell class="text-right">
                  <Button v-if="canPay" variant="ghost" size="icon-sm" @click="removePayment(payment.id)">
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
      v-model:open="paymentFormOpen"
      title="Registrar pago"
      :fields="paymentFields"
      :initial="{ date: new Date().toISOString().slice(0, 10), method: 'cash' }"
      :schema="paymentSchema"
      :problems="paymentProblems"
      :submitting="paymentSubmitting"
      @submit="savePayment"
    />

    <ConfirmDialog
      v-model:open="confirmOpen"
      :title="confirmMode === 'cancel' ? 'Cancelar factura' : 'Eliminar factura'"
      :description="actionTarget ? `Factura ${actionTarget.invoiceNumber}.` : ''"
      :confirm-label="confirmMode === 'cancel' ? 'Cancelar factura' : 'Eliminar'"
      @confirm="confirmAction"
    />
  </div>
</template>
