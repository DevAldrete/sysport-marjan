<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { KeyRound, Pencil, Plus, RefreshCw, Trash2 } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { z } from 'zod'

import { problemsOf } from '@/api/errors'
import type { UserAccount } from '@/api/types'
import { usersApi, type UserWrite } from '@/api/users'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormDialog, { type FormField } from '@/components/FormDialog.vue'
import PageHeader from '@/components/PageHeader.vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { labelOf, userStatuses } from '@/lib/enums'

const queryClient = useQueryClient()

const { data: users, isFetching, isError, error } = useQuery({
  queryKey: ['users'],
  queryFn: () => usersApi.list(),
})
const { data: roles } = useQuery({ queryKey: ['roles'], queryFn: () => usersApi.roles() })

const roleOptions = computed(() =>
  (roles.value ?? []).map((role) => ({ value: String(role.id), label: role.name })),
)

const formOpen = ref(false)
const editing = ref<UserAccount | null>(null)
const formProblems = ref<string[]>([])
const submitting = ref(false)

const fields = computed<FormField[]>(() => {
  const base: FormField[] = [{ key: 'username', label: 'Usuario', full: true }]
  if (!editing.value) {
    base.push({ key: 'password', label: 'Contrasena', type: 'password', full: true })
  }
  base.push({ key: 'roleId', label: 'Rol', type: 'select', options: roleOptions.value })
  base.push({ key: 'status', label: 'Estado', type: 'select', options: userStatuses })
  return base
})

const schema = z.object({
  username: z.string().min(3, 'El usuario debe tener al menos 3 caracteres'),
  password: z.string().optional(),
  roleId: z.coerce.number().int().positive('Seleccione un rol'),
  status: z.string().default('active'),
})

const initial = computed(() =>
  editing.value
    ? { username: editing.value.username, roleId: String(editing.value.roleId), status: editing.value.status }
    : { status: 'active', roleId: roleOptions.value[0]?.value ?? '' },
)

function openCreate() {
  editing.value = null
  formProblems.value = []
  formOpen.value = true
}

function openEdit(user: UserAccount) {
  editing.value = user
  formProblems.value = []
  formOpen.value = true
}

async function save(values: Record<string, unknown>) {
  formProblems.value = []
  submitting.value = true
  const body: UserWrite = {
    username: values.username as string,
    roleId: Number(values.roleId),
    employeeId: editing.value?.employeeId ?? null,
    status: values.status as string,
  }
  if (!editing.value) {
    body.password = (values.password as string) ?? ''
  }
  try {
    if (editing.value) {
      await usersApi.update(editing.value.id, body)
    } else {
      await usersApi.create(body)
    }
    toast.success('Usuario guardado')
    formOpen.value = false
    await queryClient.invalidateQueries({ queryKey: ['users'] })
  } catch (failure) {
    formProblems.value = problemsOf(failure)
  } finally {
    submitting.value = false
  }
}

const passwordOpen = ref(false)
const passwordTarget = ref<UserAccount | null>(null)
const passwordProblems = ref<string[]>([])
const passwordSubmitting = ref(false)

const passwordSchema = z.object({ password: z.string().min(6, 'Minimo 6 caracteres') })

function openPassword(user: UserAccount) {
  passwordTarget.value = user
  passwordProblems.value = []
  passwordOpen.value = true
}

async function savePassword(values: Record<string, unknown>) {
  if (!passwordTarget.value) {
    return
  }
  passwordProblems.value = []
  passwordSubmitting.value = true
  try {
    await usersApi.resetPassword(passwordTarget.value.id, values.password as string)
    toast.success('Contrasena actualizada')
    passwordOpen.value = false
  } catch (failure) {
    passwordProblems.value = problemsOf(failure)
  } finally {
    passwordSubmitting.value = false
  }
}

const deleteTarget = ref<UserAccount | null>(null)
const confirmOpen = ref(false)

async function confirmDelete() {
  if (!deleteTarget.value) {
    return
  }
  try {
    await usersApi.remove(deleteTarget.value.id)
    toast.success('Usuario eliminado')
  } catch (failure) {
    toast.error(problemsOf(failure).join(' '))
  } finally {
    confirmOpen.value = false
    await queryClient.invalidateQueries({ queryKey: ['users'] })
  }
}
</script>

<template>
  <div class="flex flex-col gap-6">
    <PageHeader title="Usuarios" description="Usuarios, roles y contrasenas">
      <Button variant="outline" size="sm" :disabled="isFetching" @click="queryClient.invalidateQueries({ queryKey: ['users'] })">
        <RefreshCw :class="{ 'animate-spin': isFetching }" />
        Recargar
      </Button>
      <Button size="sm" @click="openCreate">
        <Plus />
        Nuevo
      </Button>
    </PageHeader>

    <p v-if="isError" class="text-sm text-destructive">{{ problemsOf(error).join(' ') }}</p>

    <div class="rounded-lg border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Usuario</TableHead>
            <TableHead>Rol</TableHead>
            <TableHead>Estado</TableHead>
            <TableHead class="text-right">Acciones</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-if="!users?.length">
            <TableCell colspan="4" class="py-8 text-center text-sm text-muted-foreground">
              {{ isFetching ? 'Cargando...' : 'Sin resultados' }}
            </TableCell>
          </TableRow>
          <TableRow v-for="account in users" :key="account.id" @dblclick="openEdit(account)">
            <TableCell class="font-medium">{{ account.username }}</TableCell>
            <TableCell>{{ account.roleName }}</TableCell>
            <TableCell>
              <Badge :variant="account.status === 'active' ? 'secondary' : 'outline'">
                {{ labelOf(userStatuses, account.status) }}
              </Badge>
            </TableCell>
            <TableCell>
              <div class="flex justify-end gap-1">
                <Button variant="ghost" size="icon-sm" title="Restablecer contrasena" @click="openPassword(account)">
                  <KeyRound />
                </Button>
                <Button variant="ghost" size="icon-sm" title="Editar" @click="openEdit(account)">
                  <Pencil />
                </Button>
                <Button variant="ghost" size="icon-sm" title="Eliminar" @click="((deleteTarget = account), (confirmOpen = true))">
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
      :title="editing ? 'Editar usuario' : 'Nuevo usuario'"
      :fields="fields"
      :initial="initial"
      :schema="schema"
      :problems="formProblems"
      :submitting="submitting"
      @submit="save"
    />

    <FormDialog
      v-model:open="passwordOpen"
      :title="`Restablecer contrasena de ${passwordTarget?.username ?? ''}`"
      :fields="[{ key: 'password', label: 'Nueva contrasena', type: 'password', full: true }]"
      :schema="passwordSchema"
      :problems="passwordProblems"
      :submitting="passwordSubmitting"
      @submit="savePassword"
    />

    <ConfirmDialog
      v-model:open="confirmOpen"
      title="Eliminar usuario"
      :description="deleteTarget ? `Se eliminara ${deleteTarget.username}.` : ''"
      @confirm="confirmDelete"
    />
  </div>
</template>
