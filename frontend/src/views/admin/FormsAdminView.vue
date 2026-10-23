<script setup lang="ts">
import { useRouter } from 'vue-router'
import { forms } from '@/api'
import EmptyState from '@/components/EmptyState.vue'
import ErrorAlert from '@/components/ErrorAlert.vue'
import LoadingState from '@/components/LoadingState.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useLoad } from '@/composables/useLoad'
import { dateTime, label } from '@/format'

const router = useRouter()
const { data, loading, error } = useLoad(() => forms.list())
</script>

<template>
  <PageHeader title="Forms" subtitle="Word templates people download filled in with their own details.">
    <template #actions>
      <RouterLink to="/admin/forms/new" class="button primary">New form</RouterLink>
    </template>
  </PageHeader>
  <div class="card">
    <ErrorAlert :error="error" style="margin: 16px" />
    <LoadingState v-if="loading" />
    <EmptyState v-else-if="data && data.length === 0" title="No forms" text="Upload a .docx with ${placeholders} to publish one." />
    <div v-else-if="data" class="table-wrap">
      <table class="table">
        <thead>
          <tr><th>Title</th><th>For</th><th class="numeric">Placeholders</th><th class="numeric">Extra fields</th><th>Status</th><th>Updated</th></tr>
        </thead>
        <tbody>
          <tr v-for="form in data" :key="form.id" class="clickable" @click="router.push(`/admin/forms/${form.id}`)">
            <td><RouterLink :to="`/admin/forms/${form.id}`" @click.stop>{{ form.title }}</RouterLink></td>
            <td>{{ label(form.audience) }}</td>
            <td class="numeric">{{ form.placeholders.length }}</td>
            <td class="numeric">{{ form.fields.length }}</td>
            <td><span class="badge" :class="form.active ? 'success' : 'neutral'">{{ form.active ? 'Published' : 'Hidden' }}</span></td>
            <td class="nowrap muted">{{ dateTime(form.updatedAt) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
