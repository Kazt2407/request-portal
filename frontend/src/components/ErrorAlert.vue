<script setup lang="ts">
import { computed } from 'vue'
import { ApiError } from '@/api/http'
import { describe } from '@/errors'

/** Shows an error; field messages not shown next to a field (see `shown`) are listed here. */
const props = defineProps<{ error: unknown; shown?: string[] }>()

const message = computed(() => describe(props.error))
const extra = computed(() => {
  if (!(props.error instanceof ApiError)) {
    return []
  }
  const fields = Object.entries(props.error.fieldErrors)
    .filter(([path]) => !props.shown?.includes(path))
    .map(([path, text]) => `${path}: ${text}`)
  const failures = Object.entries(props.error.failures).map(([request, reason]) => `${request}: ${reason}`)
  return [...fields, ...failures]
})
const hasFieldErrors = computed(
  () => props.error instanceof ApiError && Object.keys(props.error.fieldErrors).length > 0,
)
</script>

<template>
  <div v-if="error" class="alert danger" role="alert">
    <template v-if="hasFieldErrors">Please correct the highlighted fields.</template>
    <template v-else>{{ message }}</template>
    <ul v-if="extra.length">
      <li v-for="line in extra" :key="line">{{ line }}</li>
    </ul>
  </div>
</template>
