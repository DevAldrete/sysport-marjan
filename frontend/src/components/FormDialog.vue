<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import type { ZodTypeAny } from 'zod'

import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import type { Option } from '@/lib/enums'

/**
 * Generic form dialog, the Vue counterpart of the Swing FormPanel/ModalForm:
 * fields declared as data, validated with a zod schema, and the server's
 * problems shown as a list so the dialog stays open until it succeeds.
 */
export interface FormField {
  key: string
  label: string
  type?: 'text' | 'password' | 'number' | 'money' | 'date' | 'datetime' | 'textarea' | 'select' | 'checkbox'
  options?: Option[]
  placeholder?: string
  hint?: string
  disabled?: boolean
  full?: boolean
}

const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    fields: FormField[]
    initial?: Record<string, unknown>
    schema?: ZodTypeAny
    problems?: string[]
    submitting?: boolean
    submitLabel?: string
  }>(),
  { submitLabel: 'Guardar' },
)

const emit = defineEmits<{
  'update:open': [value: boolean]
  submit: [values: Record<string, unknown>]
  change: [values: Record<string, unknown>]
}>()

const values = reactive<Record<string, any>>({})
const errors = ref<Record<string, string>>({})

watch(values, () => emit('change', { ...values }), { deep: true })

function blank(field: FormField): unknown {
  switch (field.type) {
    case 'checkbox':
      return false
    case 'select':
      return field.options?.[0]?.value ?? ''
    case 'number':
    case 'money':
      return null
    default:
      return ''
  }
}

watch(
  [() => props.open, () => props.initial],
  () => {
    if (!props.open) {
      return
    }
    errors.value = {}
    for (const key of Object.keys(values)) {
      delete values[key]
    }
    for (const field of props.fields) {
      const value = props.initial?.[field.key]
      values[field.key] = value === undefined || value === null ? blank(field) : value
    }
  },
  { immediate: true, deep: true },
)

function submit() {
  errors.value = {}
  if (props.schema) {
    const parsed = props.schema.safeParse({ ...values })
    if (!parsed.success) {
      const found: Record<string, string> = {}
      for (const issue of parsed.error.issues) {
        found[String(issue.path[0] ?? '')] = issue.message
      }
      errors.value = found
      return
    }
    emit('submit', parsed.data as Record<string, unknown>)
    return
  }
  emit('submit', { ...values })
}
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent class="sm:max-w-2xl">
      <DialogHeader>
        <DialogTitle>{{ title }}</DialogTitle>
      </DialogHeader>

      <form class="flex flex-col gap-4" @submit.prevent="submit">
        <ul v-if="problems?.length" class="list-disc rounded-md bg-destructive/10 py-2 pl-8 pr-3 text-sm text-destructive">
          <li v-for="problem in problems" :key="problem">{{ problem }}</li>
        </ul>

        <div class="grid gap-4 sm:grid-cols-2">
          <div
            v-for="field in fields"
            :key="field.key"
            class="flex flex-col gap-2"
            :class="{ 'sm:col-span-2': field.full || field.type === 'textarea' }"
          >
            <Label :for="field.key">{{ field.label }}</Label>

            <Select v-if="field.type === 'select'" v-model="values[field.key]">
              <SelectTrigger :id="field.key" class="w-full" :disabled="field.disabled">
                <SelectValue :placeholder="field.placeholder ?? 'Seleccione'" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem v-for="option in field.options" :key="option.value" :value="option.value">
                  {{ option.label }}
                </SelectItem>
              </SelectContent>
            </Select>

            <div v-else-if="field.type === 'checkbox'" class="flex h-8 items-center">
              <Checkbox
                :id="field.key"
                :model-value="!!values[field.key]"
                :disabled="field.disabled"
                @update:model-value="values[field.key] = $event"
              />
            </div>

            <Textarea
              v-else-if="field.type === 'textarea'"
              :id="field.key"
              v-model="values[field.key]"
              :placeholder="field.placeholder"
              :disabled="field.disabled"
            />

            <Input
              v-else
              :id="field.key"
              v-model="values[field.key]"
              :type="field.type === 'datetime' ? 'datetime-local' : field.type === 'money' ? 'number' : field.type ?? 'text'"
              :step="field.type === 'money' ? '0.01' : field.type === 'number' ? 'any' : undefined"
              :placeholder="field.placeholder"
              :disabled="field.disabled"
            />

            <p v-if="errors[field.key]" class="text-xs text-destructive">{{ errors[field.key] }}</p>
            <p v-else-if="field.hint" class="text-xs text-muted-foreground">{{ field.hint }}</p>
          </div>
        </div>

        <slot />

        <DialogFooter>
          <Button type="button" variant="outline" @click="emit('update:open', false)">Cancelar</Button>
          <Button type="submit" :disabled="submitting">
            {{ submitting ? 'Guardando...' : submitLabel }}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>
