<script setup lang="ts">
import { computed, reactive } from 'vue'
import { forms, saveFile } from '@/api'
import ErrorAlert from '@/components/ErrorAlert.vue'
import LoadingState from '@/components/LoadingState.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useAction, useLoad } from '@/composables/useLoad'
import { useToast } from '@/composables/useToast'
import { fieldError } from '@/errors'

const props = defineProps<{ id: number }>()
const values = reactive<Record<string, string>>({})
const { show } = useToast()
const { data: form, loading, error } = useLoad(async () => {
  const loaded = await forms.get(props.id)
  loaded.fields.forEach((field) => (values[field.key] = ''))
  return loaded
})
const download = useAction()
const keys = computed(() => form.value?.fields.map((field) => field.key) ?? [])

/** Filled in automatically from the account and student profile. */
const AUTOMATIC: Record<string, string> = {
  full_name: 'your name',
  email: 'your email',
  student_code: 'your student code',
  date_of_birth: 'your date of birth',
  class_name: 'your class',
  faculty: 'your faculty',
  major: 'your major',
  today: "today's date",
}
const automatic = computed(() =>
  Object.entries(AUTOMATIC)
    .filter(([key]) => form.value?.placeholders.includes(key))
    .map(([, text]) => text),
)

async function generate(): Promise<void> {
  const file = await download.run(() => forms.fill(props.id, { ...values }))
  if (file) {
    saveFile(file)
    show(`${file.fileName} downloaded`)
  }
}
</script>

<template>
  <LoadingState v-if="loading" />
  <ErrorAlert v-else-if="error" :error="error" />
  <template v-else-if="form">
    <PageHeader :title="form.title" :subtitle="form.description ?? undefined" :back="{ label: 'All forms', to: '/forms' }" />
    <form class="card card-body stack narrow" novalidate @submit.prevent="generate">
      <p v-if="automatic.length" class="alert info">We fill in {{ automatic.join(', ') }} for you.</p>
      <ErrorAlert :error="download.error.value" :shown="keys" />
      <div v-for="field in form.fields" :key="field.key" class="field">
        <label :for="field.key" :class="{ required: field.required }">{{ field.label }}</label>
        <input
          :id="field.key"
          v-model="values[field.key]"
          class="input"
          :class="{ invalid: fieldError(download.error.value, field.key) }"
          :type="field.type === 'DATE' ? 'date' : 'text'"
          :inputmode="field.type === 'NUMBER' ? 'numeric' : undefined"
          :placeholder="field.hint ?? ''"
        />
        <p v-if="field.type === 'NUMBER'" class="field-help">Numbers are also written out in words on the form.</p>
        <p v-if="fieldError(download.error.value, field.key)" class="field-error">
          {{ field.label }} {{ fieldError(download.error.value, field.key) }}
        </p>
      </div>
      <div class="row">
        <button class="button primary" :disabled="download.busy.value">
          {{ download.busy.value ? 'Preparing…' : 'Download the filled-in form' }}
        </button>
      </div>
    </form>
  </template>
</template>

<style scoped>
.narrow {
  max-width: 640px;
}
</style>
