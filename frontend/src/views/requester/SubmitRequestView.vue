<script setup lang="ts">
import { computed, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { requests, templates, terms } from '@/api'
import { activeUploads } from '@/api/upload'
import DynamicField from '@/components/DynamicField.vue'
import ErrorAlert from '@/components/ErrorAlert.vue'
import LoadingState from '@/components/LoadingState.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useAction, useLoad } from '@/composables/useLoad'
import { useToast } from '@/composables/useToast'
import { fieldError } from '@/errors'

const props = defineProps<{ templateId: number }>()
const router = useRouter()
const { show } = useToast()

const { data, loading, error } = useLoad(async () => {
  const [template, termList, schoolYears] = await Promise.all([
    templates.get(props.templateId),
    terms.list(),
    terms.schoolYears(),
  ])
  for (const field of template.fields) {
    values[field.key] = field.type === 'FILE' || field.type === 'IMAGE' ? [] : ''
  }
  return { template, termList, schoolYears }
})

const values = reactive<Record<string, string | string[]>>({})
// One key per visit: a double click or a retry after a timeout returns the same request.
const idempotencyKey = crypto.randomUUID()
const submission = useAction()
const fieldKeys = computed(() => data.value?.template.fields.map((field) => field.key) ?? [])

async function submit(): Promise<void> {
  const filled = Object.fromEntries(
    Object.entries(values).filter(([, value]) => (Array.isArray(value) ? value.length > 0 : value.trim() !== '')),
  )
  const request = await submission.run(() => requests.submit(props.templateId, filled, idempotencyKey))
  if (request) {
    show(`Request ${request.code} submitted`)
    await router.push(`/requests/${request.id}`)
  }
}
</script>

<template>
  <LoadingState v-if="loading" />
  <ErrorAlert v-else-if="error" :error="error" />
  <template v-else-if="data">
    <PageHeader :title="data.template.title" :back="{ label: 'All request types', to: '/new' }">
      <template #subtitle>{{ data.template.description }}</template>
    </PageHeader>

    <div class="layout">
      <form class="card card-body stack" novalidate @submit.prevent="submit">
        <ErrorAlert :error="submission.error.value" :shown="fieldKeys" />
        <DynamicField
          v-for="field in data.template.fields"
          :key="field.key"
          v-model="values[field.key]"
          :field="field"
          :terms="data.termList"
          :school-years="data.schoolYears"
          :error="fieldError(submission.error.value, field.key)"
        />
        <p v-if="data.template.fields.length === 0" class="muted">This request needs no details; just submit it.</p>
        <div class="row">
          <button type="submit" class="button primary" :disabled="submission.busy.value || activeUploads > 0">
            {{ submission.busy.value ? 'Submitting…' : activeUploads > 0 ? 'Uploading files…' : 'Submit request' }}
          </button>
          <RouterLink to="/new" class="button ghost">Cancel</RouterLink>
        </div>
      </form>

      <aside class="card card-body stack-small steps">
        <h2>What happens next</h2>
        <ol>
          <li v-for="step in data.template.steps" :key="step.position">
            <strong>{{ step.departmentCode }}</strong> — {{ step.description }}
            <span class="muted small">({{ step.slaDays }} day{{ step.slaDays === 1 ? '' : 's' }})</span>
          </li>
        </ol>
        <p class="muted small">You get an email at each stage, and when the result is ready.</p>
      </aside>
    </div>
  </template>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  align-items: start;
  gap: 20px;
}

@media (max-width: 960px) {
  .layout {
    grid-template-columns: 1fr;
  }
}

.steps ol {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding-left: 18px;
}
</style>
