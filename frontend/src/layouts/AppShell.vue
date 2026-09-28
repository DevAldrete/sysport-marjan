<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { ChevronsUpDown, KeyRound, LogOut } from '@lucide/vue'
import { motion } from 'motion-v'
import { toast } from 'vue-sonner'
import { z } from 'zod'

import { problemsOf } from '@/api/errors'
import FormDialog from '@/components/FormDialog.vue'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { features } from '@/features'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const visibleFeatures = computed(() =>
  features.filter((feature) => !feature.permission || auth.can(feature.permission)),
)
const username = computed(() => auth.user?.username ?? '')
const roleName = computed(() => auth.user?.roleName ?? '')
const initials = computed(() => username.value.slice(0, 2).toUpperCase() || '?')

function logout() {
  auth.clear()
  router.replace({ name: 'login' })
}

const passwordOpen = ref(false)
const passwordProblems = ref<string[]>([])
const passwordSubmitting = ref(false)

const passwordFields = [
  { key: 'currentPassword', label: 'Contrasena actual', type: 'password' as const, full: true },
  { key: 'newPassword', label: 'Nueva contrasena', type: 'password' as const, full: true },
]

const passwordSchema = z.object({
  currentPassword: z.string().min(1, 'Indique su contrasena actual'),
  newPassword: z.string().min(6, 'Minimo 6 caracteres'),
})

function openPassword() {
  passwordProblems.value = []
  passwordOpen.value = true
}

async function savePassword(values: Record<string, unknown>) {
  passwordProblems.value = []
  passwordSubmitting.value = true
  try {
    await auth.changePassword(values.currentPassword as string, values.newPassword as string)
    toast.success('Contrasena actualizada')
    passwordOpen.value = false
  } catch (failure) {
    passwordProblems.value = problemsOf(failure)
  } finally {
    passwordSubmitting.value = false
  }
}
</script>

<template>
  <div class="flex min-h-svh bg-muted/30">
    <aside class="flex w-60 shrink-0 flex-col border-r bg-sidebar text-sidebar-foreground">
      <div class="flex h-14 items-center gap-2 px-4 text-lg font-semibold tracking-tight">
        SysPort
        <span class="text-xs font-normal text-muted-foreground">MARJAN</span>
      </div>

      <nav class="flex flex-1 flex-col gap-1 px-2 py-2">
        <RouterLink
          v-for="feature in visibleFeatures"
          :key="feature.name"
          :to="{ name: feature.name }"
          class="flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-sidebar-accent hover:text-sidebar-accent-foreground"
          active-class="bg-sidebar-accent text-sidebar-accent-foreground"
        >
          <component :is="feature.icon" class="size-4 shrink-0" />
          {{ feature.title }}
        </RouterLink>
      </nav>
    </aside>

    <div class="flex min-w-0 flex-1 flex-col">
      <header
        class="sticky top-0 z-10 flex h-14 items-center justify-end gap-3 border-b bg-background/80 px-4 backdrop-blur"
      >
        <DropdownMenu>
          <DropdownMenuTrigger class="flex items-center gap-2 rounded-md px-2 py-1.5 outline-none hover:bg-muted">
            <Avatar class="size-7">
              <AvatarFallback class="text-xs">{{ initials }}</AvatarFallback>
            </Avatar>
            <span class="flex flex-col items-start leading-tight">
              <span class="text-sm font-medium">{{ username }}</span>
              <span class="text-xs text-muted-foreground">{{ roleName }}</span>
            </span>
            <ChevronsUpDown class="size-4 text-muted-foreground" />
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" class="w-52">
            <DropdownMenuLabel>Mi sesion</DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem @select="openPassword">
              <KeyRound class="size-4" />
              Cambiar contrasena
            </DropdownMenuItem>
            <DropdownMenuItem class="text-destructive" @select="logout">
              <LogOut class="size-4" />
              Cerrar sesion
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </header>

      <main class="flex-1 p-6">
        <RouterView v-slot="{ Component, route }">
          <motion.div
            :key="route.path"
            :initial="{ opacity: 0, y: 8 }"
            :animate="{ opacity: 1, y: 0 }"
            :transition="{ duration: 0.18, ease: 'easeOut' }"
          >
            <component :is="Component" />
          </motion.div>
        </RouterView>
      </main>
    </div>

    <FormDialog
      v-model:open="passwordOpen"
      title="Cambiar contrasena"
      :fields="passwordFields"
      :schema="passwordSchema"
      :problems="passwordProblems"
      :submitting="passwordSubmitting"
      @submit="savePassword"
    />
  </div>
</template>
