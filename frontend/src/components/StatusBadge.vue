<script setup lang="ts">
import { computed } from 'vue'
import { label } from '@/format'

const props = defineProps<{ status: string; overdue?: boolean }>()

const KINDS: Record<string, string> = {
  IN_PROGRESS: 'info',
  PENDING: 'neutral',
  RUNNING: 'info',
  COMPLETED: 'success',
  READY: 'success',
  SENT: 'success',
  ACTIVE: 'success',
  REJECTED: 'danger',
  FAILED: 'danger',
  CANCELLED: 'neutral',
  EXPIRED: 'neutral',
  SKIPPED: 'neutral',
  INACTIVE: 'neutral',
}

const kind = computed(() => (props.overdue ? 'warning' : (KINDS[props.status] ?? 'neutral')))
const text = computed(() => (props.overdue ? 'Overdue' : label(props.status)))
</script>

<template>
  <span class="badge" :class="kind">{{ text }}</span>
</template>
