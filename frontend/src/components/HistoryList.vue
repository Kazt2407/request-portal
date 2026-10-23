<script setup lang="ts">
import type { HistoryEntry } from '@/api/types'
import { dateTime, label } from '@/format'

defineProps<{ entries: HistoryEntry[] }>()
</script>

<template>
  <ul class="history">
    <li v-for="(entry, index) in entries" :key="index">
      <span class="when muted small nowrap">{{ dateTime(entry.at) }}</span>
      <div>
        <strong>{{ label(entry.action) }}</strong>
        <template v-if="entry.stepPosition"> · step {{ entry.stepPosition }}</template>
        <span class="muted"> by {{ entry.actor.name }}</span>
        <p v-if="entry.note" class="muted pre-line">{{ entry.note }}</p>
      </div>
    </li>
  </ul>
</template>

<style scoped>
.history {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.history li {
  display: grid;
  grid-template-columns: 130px 1fr;
  gap: 12px;
}

@media (max-width: 560px) {
  .history li {
    grid-template-columns: 1fr;
    gap: 0;
  }
}

.when {
  padding-top: 1px;
}
</style>
