<script setup lang="ts">
import { reactive, ref } from 'vue'
import { departments } from '@/api'
import type { Department } from '@/api/types'
import ErrorAlert from '@/components/ErrorAlert.vue'
import LoadingState from '@/components/LoadingState.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useAction, useLoad } from '@/composables/useLoad'
import { useToast } from '@/composables/useToast'
import { fieldError } from '@/errors'

const { show } = useToast()
const { data, loading, error, reload } = useLoad(() => departments.list())

/** null: closed; 'new': creating; a department: renaming it. */
const editing = ref<Department | 'new' | null>(null)
const form = reactive({ code: '', name: '', email: '', version: 0 })
const save = useAction()

function open(department: Department | 'new'): void {
  save.error.value = null
  Object.assign(
    form,
    department === 'new'
      ? { code: '', name: '', email: '', version: 0 }
      : { code: department.code, name: department.name, email: department.email ?? '', version: department.version },
  )
  editing.value = department
}

async function submit(): Promise<void> {
  const target = editing.value
  const email = form.email.trim() || null
  const saved = await save.run(() =>
    target === 'new'
      ? departments.create({ code: form.code.trim().toUpperCase(), name: form.name, email })
      : departments.update((target as Department).id, { name: form.name, email, version: form.version }),
  )
  if (saved) {
    show(`Saved ${saved.name}`)
    editing.value = null
    await reload()
  }
}

const err = (path: string) => fieldError(save.error.value, path)
</script>

<template>
  <PageHeader title="Departments" subtitle="Departments process request steps. Codes start request codes and never change.">
    <template #actions>
      <button class="button primary" @click="open('new')">New department</button>
    </template>
  </PageHeader>
  <div class="card">
    <ErrorAlert :error="error" style="margin: 16px" />
    <LoadingState v-if="loading && !data" />
    <div v-else-if="data" class="table-wrap">
      <table class="table">
        <thead><tr><th>Code</th><th>Name</th><th>Shared mailbox</th><th></th></tr></thead>
        <tbody>
          <tr v-for="department in data" :key="department.id">
            <td class="mono">{{ department.code }}</td>
            <td>{{ department.name }}</td>
            <td>{{ department.email ?? '—' }}</td>
            <td class="numeric"><button class="button small" @click="open(department)">Edit</button></td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>

  <ModalDialog :open="editing !== null" :title="editing === 'new' ? 'New department' : `Edit ${form.code}`" @update:open="editing = null">
    <ErrorAlert :error="save.error.value" :shown="['code', 'name', 'email']" />
    <label v-if="editing === 'new'" class="field">
      <span class="field-label required">Code</span>
      <input v-model="form.code" class="input mono" :class="{ invalid: err('code') }" placeholder="ACAD" maxlength="16" />
      <span v-if="err('code')" class="field-error">Code {{ err('code') }}</span>
    </label>
    <label class="field">
      <span class="field-label required">Name</span>
      <input v-model="form.name" class="input" :class="{ invalid: err('name') }" maxlength="200" />
      <span v-if="err('name')" class="field-error">Name {{ err('name') }}</span>
    </label>
    <label class="field">
      <span class="field-label">Shared mailbox</span>
      <input v-model="form.email" class="input" :class="{ invalid: err('email') }" type="email" placeholder="academic-affairs@example.edu" />
      <span class="field-help">Gets the emails about new and overdue requests.</span>
      <span v-if="err('email')" class="field-error">Email {{ err('email') }}</span>
    </label>
    <template #footer>
      <button class="button" @click="editing = null">Back</button>
      <button class="button primary" :disabled="save.busy.value" @click="submit">Save</button>
    </template>
  </ModalDialog>
</template>
