<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { departments, files, requests, saveFile, templates } from '@/api'
import type { BulkDownloadJob, BulkDownloadPreview, BulkResult, RequestFilter } from '@/api/types'
import { useAuth } from '@/auth'
import EmptyState from '@/components/EmptyState.vue'
import ErrorAlert from '@/components/ErrorAlert.vue'
import LoadingState from '@/components/LoadingState.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import PageHeader from '@/components/PageHeader.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { useAction, useLoad } from '@/composables/useLoad'
import { useToast } from '@/composables/useToast'
import { describe } from '@/errors'
import { bytes, dateTime, due } from '@/format'

const { user, hasRole } = useAuth()
const isAdmin = hasRole('ADMIN')
const router = useRouter()
const { show } = useToast()

// Staff start with what waits for their department; admins with everything in progress.
const filter = reactive<Required<RequestFilter>>({
  q: '',
  templateId: null,
  status: 'IN_PROGRESS',
  awaitingMe: !isAdmin,
  resultReceived: null,
  submittedFrom: '',
  submittedTo: '',
  departmentCode: '',
})
const page = ref(0)
const selected = ref(new Set<number>())

const options = useLoad(async () => {
  const [templateList, departmentList] = await Promise.all([templates.list(), departments.list()])
  return { templates: templateList, departments: departmentList }
})
const { data, loading, error, reload } = useLoad(() =>
  requests.search(filter, { page: page.value, size: 25, sort: 'submittedAt,desc' }),
)

let searchTimer: ReturnType<typeof setTimeout> | undefined
watch(
  () => ({ ...filter }),
  (next, previous) => {
    clearTimeout(searchTimer)
    // Typing in the search box waits for a pause; other filters apply at once.
    searchTimer = setTimeout(
      () => {
        page.value = 0
        selected.value = new Set()
        void reload()
      },
      next.q !== previous.q ? 300 : 0,
    )
  },
)
watch(page, () => void reload())
onBeforeUnmount(() => clearTimeout(searchTimer))

const items = computed(() => data.value?.items ?? [])
const allSelected = computed(() => items.value.length > 0 && items.value.every((item) => selected.value.has(item.id)))

function toggle(id: number): void {
  const next = new Set(selected.value)
  if (!next.delete(id)) {
    next.add(id)
  }
  selected.value = next
}

function toggleAll(): void {
  selected.value = allSelected.value ? new Set() : new Set(items.value.map((item) => item.id))
}

// Bulk actions: all succeed or none do; the error lists every request that failed and why.
const bulk = useAction()
const bulkDialog = ref<'complete' | 'reject' | null>(null)
const bulkText = ref('')
// A dialog acts on the requests selected when it opened, even if a late filter change clears the selection.
let actingOn: number[] = []

function openBulk(kind: 'complete' | 'reject'): void {
  actingOn = [...selected.value]
  bulk.error.value = null
  bulkText.value = ''
  bulkDialog.value = kind
}

async function runBulk(change: () => Promise<BulkResult>, verb: string): Promise<void> {
  const result = await bulk.run(change)
  if (result) {
    show(`${verb} ${result.count} request${result.count === 1 ? '' : 's'}: ${result.codes.join(', ')}`)
    bulkDialog.value = null
    selected.value = new Set()
    await reload()
  }
}

const completeSelected = () => runBulk(() => requests.bulkComplete(actingOn, bulkText.value), 'Completed the current step of')
const rejectSelected = () => runBulk(() => requests.bulkReject(actingOn, bulkText.value), 'Rejected')
async function markCollected(): Promise<void> {
  const ids = [...selected.value]
  await runBulk(() => requests.bulkResultReceived(ids, true), 'Marked as collected')
  if (bulk.error.value) {
    show(describe(bulk.error.value), 'danger')
  }
}

const exporting = useAction()
async function exportExcel(): Promise<void> {
  const file = await exporting.run(() => requests.export(filter))
  if (file) {
    saveFile(file)
  } else {
    show(describe(exporting.error.value), 'danger')
  }
}

// Bulk ZIP download: preview, start a background job, poll until the archive is ready.
const zipOpen = ref(false)
const zip = useAction()
const preview = ref<BulkDownloadPreview>()
const job = ref<BulkDownloadJob>()
let pollTimer: ReturnType<typeof setTimeout> | undefined

