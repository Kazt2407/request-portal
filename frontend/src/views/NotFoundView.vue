<script setup lang="ts">
import EmptyState from '@/components/EmptyState.vue'
</script>

<template>
  <EmptyState title="Page not found" text="The address may be mistyped, or the page has moved.">
    <RouterLink to="/" class="button">Go to the start page</RouterLink>
  </EmptyState>
</template>
