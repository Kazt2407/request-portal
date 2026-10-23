<script setup lang="ts">
import { computed, ref } from 'vue'
import { notifications, requests, saveFile } from '@/api'
import { ApiError } from '@/api/http'
import { activeUploads } from '@/api/upload'
import type { Notification, RequestDetail } from '@/api/types'
import { useAuth } from '@/auth'
import ErrorAlert from '@/components/ErrorAlert.vue'
import FileList from '@/components/FileList.vue'
import FileUploader from '@/components/FileUploader.vue'
import HistoryList from '@/components/HistoryList.vue'
import LoadingState from '@/components/LoadingState.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import StepTimeline from '@/components/StepTimeline.vue'
import { useAction, useLoad } from '@/composables/useLoad'
import { useToast } from '@/composables/useToast'
import { describe } from '@/errors'
import { dateTime, label } from '@/format'

const props = defineProps<{ id: number }>()
const { user, hasRole } = useAuth()
const { show } = useToast()

const { data: request, loading, error, reload } = useLoad(() => requests.get(props.id))
const emails = useLoad(() => notifications.forRequest(props.id), { immediate: hasRole('ADMIN') })

const current = computed(() => request.value?.steps.find((step) => step.position === request.value?.currentStep))
const lastStep = computed(() => request.value?.steps.at(-1))
const isRequester = computed(() => request.value?.requester.id === user.value.id)
const canCancel = computed(
  () =>
    isRequester.value &&
    request.value?.status === 'IN_PROGRESS' &&
    !request.value.steps.some((step) => step.status === 'COMPLETED'),
)
const canProcess = computed(
  () =>
    request.value?.status === 'IN_PROGRESS' &&
    hasRole('STAFF') &&
    current.value?.departmentCode === user.value.department,
)
const canRecordResult = computed(
  () =>
    request.value?.status === 'COMPLETED' &&
    hasRole('STAFF') &&
    lastStep.value?.departmentCode === user.value.department,
)

const action = useAction()
const completing = ref(false)
const rejecting = ref(false)
const cancelling = ref(false)
const note = ref('')
const reason = ref('')
const attachmentIds = ref<string[]>([])

function open(dialog: 'complete' | 'reject' | 'cancel'): void {
  action.error.value = null
  note.value = ''
  reason.value = ''
  attachmentIds.value = []
  completing.value = dialog === 'complete'
  rejecting.value = dialog === 'reject'
  cancelling.value = dialog === 'cancel'
}

/** Applies a workflow action; if someone else acted first (409), shows the latest state. */
async function act(change: () => Promise<RequestDetail>, done: string): Promise<void> {
  const updated = await action.run(change)
  if (updated) {
    request.value = updated
    completing.value = rejecting.value = cancelling.value = false
    show(done)
    if (hasRole('ADMIN')) {
      setTimeout(() => void emails.reload(), 1500)
    }
  } else if (action.error.value instanceof ApiError && action.error.value.status === 409) {
    completing.value = rejecting.value = cancelling.value = false
    show(describe(action.error.value), 'danger')
    await reload()
  }
}

const complete = () =>
  act(() => requests.complete(props.id, current.value!.position, note.value, attachmentIds.value), 'Step completed')
const reject = () => act(() => requests.reject(props.id, current.value!.position, reason.value), 'Request rejected')
const cancel = () => act(() => requests.cancel(props.id), 'Request cancelled')
const recordResult = (received: boolean) =>
  act(() => requests.resultReceived(props.id, received), received ? 'Marked as collected' : 'Marked as not collected')

const downloading = useAction()
async function downloadDocument(): Promise<void> {
  const file = await downloading.run(() => requests.document(props.id))
  if (file) {
    saveFile(file)
  } else {
    show(describe(downloading.error.value), 'danger')
  }
}

async function retry(notification: Notification): Promise<void> {
  try {
    await notifications.retry(notification.id)
    show('Email queued again')
    await emails.reload()
  } catch (caught) {
    show(describe(caught), 'danger')
  }
}

function isFiles(value: unknown): value is string[] {
  return Array.isArray(value)
}
</script>

