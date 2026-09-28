<script setup lang="ts">
import { computed, reactive } from 'vue'
import { Plus, X } from '@lucide/vue'

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
import { packageUnits } from '@/lib/enums'

export interface PackageLine {
  id: number
  description: string
  quantity: number | null
  unit: string | null
  unitWeight: number | null
}

const props = defineProps<{ modelValue: PackageLine[] }>()
const emit = defineEmits<{ 'update:modelValue': [value: PackageLine[]] }>()

const draft = reactive({
  description: '',
  quantity: undefined as number | undefined,
  unit: 'caja',
  unitWeight: undefined as number | undefined,
})

const total = computed(() =>
  props.modelValue.reduce(
    (sum, line) => sum + (Number(line.quantity) || 0) * (Number(line.unitWeight) || 0),
    0,
  ),
)

function add() {
  const description = draft.description.trim()
  if (!description) {
    return
  }
  emit('update:modelValue', [
    ...props.modelValue,
    {
      id: 0,
      description,
      quantity: draft.quantity ?? null,
      unit: draft.unit,
      unitWeight: draft.unitWeight ?? null,
    },
  ])
  draft.description = ''
  draft.quantity = undefined
  draft.unitWeight = undefined
}

function remove(index: number) {
  const next = [...props.modelValue]
  next.splice(index, 1)
  emit('update:modelValue', next)
}
</script>

<template>
  <div class="rounded-lg border">
    <div class="flex items-center justify-between border-b px-3 py-2">
      <span class="text-sm font-medium">Paquetes</span>
      <span class="text-xs text-muted-foreground">
        {{ modelValue.length }} linea(s) - Peso total: {{ total.toLocaleString('es-MX') }} kg
      </span>
    </div>

    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Descripcion</TableHead>
          <TableHead class="w-20 text-right">Cantidad</TableHead>
          <TableHead class="w-28">Unidad</TableHead>
          <TableHead class="w-24 text-right">Peso/u</TableHead>
          <TableHead class="w-20" />
        </TableRow>
      </TableHeader>
      <TableBody>
        <TableRow v-if="!modelValue.length">
          <TableCell colspan="5" class="py-4 text-center text-xs text-muted-foreground">
            Sin paquetes capturados
          </TableCell>
        </TableRow>
        <TableRow v-for="(line, index) in modelValue" :key="index">
          <TableCell>{{ line.description }}</TableCell>
          <TableCell class="text-right tabular-nums">{{ line.quantity ?? '' }}</TableCell>
          <TableCell>
            {{ packageUnits.find((unit) => unit.value === line.unit)?.label ?? line.unit }}
          </TableCell>
          <TableCell class="text-right tabular-nums">{{ line.unitWeight ?? '' }}</TableCell>
          <TableCell class="text-right">
            <Button type="button" variant="ghost" size="icon-xs" title="Quitar" @click="remove(index)">
              <X />
            </Button>
          </TableCell>
        </TableRow>
      </TableBody>
    </Table>

    <div class="grid grid-cols-[1fr_5rem_8rem_6rem_auto] items-center gap-2 border-t p-2">
      <Input v-model="draft.description" placeholder="Descripcion" />
      <Input v-model="draft.quantity" type="number" step="any" placeholder="Cant." />
      <Select v-model="draft.unit">
        <SelectTrigger class="w-full">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem v-for="unit in packageUnits" :key="unit.value" :value="unit.value">
            {{ unit.label }}
          </SelectItem>
        </SelectContent>
      </Select>
      <Input v-model="draft.unitWeight" type="number" step="any" placeholder="Peso/u" />
      <Button type="button" variant="outline" size="sm" @click="add">
        <Plus />
      </Button>
    </div>
  </div>
</template>
