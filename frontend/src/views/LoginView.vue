<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { z } from 'zod'

import { problemsOf } from '@/api/errors'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const serverError = ref<string[]>([])

const { handleSubmit, defineField, errors, isSubmitting } = useForm({
  validationSchema: toTypedSchema(
    z.object({
      username: z.string().min(1, 'El usuario es obligatorio'),
      password: z.string().min(1, 'La contrasena es obligatoria'),
    }),
  ),
})

const [username, usernameProps] = defineField('username')
const [password, passwordProps] = defineField('password')

const onSubmit = handleSubmit(async (values) => {
  serverError.value = []
  try {
    await auth.login(values.username, values.password)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch (error) {
    serverError.value = problemsOf(error)
  }
})
</script>

<template>
  <div class="grid min-h-svh place-items-center bg-muted/40 p-4">
    <Card class="w-full max-w-sm">
      <CardHeader class="text-center">
        <CardTitle class="text-2xl tracking-tight">SysPort MARJAN</CardTitle>
        <CardDescription>Gestion de transportes</CardDescription>
      </CardHeader>
      <CardContent>
        <form class="flex flex-col gap-4" @submit="onSubmit">
          <div class="flex flex-col gap-2">
            <Label for="username">Usuario</Label>
            <Input
              id="username"
              v-model="username"
              v-bind="usernameProps"
              autocomplete="username"
              autofocus
            />
            <p v-if="errors.username" class="text-xs text-destructive">{{ errors.username }}</p>
          </div>

          <div class="flex flex-col gap-2">
            <Label for="password">Contrasena</Label>
            <Input
              id="password"
              v-model="password"
              v-bind="passwordProps"
              type="password"
              autocomplete="current-password"
            />
            <p v-if="errors.password" class="text-xs text-destructive">{{ errors.password }}</p>
          </div>

          <ul v-if="serverError.length" class="list-disc pl-4 text-xs text-destructive">
            <li v-for="problem in serverError" :key="problem">{{ problem }}</li>
          </ul>

          <Button type="submit" :disabled="isSubmitting" class="w-full">
            {{ isSubmitting ? 'Verificando...' : 'Entrar' }}
          </Button>
        </form>
      </CardContent>
    </Card>
  </div>
</template>
