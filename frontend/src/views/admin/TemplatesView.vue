<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { templates } from '@/api'
import type { TemplateStatus } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import ErrorAlert from '@/components/ErrorAlert.vue'
import LoadingState from '@/components/LoadingState.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { useLoad } from '@/composables/useLoad'
import { dateTime, label } from '@/format'

const router = useRouter()
const status = ref<TemplateStatus | ''>('')
const { data, loading, error, reload } = useLoad(() => templates.list(status.value))
watch(status, () => void reload())
</script>

<template>
  <PageHeader title="Request templates" subtitle="What people can request: the form they fill in and the departments that process it.">
    <template #actions>
      <select v-model="status" class="select" aria-label="Status" style="width: auto">
        <option value="">All</option>
        <option value="ACTIVE">Active</option>
        <option value="INACTIVE">Inactive</option>
      </select>
      <RouterLink to="/admin/templates/new" class="button primary">New template</RouterLink>
    </template>
  </PageHeader>
  <div class="card">
    <ErrorAlert :error="error" style="margin: 16px" />
    <LoadingState v-if="loading && !data" />
    <EmptyState v-else-if="data && data.length === 0" title="No templates" text="Create one to let people submit requests." />
    <div v-else-if="data" class="table-wrap">
      <table class="table">
        <thead>
          <tr><th>Title</th><th>Department</th><th>For</th><th class="numeric">Fields</th><th class="numeric">Steps</th><th class="numeric">Days</th><th>Status</th><th>Updated</th></tr>
        </thead>
        <tbody>
          <tr v-for="template in data" :key="template.id" class="clickable" @click="router.push(`/admin/templates/${template.id}`)">
            <td><RouterLink :to="`/admin/templates/${template.id}`" @click.stop>{{ template.title }}</RouterLink></td>
            <td class="mono">{{ template.departmentCode }}</td>
            <td>{{ label(template.audience) }}</td>
            <td class="numeric">{{ template.fieldCount }}</td>
            <td class="numeric">{{ template.stepCount }}</td>
            <td class="numeric">{{ template.totalSlaDays }}</td>
            <td><StatusBadge :status="template.status" /></td>
            <td class="nowrap muted">{{ dateTime(template.updatedAt) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
