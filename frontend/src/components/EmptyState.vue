<template>
  <div class="flex flex-col items-center justify-center py-10 px-4 text-center">
    <div class="w-14 h-14 rounded-2xl bg-indigo-50 text-primary flex items-center justify-center mb-3">
      <slot name="icon">
        <Inbox class="w-7 h-7" />
      </slot>
    </div>
    <div class="text-sm font-medium text-gray-700 mb-1">{{ title }}</div>
    <div v-if="description" class="text-xs text-gray-400 max-w-sm leading-relaxed">{{ description }}</div>
    <div v-if="$slots.action" class="mt-4 flex flex-wrap justify-center gap-2">
      <slot name="action" />
    </div>
  </div>
</template>

<script setup>
import { Inbox } from '@lucide/vue'

defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' }
})
</script>
