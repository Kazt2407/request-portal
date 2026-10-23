<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { terms } from '@/api'
import ErrorAlert from '@/components/ErrorAlert.vue'
import LoadingState from '@/components/LoadingState.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useAction, useLoad } from '@/composables/useLoad'
import { useToast } from '@/composables/useToast'
import { fieldError } from '@/errors'
import { date } from '@/format'

const { show } = useToast()
const schoolYear = ref('')
const years = useLoad(() => terms.schoolYears())
const { data, loading, error, reload } = useLoad(() => terms.list(schoolYear.value))
watch(schoolYear, () => void reload())

const adding = ref(false)
const form = reactive({ schoolYear: '', termNumber: 1, name: '', startsOn: '', endsOn: '' })
const save = useAction()

function open(): void {
  save.error.value = null
  Object.assign(form, { schoolYear: '', termNumber: 1, name: '', startsOn: '', endsOn: '' })
  adding.value = true
}

async function submit(): Promise<void> {
  const saved = await save.run(() => terms.create({ ...form, termNumber: Number(form.termNumber) }))
  if (saved) {
    show(`Added ${saved.name} ${saved.schoolYear}`)
    adding.value = false
    await Promise.all([reload(), years.reload()])
  }
}

const err = (path: string) => fieldError(save.error.value, path)
</script>

<template>
  <PageHeader title="Academic terms" subtitle="Terms and school years people pick on request forms.">
    <template #actions>
      <select v-model="schoolYear" class="select" aria-label="School year" style="width: auto">
        <option value="">All school years</option>
        <option v-for="year in years.data.value" :key="year" :value="year">{{ year }}</option>
      </select>
      <button class="button primary" @click="open">Add term</button>
    </template>
  </PageHeader>
  <div class="card">
    <ErrorAlert :error="error" style="margin: 16px" />
    <LoadingState v-if="loading && !data" />
    <div v-else-if="data" class="table-wrap">
      <table class="table">
        <thead><tr><th>Code</th><th>Name</th><th>School year</th><th>Starts</th><th>Ends</th></tr></thead>
        <tbody>
          <tr v-for="term in data" :key="term.id">
            <td class="mono">{{ term.code }}</td>
            <td>{{ term.name }}</td>
            <td>{{ term.schoolYear }}</td>
            <td>{{ date(term.startsOn) }}</td>
            <td>{{ date(term.endsOn) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>

  <ModalDialog v-model:open="adding" title="Add a term">
    <ErrorAlert :error="save.error.value" :shown="['schoolYear', 'termNumber', 'name', 'startsOn', 'endsOn']" />
    <div class="grid-2">
      <label class="field">
        <span class="field-label required">School year</span>
        <input v-model="form.schoolYear" class="input" :class="{ invalid: err('schoolYear') }" placeholder="2026-2027" />
        <span v-if="err('schoolYear')" class="field-error">School year {{ err('schoolYear') }}</span>
      </label>
      <label class="field">
        <span class="field-label required">Term</span>
        <select v-model.number="form.termNumber" class="select">
          <option :value="1">1</option>
          <option :value="2">2</option>
          <option :value="3">3 (summer)</option>
        </select>
      </label>
    </div>
    <label class="field">
      <span class="field-label required">Name</span>
      <input v-model="form.name" class="input" :class="{ invalid: err('name') }" placeholder="Semester 1" />
      <span v-if="err('name')" class="field-error">Name {{ err('name') }}</span>
    </label>
    <div class="grid-2">
      <label class="field">
        <span class="field-label required">Starts</span>
        <input v-model="form.startsOn" class="input" :class="{ invalid: err('startsOn') }" type="date" />
        <span v-if="err('startsOn')" class="field-error">Start {{ err('startsOn') }}</span>
      </label>
      <label class="field">
        <span class="field-label required">Ends</span>
        <input v-model="form.endsOn" class="input" :class="{ invalid: err('endsOn') }" type="date" />
        <span v-if="err('endsOn')" class="field-error">End {{ err('endsOn') }}</span>
      </label>
    </div>
    <template #footer>
      <button class="button" @click="adding = false">Back</button>
      <button class="button primary" :disabled="save.busy.value" @click="submit">Add term</button>
    </template>
  </ModalDialog>
</template>
