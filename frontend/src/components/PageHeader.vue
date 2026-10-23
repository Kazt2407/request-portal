<script setup lang="ts">
defineProps<{ title: string; subtitle?: string; back?: { label: string; to: string } }>()
</script>

<template>
  <header class="page-header">
    <RouterLink v-if="back" :to="back.to" class="back small">← {{ back.label }}</RouterLink>
    <div class="row">
      <div class="titles">
        <h1>{{ title }}</h1>
        <p v-if="subtitle || $slots.subtitle" class="muted"><slot name="subtitle">{{ subtitle }}</slot></p>
      </div>
      <div class="spacer"></div>
      <div class="row"><slot name="actions" /></div>
    </div>
  </header>
</template>

<style scoped>
.page-header {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 20px;
}

.titles {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.back {
  color: var(--text-muted);
  width: fit-content;
}
</style>
