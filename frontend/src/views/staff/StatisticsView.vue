<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { departments, requests, saveFile, templates } from '@/api'
import type { StatisticsFilter } from '@/api/types'
import { useAuth } from '@/auth'
import EmptyState from '@/components/EmptyState.vue'
import ErrorAlert from '@/components/ErrorAlert.vue'
import LoadingState from '@/components/LoadingState.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useAction, useLoad } from '@/composables/useLoad'
import { useToast } from '@/composables/useToast'
import { describe } from '@/errors'
import { decimal, percent } from '@/format'

const { user, hasRole } = useAuth()
const isAdmin = hasRole('ADMIN')
const { show } = useToast()

const filter = reactive<Required<StatisticsFilter>>({ submittedFrom: '', submittedTo: '', departmentCode: '', templateId: null })
const options = useLoad(async () => {
  const [templateList, departmentList] = await Promise.all([templates.list(), departments.list()])
  return { templates: templateList, departments: departmentList }
})
const { data, loading, error, reload } = useLoad(() => requests.statistics(filter))
watch(() => ({ ...filter }), () => void reload())

const totals = computed(() => {
  const rows = data.value?.byTemplate ?? []
  const sum = (pick: (row: (typeof rows)[number]) => number) => rows.reduce((total, row) => total + pick(row), 0)
  return {
    total: sum((row) => row.total),
    inProgress: sum((row) => row.inProgress),
    completed: sum((row) => row.completed),
    overdue: sum((row) => row.overdue),
  }
})

const exporting = useAction()
async function exportExcel(): Promise<void> {
  const file = await exporting.run(() => requests.exportStatistics(filter))
  if (file) {
    saveFile(file)
  } else {
    show(describe(exporting.error.value), 'danger')
  }
}

/** Share of the bar for an outcome, for the small stacked bars. */
function share(part: number, whole: number): string {
  return whole === 0 ? '0%' : `${(part / whole) * 100}%`
}
</script>

<template>
  <PageHeader title="Statistics" :subtitle="isAdmin ? 'Outcomes across all departments.' : `Outcomes for ${user.department}.`">
    <template #actions>
      <button class="button" :disabled="exporting.busy.value" @click="exportExcel">Export to Excel</button>
    </template>
  </PageHeader>

  <div class="stack">
    <div class="card filters">
      <label class="field"><span class="field-label">Submitted from</span><input v-model="filter.submittedFrom" class="input" type="date" /></label>
      <label class="field"><span class="field-label">to</span><input v-model="filter.submittedTo" class="input" type="date" /></label>
      <label class="field">
        <span class="field-label">Request type</span>
        <select v-model="filter.templateId" class="select">
          <option :value="null">All</option>
          <option v-for="template in options.data.value?.templates" :key="template.id" :value="template.id">{{ template.title }}</option>
        </select>
      </label>
      <label v-if="isAdmin" class="field">
        <span class="field-label">Department</span>
        <select v-model="filter.departmentCode" class="select">
          <option value="">All</option>
          <option v-for="department in options.data.value?.departments" :key="department.code" :value="department.code">{{ department.name }}</option>
        </select>
      </label>
    </div>

    <ErrorAlert :error="error" />
    <LoadingState v-if="loading && !data" />
    <template v-else-if="data">
      <div class="tiles">
        <div class="card tile"><span class="muted">Requests</span><strong>{{ totals.total }}</strong></div>
        <div class="card tile"><span class="muted">In progress</span><strong>{{ totals.inProgress }}</strong></div>
        <div class="card tile"><span class="muted">Completed</span><strong>{{ totals.completed }}</strong></div>
        <div class="card tile"><span class="muted">Overdue now</span><strong :class="{ warn: totals.overdue > 0 }">{{ totals.overdue }}</strong></div>
      </div>

      <section class="card">
        <div class="card-header"><h2>By request type</h2></div>
        <EmptyState v-if="data.byTemplate.length === 0" title="No requests in this period" />
        <div v-else class="table-wrap">
          <table class="table">
            <thead>
              <tr>
                <th>Request type</th>
                <th>Outcomes</th>
                <th class="numeric">Total</th>
                <th class="numeric">In progress</th>
                <th class="numeric">Completed</th>
                <th class="numeric">Rejected</th>
                <th class="numeric">Cancelled</th>
                <th class="numeric">Overdue</th>
                <th class="numeric">Avg. days</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in data.byTemplate" :key="row.templateId">
                <td>{{ row.title }}</td>
                <td>
                  <div class="bar" :title="`${row.completed} completed, ${row.inProgress} in progress, ${row.rejected} rejected, ${row.cancelled} cancelled`">
                    <span class="completed" :style="{ width: share(row.completed, row.total) }"></span>
                    <span class="progress" :style="{ width: share(row.inProgress, row.total) }"></span>
                    <span class="rejected" :style="{ width: share(row.rejected, row.total) }"></span>
                    <span class="cancelled" :style="{ width: share(row.cancelled, row.total) }"></span>
                  </div>
                </td>
                <td class="numeric">{{ row.total }}</td>
                <td class="numeric">{{ row.inProgress }}</td>
                <td class="numeric">{{ row.completed }}</td>
                <td class="numeric">{{ row.rejected }}</td>
                <td class="numeric">{{ row.cancelled }}</td>
                <td class="numeric">{{ row.overdue }}</td>
                <td class="numeric">{{ decimal(row.averageDaysToComplete) }}</td>
              </tr>
            </tbody>
          </table>
          <p class="legend small muted">
            <span><i class="completed"></i>Completed</span><span><i class="progress"></i>In progress</span>
            <span><i class="rejected"></i>Rejected</span><span><i class="cancelled"></i>Cancelled</span>
          </p>
        </div>
      </section>

      <section class="card">
        <div class="card-header"><h2>By department</h2></div>
        <EmptyState v-if="data.byDepartment.length === 0" title="No steps in this period" />
        <div v-else class="table-wrap">
          <table class="table">
            <thead>
              <tr>
                <th>Department</th>
                <th class="numeric">In progress</th>
                <th class="numeric">Overdue</th>
                <th class="numeric">Completed</th>
                <th class="numeric">Rejected</th>
                <th class="numeric">On time</th>
                <th class="numeric">Avg. days per step</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in data.byDepartment" :key="row.departmentCode">
                <td class="mono">{{ row.departmentCode }}</td>
                <td class="numeric">{{ row.inProgress }}</td>
                <td class="numeric">{{ row.overdue }}</td>
                <td class="numeric">{{ row.completed }}</td>
                <td class="numeric">{{ row.rejected }}</td>
                <td class="numeric">{{ percent(row.onTimeRate) }}</td>
                <td class="numeric">{{ decimal(row.averageDaysPerStep) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 14px 16px;
}

.filters .field {
  flex: 0 1 200px;
}

.tiles {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 16px;
}

.tile {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 16px 18px;
}

.tile strong {
  font-size: 26px;
  font-variant-numeric: tabular-nums;
}

.tile .warn {
  color: var(--warning);
}

.bar {
  display: flex;
  width: 140px;
  height: 8px;
  overflow: hidden;
  border-radius: 4px;
  background: var(--surface-sunken);
}

.completed {
  background: var(--success);
}

.progress {
  background: var(--info);
}

.rejected {
  background: var(--danger);
}

.cancelled {
  background: var(--border-strong);
}

.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  padding: 10px 16px;
  border-top: 1px solid var(--border);
}

.legend i {
  display: inline-block;
  width: 10px;
  height: 10px;
  margin-right: 6px;
  border-radius: 2px;
  vertical-align: -1px;
}
</style>
