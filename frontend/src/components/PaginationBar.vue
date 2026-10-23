<script setup lang="ts">
const props = defineProps<{ page: number; totalPages: number; totalItems: number }>()
const emit = defineEmits<{ change: [page: number] }>()
</script>

<template>
  <div v-if="props.totalItems > 0" class="pagination row small muted">
    <span>{{ props.totalItems }} {{ props.totalItems === 1 ? 'result' : 'results' }}</span>
    <div class="spacer"></div>
    <button class="button small" :disabled="props.page === 0" @click="emit('change', props.page - 1)">Previous</button>
    <span>Page {{ props.page + 1 }} of {{ Math.max(props.totalPages, 1) }}</span>
    <button class="button small" :disabled="props.page + 1 >= props.totalPages" @click="emit('change', props.page + 1)">
      Next
    </button>
  </div>
</template>

<style scoped>
.pagination {
  padding: 12px 16px;
  border-top: 1px solid var(--border);
}
</style>
