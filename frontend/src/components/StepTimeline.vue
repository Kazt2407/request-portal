<script setup lang="ts">
import type { RequestStep } from '@/api/types'
import { dateTime, due } from '@/format'
import FileList from './FileList.vue'
import StatusBadge from './StatusBadge.vue'

defineProps<{ steps: RequestStep[] }>()
</script>

<template>
  <ol class="timeline">
    <li v-for="step in steps" :key="step.position" :class="step.overdue ? 'OVERDUE' : step.status">
      <span class="marker" aria-hidden="true">{{ step.status === 'COMPLETED' ? '✓' : step.status === 'REJECTED' ? '✕' : step.position }}</span>
      <div class="content">
        <div class="row">
          <strong>{{ step.description }}</strong>
          <StatusBadge :status="step.status" :overdue="step.overdue" />
        </div>
        <p class="muted small">
          {{ step.departmentCode }} · {{ step.slaDays }} day{{ step.slaDays === 1 ? '' : 's' }}
          <template v-if="step.requiresAttachment"> · needs an attachment</template>
          <template v-if="step.status === 'IN_PROGRESS' && step.dueAt"> · due {{ dateTime(step.dueAt) }} ({{ due(step.dueAt) }})</template>
        </p>
        <p v-if="step.finishedAt" class="small">
          {{ step.status === 'REJECTED' ? 'Rejected' : step.status === 'CANCELLED' ? 'Cancelled' : 'Completed' }}
          {{ dateTime(step.finishedAt) }}<template v-if="step.finishedBy"> by {{ step.finishedBy.name }}</template>
        </p>
        <p v-if="step.note" class="note pre-line">{{ step.note }}</p>
        <FileList v-if="step.attachmentIds.length" :ids="step.attachmentIds" />
      </div>
    </li>
  </ol>
</template>

<style scoped>
.timeline {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
}

.timeline li {
  position: relative;
  display: flex;
  gap: 14px;
  padding-bottom: 20px;
}

.timeline li:not(:last-child)::before {
  content: '';
  position: absolute;
  top: 28px;
  bottom: 0;
  left: 13px;
  width: 2px;
  background: var(--border);
}

.marker {
  display: grid;
  flex: none;
  place-items: center;
  width: 28px;
  height: 28px;
  border: 2px solid var(--border-strong);
  border-radius: 50%;
  background: var(--surface);
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 700;
}

.COMPLETED .marker {
  border-color: var(--success);
  background: var(--success);
  color: #fff;
}

.IN_PROGRESS .marker {
  border-color: var(--info);
  color: var(--info);
}

.OVERDUE .marker {
  border-color: var(--warning);
  color: var(--warning);
}

.REJECTED .marker {
  border-color: var(--danger);
  background: var(--danger);
  color: #fff;
}

.content {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  padding-top: 3px;
}

.note {
  padding: 8px 10px;
  border-radius: var(--radius-small);
  background: var(--surface-sunken);
}
</style>
