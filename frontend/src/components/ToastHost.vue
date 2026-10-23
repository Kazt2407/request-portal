<script setup lang="ts">
import { useToast } from '@/composables/useToast'

const { toasts, dismiss } = useToast()
</script>

<template>
  <div class="toasts" aria-live="polite">
    <div v-for="toast in toasts" :key="toast.id" class="toast" :class="toast.kind" role="status">
      <span>{{ toast.message }}</span>
      <button class="button ghost small icon-button" aria-label="Dismiss" @click="dismiss(toast.id)">×</button>
    </div>
  </div>
</template>

<style scoped>
.toasts {
  position: fixed;
  right: 16px;
  bottom: 16px;
  z-index: 50;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: min(420px, calc(100vw - 32px));
}

.toast {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 10px 10px 14px;
  border: 1px solid var(--border);
  border-left: 4px solid var(--success);
  border-radius: var(--radius-small);
  background: var(--surface);
  box-shadow: var(--shadow-raised);
}

.toast span {
  flex: 1;
}

.toast.danger {
  border-left-color: var(--danger);
}

.toast.info {
  border-left-color: var(--info);
}
</style>
