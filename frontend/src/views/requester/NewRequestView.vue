<script setup lang="ts">
import { templates } from '@/api'
import EmptyState from '@/components/EmptyState.vue'
import ErrorAlert from '@/components/ErrorAlert.vue'
import LoadingState from '@/components/LoadingState.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useLoad } from '@/composables/useLoad'

const { data, loading, error } = useLoad(() => templates.available())
</script>

<template>
  <PageHeader title="New request" subtitle="Choose what you need. Each request goes through the departments shown." />
  <ErrorAlert :error="error" />
  <LoadingState v-if="loading" />
  <EmptyState v-else-if="data && data.length === 0" title="Nothing to request yet" text="No request types are open for your role." />
  <div v-else class="cards">
    <RouterLink v-for="template in data" :key="template.id" :to="`/new/${template.id}`" class="card template">
      <h2>{{ template.title }}</h2>
      <p v-if="template.description" class="muted description">{{ template.description }}</p>
      <div class="row small muted meta">
        <span>{{ template.departmentCode }}</span>
        <span>·</span>
        <span>{{ template.stepCount }} step{{ template.stepCount === 1 ? '' : 's' }}</span>
        <span>·</span>
        <span>up to {{ template.totalSlaDays }} day{{ template.totalSlaDays === 1 ? '' : 's' }}</span>
      </div>
    </RouterLink>
  </div>
</template>

<style scoped>
.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}

.template {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 18px;
  color: var(--text);
  transition:
    border-color 0.12s,
    transform 0.12s;
}

.template:hover {
  border-color: var(--accent);
  text-decoration: none;
  transform: translateY(-1px);
}

.description {
  display: -webkit-box;
  flex: 1;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  line-clamp: 3;
}

.meta {
  gap: 6px;
}
</style>