async function openZip(): Promise<void> {
  actingOn = [...selected.value]
  preview.value = undefined
  job.value = undefined
  zipOpen.value = true
  preview.value = await zip.run(() => files.previewBulk(actingOn))
}

async function startZip(): Promise<void> {
  job.value = await zip.run(() => files.startBulk(actingOn))
  poll()
}

function poll(): void {
  clearTimeout(pollTimer)
  const current = job.value
  if (!current || !zipOpen.value || current.status === 'READY' || current.status === 'FAILED') {
    return
  }
  pollTimer = setTimeout(async () => {
    job.value = await zip.run(() => files.bulkJob(current.id))
    poll()
  }, 1500)
}

watch(zipOpen, (open) => (open ? poll() : clearTimeout(pollTimer)))
onBeforeUnmount(() => clearTimeout(pollTimer))
</script>

<template>
  <PageHeader title="Requests" :subtitle="isAdmin ? 'All requests across departments.' : `Requests involving ${user.department}.`">
    <template #actions>
      <button class="button" :disabled="exporting.busy.value" @click="exportExcel">Export to Excel</button>
    </template>
  </PageHeader>

  <div class="card">
    <div class="filters">
      <input v-model="filter.q" class="input search" type="search" placeholder="Search by code, name or email" aria-label="Search" />
      <select v-model="filter.status" class="select" aria-label="Status">
        <option value="">Any status</option>
        <option value="IN_PROGRESS">In progress</option>
        <option value="COMPLETED">Completed</option>
        <option value="REJECTED">Rejected</option>
        <option value="CANCELLED">Cancelled</option>
      </select>
      <select v-model="filter.templateId" class="select" aria-label="Request type">
        <option :value="null">Any request type</option>
        <option v-for="template in options.data.value?.templates" :key="template.id" :value="template.id">{{ template.title }}</option>
      </select>
      <select v-if="isAdmin" v-model="filter.departmentCode" class="select" aria-label="Department">
        <option value="">Any department</option>
        <option v-for="department in options.data.value?.departments" :key="department.code" :value="department.code">
          {{ department.name }}
        </option>
      </select>
      <select v-model="filter.resultReceived" class="select" aria-label="Collected">
        <option :value="null">Collected or not</option>
        <option :value="false">Not collected</option>
        <option :value="true">Collected</option>
      </select>
      <span class="dates">
        <span class="muted small">Submitted</span>
        <input v-model="filter.submittedFrom" class="input" type="date" aria-label="Submitted from" />
        <span class="muted">–</span>
        <input v-model="filter.submittedTo" class="input" type="date" aria-label="Submitted to" />
      </span>
      <label v-if="user.department" class="checkbox"><input v-model="filter.awaitingMe" type="checkbox" /> Waiting for {{ user.department }}</label>
    </div>

    <div v-if="selected.size" class="selection row">
      <strong>{{ selected.size }} selected</strong>
      <div class="spacer"></div>
      <button class="button small" @click="openZip">Download files</button>
      <button class="button small" @click="markCollected">Mark as collected</button>
      <template v-if="hasRole('STAFF')">
        <button class="button small danger" @click="openBulk('reject')">Reject</button>
        <button class="button small primary" @click="openBulk('complete')">Complete current step</button>
      </template>
    </div>

    <ErrorAlert :error="error" style="margin: 16px" />
    <LoadingState v-if="loading && !data" />
    <EmptyState v-else-if="items.length === 0 && !loading" title="Nothing matches" text="Try other filters, or clear the search." />
    <div v-else class="table-wrap" :class="{ refreshing: loading }">
      <table class="table">
        <thead>
          <tr>
            <th class="check"><input type="checkbox" :checked="allSelected" aria-label="Select all" @change="toggleAll" /></th>
            <th>Code</th>
            <th>Request</th>
            <th>Requester</th>
            <th>Current step</th>
            <th>Status</th>
            <th>Submitted</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="request in items"
            :key="request.id"
            class="clickable"
            :class="{ selected: selected.has(request.id) }"
            @click="router.push(`/requests/${request.id}`)"
          >
            <td class="check" @click.stop>
              <input type="checkbox" :checked="selected.has(request.id)" :aria-label="`Select ${request.code}`" @change="toggle(request.id)" />
            </td>
            <td class="mono nowrap"><RouterLink :to="`/requests/${request.id}`" @click.stop>{{ request.code }}</RouterLink></td>
            <td>{{ request.title }}</td>
            <td>{{ request.requester.name }}<div class="muted small">{{ request.requester.email }}</div></td>
            <td>
              <template v-if="request.currentStep">
                {{ request.currentStep.departmentCode }} · step {{ request.currentStep.position }}
                <div class="small" :class="request.currentStep.overdue ? 'overdue' : 'muted'">{{ due(request.currentStep.dueAt) }}</div>
              </template>
              <span v-else-if="request.status === 'COMPLETED'" class="muted">{{ request.resultReceived ? 'Collected' : 'Not collected' }}</span>
              <span v-else class="muted">—</span>
            </td>
            <td><StatusBadge :status="request.status" :overdue="request.currentStep?.overdue" /></td>
            <td class="nowrap muted">{{ dateTime(request.submittedAt) }}</td>
          </tr>
        </tbody>
      </table>
      <PaginationBar v-if="data" :page="data.page" :total-pages="data.totalPages" :total-items="data.totalItems" @change="page = $event" />
    </div>
  </div>

  <ModalDialog :open="bulkDialog !== null" :title="bulkDialog === 'complete' ? `Complete the current step of ${actingOn.length} requests` : `Reject ${actingOn.length} requests`" @update:open="bulkDialog = null">
    <ErrorAlert :error="bulk.error.value" />
    <div class="field">
      <label for="bulk-text" :class="{ required: bulkDialog === 'reject' }">{{ bulkDialog === 'complete' ? 'Note (optional)' : 'Reason' }}</label>
      <textarea id="bulk-text" v-model="bulkText" class="textarea" maxlength="2000"></textarea>
    </div>
    <p class="muted small">All selected requests change together, or none do. Steps that need an attachment must be completed one by one.</p>
    <template #footer>
      <button class="button" @click="bulkDialog = null">Back</button>
      <button v-if="bulkDialog === 'complete'" class="button primary" :disabled="bulk.busy.value" @click="completeSelected">Complete</button>
      <button v-else class="button danger solid" :disabled="bulk.busy.value || !bulkText.trim()" @click="rejectSelected">Reject</button>
    </template>
  </ModalDialog>

  <ModalDialog v-model:open="zipOpen" title="Download files as a ZIP">
    <ErrorAlert :error="zip.error.value" />
    <LoadingState v-if="!preview && !zip.error.value" />
    <template v-if="preview && !job">
      <dl class="details">
        <dt>Requests</dt>
        <dd>{{ preview.visibleRequests }} of {{ preview.requests }} selected<span v-if="preview.visibleRequests < preview.requests" class="muted"> (you cannot see the others)</span></dd>
        <dt>Files</dt>
        <dd>{{ preview.files }}</dd>
        <dt>Total size</dt>
        <dd>{{ bytes(preview.totalBytes) }}</dd>
      </dl>
      <p v-if="preview.problem" class="alert warning">{{ preview.problem }}</p>
      <p v-else class="muted small">The archive is built in the background, with a folder per request. You can close this window; the download stays available for 3 days.</p>
    </template>
    <template v-if="job">
      <div class="row"><StatusBadge :status="job.status" /> <span v-if="job.status === 'PENDING' || job.status === 'RUNNING'" class="spinner"></span></div>
      <p v-if="job.status === 'READY'">{{ job.files }} files, {{ bytes(job.totalBytes) }}. Available until {{ dateTime(job.expiresAt) }}.</p>
      <p v-else-if="job.status === 'FAILED'" class="field-error">{{ job.error }}</p>
      <p v-else class="muted">Building the archive…</p>
    </template>
    <template #footer>
      <button class="button" @click="zipOpen = false">Close</button>
      <a v-if="job?.status === 'READY' && job.downloadUrl" class="button primary" :href="job.downloadUrl">Download ZIP</a>
      <button v-else-if="!job" class="button primary" :disabled="!preview || !!preview.problem || zip.busy.value" @click="startZip">Build ZIP</button>
    </template>
  </ModalDialog>
</template>

<style scoped>
.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
}

.filters .select,
.filters .input {
  width: auto;
}

.filters .search {
  flex: 1 1 240px;
}

.dates {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.selection {
  padding: 10px 16px;
  border-bottom: 1px solid var(--border);
  background: var(--accent-soft);
}

.overdue {
  color: var(--warning);
  font-weight: 600;
}

.refreshing {
  opacity: 0.6;
  transition: opacity 0.15s;
}
</style>
