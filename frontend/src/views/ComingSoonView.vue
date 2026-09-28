<script setup lang="ts">
import { useRoute } from 'vue-router'
import { Construction } from '@lucide/vue'

const route = useRoute()
</script>

<template>
  <div class="grid h-full place-items-center py-24 text-center">
    <div class="flex flex-col items-center gap-3 text-muted-foreground">
      <Construction class="size-8" />
      <h1 class="text-xl font-semibold tracking-tight text-foreground">
        {{ route.meta.title }}
      </h1>
      <p class="text-sm">Esta pantalla se esta construyendo.</p>
    </div>
  </div>
</template>
