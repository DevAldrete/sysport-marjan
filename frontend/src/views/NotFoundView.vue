<script setup lang="ts">
import { RouterLink } from 'vue-router'

import { Button } from '@/components/ui/button'
</script>

<template>
  <div class="grid h-full place-items-center py-24 text-center">
    <div class="flex flex-col items-center gap-4">
      <p class="text-5xl font-semibold tracking-tight">404</p>
      <p class="text-sm text-muted-foreground">La pagina que busca no existe.</p>
      <Button as-child>
        <RouterLink to="/">Volver al inicio</RouterLink>
      </Button>
    </div>
  </div>
</template>
