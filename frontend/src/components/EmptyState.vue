<script setup lang="ts">
defineProps<{ title: string; text?: string }>()
</script>

<template>
  <div class="empty">
    <strong>{{ title }}</strong>
    <p v-if="text">{{ text }}</p>
    <div v-if="$slots.default" class="row" style="justify-content: center; margin-top: 14px"><slot /></div>
  </div>
</template>
