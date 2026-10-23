<script setup lang="ts">
import { forms } from '@/api'
import EmptyState from '@/components/EmptyState.vue'
import ErrorAlert from '@/components/ErrorAlert.vue'
import LoadingState from '@/components/LoadingState.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useLoad } from '@/composables/useLoad'

const { data, loading, error } = useLoad(() => forms.available())
</script>

<template>
  <PageHeader title="Forms" subtitle="Word forms filled in with your details, ready to print and sign." />
  <ErrorAlert :error="error" />
  <LoadingState v-if="loading" />
  <EmptyState v-else-if="data && data.length === 0" title="No forms yet" text="Forms published by the university appear here." />
  <div v-else class="cards">
    <RouterLink v-for="form in data" :key="form.id" :to="`/forms/${form.id}`" class="card form">
      <span class="icon" aria-hidden="true">W</span>
      <div class="stack-small">
        <h2>{{ form.title }}</h2>
        <p v-if="form.description" class="muted">{{ form.description }}</p>
      </div>
    </RouterLink>
  </div>
</template>

<style scoped>
.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.form {
  display: flex;
  gap: 14px;
  padding: 18px;
  color: var(--text);
}

.form:hover {
  border-color: var(--accent);
  text-decoration: none;
}

.icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 36px;
  height: 44px;
  border-radius: 4px;
  background: #2b5797;
  color: #fff;
  font-weight: 800;
}
</style>
