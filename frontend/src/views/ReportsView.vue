<script setup lang="ts">
import { computed, ref } from 'vue'
import { Download, FileBarChart, Loader2 } from '@lucide/vue'
import { toast } from 'vue-sonner'

import { problemsOf } from '@/api/errors'
import { reportsApi, type ReportKind } from '@/api/reports'
import type { Report } from '@/api/types'
import PageHeader from '@/components/PageHeader.vue'
import { Button } from '@/components/ui/button'
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
import { formatDate } from '@/lib/format'

interface KindOption {
  value: ReportKind
  label: string
  ranged: boolean
}

const kinds: KindOption[] = [
  { value: 'revenue', label: 'Ingresos por cliente', ranged: true },
  { value: 'routes', label: 'Rutas mas utilizadas', ranged: true },
  { value: 'vehicles', label: 'Viajes por unidad', ranged: true },
  { value: 'fuel', label: 'Rendimiento de combustible', ranged: true },
  { value: 'profitability', label: 'Rentabilidad por viaje', ranged: true },
  { value: 'receivables', label: 'Saldos por cobrar', ranged: false },
  { value: 'licenses', label: 'Licencias por vencer', ranged: false },
  { value: 'maintenance', label: 'Mantenimiento proximo', ranged: false },
]

const today = new Date().toISOString().slice(0, 10)
const yearStart = `${new Date().getFullYear()}-01-01`

const kind = ref<ReportKind>('revenue')
const from = ref(yearStart)
const to = ref(today)
const report = ref<Report | null>(null)
const loading = ref(false)

const selectedKind = computed(() => kinds.find((option) => option.value === kind.value))

async function generate() {
  loading.value = true
  report.value = null
  try {
    report.value = await reportsApi.run(kind.value, from.value, to.value)
  } catch (failure) {
    toast.error(problemsOf(failure).join(' '))
  } finally {
    loading.value = false
  }
}

function display(value: string | number | null): string {
  if (value === null || value === undefined) {
    return ''
  }
  if (typeof value === 'number') {
    return new Intl.NumberFormat('es-MX', { maximumFractionDigits: 2 }).format(value)
  }
  return value
}

function isDateHeader(header: string): boolean {
  return header.includes('fecha') || header.includes('venc')
}

async function exportCsv() {
  if (!report.value) {
    return
  }
  try {
    const csv = await reportsApi.csv(kind.value, from.value, to.value)
    const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${report.value.title.replaceAll(' ', '_')}.csv`
    link.click()
    URL.revokeObjectURL(url)
  } catch (failure) {
    toast.error(problemsOf(failure).join(' '))
  }
}
</script>

<template>
  <div class="flex flex-col gap-6">
    <PageHeader title="Reportes" description="Reportes de operacion y su exportacion a CSV">
      <Button variant="outline" size="sm" :disabled="!report" @click="exportCsv">
        <Download />
        Exportar CSV
      </Button>
    </PageHeader>

    <form class="flex flex-wrap items-end gap-2" @submit.prevent="generate">
      <Select v-model="kind">
        <SelectTrigger class="w-64"><SelectValue /></SelectTrigger>
        <SelectContent>
          <SelectItem v-for="option in kinds" :key="option.value" :value="option.value">
            {{ option.label }}
          </SelectItem>
        </SelectContent>
      </Select>
      <Input v-if="selectedKind?.ranged" v-model="from" type="date" class="w-40" />
      <Input v-if="selectedKind?.ranged" v-model="to" type="date" class="w-40" />
      <Button type="submit" size="sm" :disabled="loading">
        <Loader2 v-if="loading" class="animate-spin" />
        <FileBarChart v-else />
        Generar
      </Button>
    </form>

    <div v-if="report" class="rounded-lg border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead v-for="header in report.headers" :key="header">{{ header }}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-if="!report.rows.length">
            <TableCell :colspan="report.headers.length" class="py-8 text-center text-sm text-muted-foreground">
              Sin datos para el periodo
            </TableCell>
          </TableRow>
          <TableRow v-for="(row, index) in report.rows" :key="index">
            <TableCell v-for="(cell, column) in row" :key="column" class="tabular-nums">
              {{ isDateHeader(report.headers[column]) ? formatDate(cell as string) : display(cell) }}
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>
    <p v-else class="text-sm text-muted-foreground">Seleccione un reporte y presione Generar.</p>
  </div>
</template>