<template>
  <LoadingState v-if="loading && !request" />
  <ErrorAlert v-else-if="error" :error="error" />
  <template v-else-if="request">
    <PageHeader :title="request.title" :back="hasRole('STAFF', 'ADMIN') ? { label: 'Requests', to: '/queue' } : { label: 'My requests', to: '/my-requests' }">
      <template #subtitle>
        <span class="row">
          <span class="mono">{{ request.code }}</span>
          <StatusBadge :status="request.status" :overdue="current?.overdue" />
          <span v-if="request.status === 'COMPLETED'" class="badge plain" :class="request.resultReceived ? 'success' : 'neutral'">
            {{ request.resultReceived ? 'Collected' : 'Not collected yet' }}
          </span>
        </span>
      </template>
      <template #actions>
        <button class="button" :disabled="downloading.busy.value" @click="downloadDocument">Download as Word</button>
        <button v-if="canCancel" class="button danger" @click="open('cancel')">Cancel request</button>
        <template v-if="canProcess">
          <button class="button danger" @click="open('reject')">Reject</button>
          <button class="button primary" @click="open('complete')">Complete step {{ current?.position }}</button>
        </template>
        <template v-if="canRecordResult">
          <button v-if="!request.resultReceived" class="button primary" @click="recordResult(true)">Mark as collected</button>
          <button v-else class="button" @click="recordResult(false)">Mark as not collected</button>
        </template>
      </template>
    </PageHeader>

    <div class="layout">
      <div class="stack">
        <section class="card">
          <div class="card-header"><h2>Details</h2></div>
          <div class="card-body">
            <dl class="details">
              <dt>Requester</dt>
              <dd>{{ request.requester.name }} <span class="muted">· {{ request.requester.email }}</span></dd>
              <dt>Submitted</dt>
              <dd>{{ dateTime(request.submittedAt) }}</dd>
              <template v-if="request.finishedAt">
                <dt>Finished</dt>
                <dd>{{ dateTime(request.finishedAt) }}</dd>
              </template>
              <template v-for="value in request.values" :key="value.key">
                <dt>{{ value.label }}</dt>
                <dd>
                  <FileList v-if="isFiles(value.value)" :ids="value.value" />
                  <span v-else class="pre-line">{{ value.value || '—' }}</span>
                </dd>
              </template>
            </dl>
          </div>
        </section>

        <section class="card">
          <div class="card-header"><h2>History</h2></div>
          <div class="card-body"><HistoryList :entries="request.history" /></div>
        </section>

        <section v-if="hasRole('ADMIN')" class="card">
          <div class="card-header">
            <h2>Emails</h2>
            <button class="button small" @click="emails.reload()">Refresh</button>
          </div>
          <ErrorAlert :error="emails.error.value" style="margin: 16px" />
          <div v-if="emails.data.value?.items.length" class="table-wrap">
            <table class="table">
              <thead>
                <tr><th>Email</th><th>Status</th><th>Sent</th><th></th></tr>
              </thead>
              <tbody>
                <tr v-for="email in emails.data.value.items" :key="email.id">
                  <td>
                    {{ email.subject }}
                    <div class="muted small">{{ label(email.kind) }} · to {{ email.recipient.replace('department:', 'the department mailbox of ') }}</div>
                  </td>
                  <td>
                    <StatusBadge :status="email.status" />
                    <div v-if="email.lastError" class="field-error">{{ email.lastError }}</div>
                  </td>
                  <td class="nowrap muted small">{{ dateTime(email.sentAt) }}</td>
                  <td>
                    <button v-if="email.status === 'FAILED' || email.status === 'SKIPPED'" class="button small" @click="retry(email)">Retry</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-else-if="!emails.loading.value" class="card-body muted">No emails yet.</p>
        </section>
      </div>

      <section class="card">
        <div class="card-header"><h2>Steps</h2></div>
        <div class="card-body"><StepTimeline :steps="request.steps" /></div>
      </section>
    </div>

    <ModalDialog v-model:open="completing" :title="`Complete step ${current?.position}: ${current?.description}`">
      <ErrorAlert :error="action.error.value" :shown="['attachmentIds', 'note']" />
      <div class="field">
        <label for="note">Note <span class="muted">(optional, the requester sees it)</span></label>
        <textarea id="note" v-model="note" class="textarea" maxlength="2000"></textarea>
      </div>
      <div class="field">
        <span class="field-label" :class="{ required: current?.requiresAttachment }">Attachments</span>
        <p v-if="current?.requiresAttachment" class="field-help">This step needs at least one file, such as a signed scan.</p>
        <FileUploader v-model="attachmentIds" purpose="STEP_ATTACHMENT" :invalid="!!action.error.value" />
      </div>
      <template #footer>
        <button class="button" @click="completing = false">Back</button>
        <button
          class="button primary"
          :disabled="action.busy.value || activeUploads > 0 || (current?.requiresAttachment && attachmentIds.length === 0)"
          @click="complete"
        >
          Complete step
        </button>
      </template>
    </ModalDialog>

    <ModalDialog v-model:open="rejecting" title="Reject this request">
      <ErrorAlert :error="action.error.value" :shown="['reason']" />
      <div class="field">
        <label for="reason" class="required">Reason</label>
        <textarea id="reason" v-model="reason" class="textarea" maxlength="2000" placeholder="The requester sees this in the email."></textarea>
      </div>
      <p class="muted small">The request ends here; later steps are cancelled.</p>
      <template #footer>
        <button class="button" @click="rejecting = false">Back</button>
        <button class="button danger solid" :disabled="action.busy.value || !reason.trim()" @click="reject">Reject request</button>
      </template>
    </ModalDialog>

    <ModalDialog v-model:open="cancelling" title="Cancel this request?">
      <ErrorAlert :error="action.error.value" />
      <p>The departments stop working on it. You can submit a new request later.</p>
      <template #footer>
        <button class="button" @click="cancelling = false">Keep it</button>
        <button class="button danger solid" :disabled="action.busy.value" @click="cancel">Cancel request</button>
      </template>
    </ModalDialog>
  </template>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 380px);
  align-items: start;
  gap: 20px;
}

@media (max-width: 1000px) {
  .layout {
    grid-template-columns: 1fr;
  }
}
</style>
