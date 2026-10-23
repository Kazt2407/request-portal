<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { requests } from '@/api'
import type { RequestStatus } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import ErrorAlert from '@/components/ErrorAlert.vue'
import LoadingState from '@/components/LoadingState.vue'
import PageHeader from '@/components/PageHeader.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { useLoad } from '@/composables/useLoad'
import { dateTime, due } from '@/format'

const router = useRouter()
const status = ref<RequestStatus | ''>('')
const page = ref(0)
const tabs: { value: RequestStatus | ''; text: string }[] = [
  { value: '', text: 'All' },
  { value: 'IN_PROGRESS', text: 'In progress' },
  { value: 'COMPLETED', text: 'Completed' },
  { value: 'REJECTED', text: 'Rejected' },
  { value: 'CANCELLED', text: 'Cancelled' },
]

const { data, loading, error, reload } = useLoad(() =>
  requests.mine(status.value, { page: page.value, size: 20, sort: 'submittedAt,desc' }),
)
watch(status, () => {
  page.value = 0
  void reload()
})
watch(page, () => void reload())
</script>

<template>
  <PageHeader title="My requests" subtitle="Everything you have submitted, newest first.">
    <template #actions>
      <RouterLink to="/new" class="button primary">New request</RouterLink>
    </template>
  </PageHeader>

  <div class="card">
    <div class="tabs" role="tablist">
      <button
        v-for="tab in tabs"
        :key="tab.value"
        role="tab"
        class="tab"
        :aria-selected="status === tab.value"
        @click="status = tab.value"
      >
        {{ tab.text }}
      </button>
    </div>
    <ErrorAlert :error="error" style="margin: 16px" />
    <LoadingState v-if="loading && !data" />
    <EmptyState v-else-if="data && data.items.length === 0" title="No requests here" text="Requests you submit appear in this list.">
      <RouterLink to="/new" class="button">Make a request</RouterLink>
    </EmptyState>
    <div v-else-if="data" class="table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>Code</th>
            <th>Request</th>
            <th>Where it is</th>
            <th>Status</th>
            <th>Submitted</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="request in data.items" :key="request.id" class="clickable" @click="router.push(`/requests/${request.id}`)">
            <td class="mono nowrap">
              <RouterLink :to="`/requests/${request.id}`" @click.stop>{{ request.code }}</RouterLink>
            </td>
            <td>{{ request.title }}</td>
            <td>
              <template v-if="request.currentStep">
                {{ request.currentStep.description }}
                <span class="muted small">· {{ request.currentStep.departmentCode }}, {{ due(request.currentStep.dueAt) }}</span>
              </template>
              <span v-else-if="request.status === 'COMPLETED'" class="muted">
                {{ request.resultReceived ? 'Collected' : 'Ready to collect' }}
              </span>
              <span v-else class="muted">—</span>
            </td>
            <td><StatusBadge :status="request.status" :overdue="request.currentStep?.overdue" /></td>
            <td class="nowrap muted">{{ dateTime(request.submittedAt) }}</td>
          </tr>
        </tbody>
      </table>
      <PaginationBar :page="data.page" :total-pages="data.totalPages" :total-items="data.totalItems" @change="page = $event" />
    </div>
  </div>
</template>

<style scoped>
.tabs {
  display: flex;
  gap: 4px;
  padding: 8px 12px 0;
  border-bottom: 1px solid var(--border);
  overflow-x: auto;
}

.tab {
  padding: 8px 12px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: none;
  color: var(--text-muted);
  font: inherit;
  font-weight: 550;
  cursor: pointer;
  white-space: nowrap;
}

.tab[aria-selected='true'] {
  border-bottom-color: var(--accent);
  color: var(--accent);
}
</style>
