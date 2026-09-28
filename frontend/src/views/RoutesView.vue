<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { Pencil, Plus, RefreshCw, Search, Trash2 } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { z } from 'zod'

import { problemsOf } from '@/api/errors'
import { routesApi } from '@/api/routes'
import type { Route } from '@/api/types'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormDialog, { type FormField } from '@/components/FormDialog.vue'
import PageHeader from '@/components/PageHeader.vue'
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
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const queryClient = useQueryClient()
const canWrite = computed(() => auth.can('routes.write'))

const searchInput = ref('')
const term = ref('')

const { data: routes, isFetching, isError, error } = useQuery({
  queryKey: computed(() => ['routes', term.value]),
  queryFn: () => routesApi.search(term.value),
})

function applySearch() {
  term.value = searchInput.value.trim()
}

const fields: FormField[] = [
  { key: 'origin', label: 'Origen' },
  { key: 'destination', label: 'Destino' },
  { key: 'estimatedKm', label: 'Km estimados', type: 'number' },
  { key: 'description', label: 'Descripcion', type: 'textarea' },
]

const schema = z.object({
  origin: z.string().min(1, 'El origen es obligatorio'),
  destination: z.string().min(1, 'El destino es obligatorio'),
  estimatedKm: z.coerce.number().min(0).default(0),
  description: z.string().default(''),
})

const formOpen = ref(false)
const editing = ref<Route | null>(null)
const formProblems = ref<string[]>([])
const submitting = ref(false)

function openCreate() {
  editing.value = null
  formProblems.value = []
  formOpen.value = true
}

function openEdit(route: Route) {
  editing.value = route
  formProblems.value = []
  formOpen.value = true
}

async function save(values: Record<string, unknown>) {
  formProblems.value = []
  submitting.value = true
  try {
    if (editing.value) {
      await routesApi.update(editing.value.id, values)
    } else {
      await routesApi.create(values)
    }
    toast.success('Ruta guardada')
    formOpen.value = false
    await queryClient.invalidateQueries({ queryKey: ['routes'] })
  } catch (failure) {
    formProblems.value = problemsOf(failure)
  } finally {
    submitting.value = false
  }
}

const deleteTarget = ref<Route | null>(null)
const confirmOpen = ref(false)

async function confirmDelete() {
  if (!deleteTarget.value) {
    return
  }
  try {
    await routesApi.remove(deleteTarget.value.id)
    toast.success('Ruta eliminada')
  } catch (failure) {
    toast.error(problemsOf(failure).join(' '))
  } finally {
    confirmOpen.value = false
    await queryClient.invalidateQueries({ queryKey: ['routes'] })
  }
}
</script>

<template>
  <div class="flex flex-col gap-6">
    <PageHeader title="Rutas" description="Trayectos origen-destino">
      <Button variant="outline" size="sm" :disabled="isFetching" @click="queryClient.invalidateQueries({ queryKey: ['routes'] })">
        <RefreshCw :class="{ 'animate-spin': isFetching }" />
        Recargar
      </Button>
      <Button v-if="canWrite" size="sm" @click="openCreate">
        <Plus />
        Nueva
      </Button>
    </PageHeader>

    <form class="flex items-center gap-2" @submit.prevent="applySearch">
      <Input v-model="searchInput" placeholder="Buscar por origen o destino" class="max-w-xs" />
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
            <TableHead>Origen</TableHead>
            <TableHead>Destino</TableHead>
            <TableHead class="text-right">Km estimados</TableHead>
            <TableHead>Descripcion</TableHead>
            <TableHead class="text-right">Acciones</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-if="!routes?.length">
            <TableCell colspan="5" class="py-8 text-center text-sm text-muted-foreground">
              {{ isFetching ? 'Cargando...' : 'Sin resultados' }}
            </TableCell>
          </TableRow>
          <TableRow v-for="route in routes" :key="route.id" @dblclick="canWrite && openEdit(route)">
            <TableCell class="font-medium">{{ route.origin }}</TableCell>
            <TableCell>{{ route.destination }}</TableCell>
            <TableCell class="text-right tabular-nums">{{ route.estimatedKm }}</TableCell>
            <TableCell class="max-w-md truncate">{{ route.description }}</TableCell>
            <TableCell>
              <div class="flex justify-end gap-1">
                <Button v-if="canWrite" variant="ghost" size="icon-sm" title="Editar" @click="openEdit(route)">
                  <Pencil />
                </Button>
                <Button
                  v-if="canWrite"
                  variant="ghost"
                  size="icon-sm"
                  title="Eliminar"
                  @click="((deleteTarget = route), (confirmOpen = true))"
                >
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
      :title="editing ? 'Editar ruta' : 'Nueva ruta'"
      :fields="fields"
      :initial="editing ?? { estimatedKm: null }"
      :schema="schema"
      :problems="formProblems"
      :submitting="submitting"
      @submit="save"
    />

    <ConfirmDialog
      v-model:open="confirmOpen"
      title="Eliminar ruta"
      :description="deleteTarget ? `Se eliminara ${deleteTarget.origin} -> ${deleteTarget.destination}.` : ''"
      @confirm="confirmDelete"
    />
  </div>
</template>
