<script setup lang="ts">
import { onBeforeUnmount, reactive, ref, watch } from 'vue'
import { accounts, departments, profiles } from '@/api'
import { ApiError } from '@/api/http'
import type { Account, Role, StudentProfileInput } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import ErrorAlert from '@/components/ErrorAlert.vue'
import LoadingState from '@/components/LoadingState.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import PageHeader from '@/components/PageHeader.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import { useAction, useLoad } from '@/composables/useLoad'
import { useToast } from '@/composables/useToast'
import { fieldError } from '@/errors'
import { dateTime, label } from '@/format'

const ROLES: Role[] = ['STUDENT', 'LECTURER', 'STAFF', 'ADMIN']
const { show } = useToast()

const filter = reactive<{ q: string; role: Role | ''; departmentId: number | null }>({ q: '', role: '', departmentId: null })
const page = ref(0)
const departmentList = useLoad(() => departments.list())
const { data, loading, error, reload } = useLoad(() => accounts.search(filter, { page: page.value, size: 25, sort: 'fullName' }))

let timer: ReturnType<typeof setTimeout> | undefined
watch(
  () => ({ ...filter }),
  (next, previous) => {
    clearTimeout(timer)
    timer = setTimeout(
      () => {
        page.value = 0
        void reload()
      },
      next.q !== previous.q ? 300 : 0,
    )
  },
)
watch(page, () => void reload())
onBeforeUnmount(() => clearTimeout(timer))

// Access: roles and department, written to Keycloak first by org-service.
const editing = ref<Account | null>(null)
const access = reactive<{ roles: Role[]; departmentId: number | null }>({ roles: [], departmentId: null })
const saveAccess = useAction()

function editAccess(account: Account): void {
  saveAccess.error.value = null
  access.roles = [...account.roles]
  access.departmentId = account.department?.id ?? null
  editing.value = account
}

async function submitAccess(): Promise<void> {
  const account = editing.value
  if (!account) {
    return
  }
  const saved = await saveAccess.run(() => accounts.updateAccess(account.id, access.roles, access.departmentId))
  if (saved) {
    show(`Updated ${saved.fullName}`)
    editing.value = null
    await reload()
  }
}

// Student record, used to fill in forms.
const student = ref<Account | null>(null)
const record = reactive<StudentProfileInput>({
  studentCode: '',
  dateOfBirth: null,
  className: null,
  faculty: null,
  major: null,
  phone: null,
  version: null,
})
const loadRecord = useAction()
const saveRecord = useAction()

async function editRecord(account: Account): Promise<void> {
  saveRecord.error.value = null
  Object.assign(record, { studentCode: '', dateOfBirth: null, className: null, faculty: null, major: null, phone: null, version: null })
  student.value = account
  const existing = await loadRecord.run(() =>
    profiles.get(account.id).catch((caught: unknown) => {
      if (caught instanceof ApiError && caught.status === 404) {
        return null
      }
      throw caught
    }),
  )
  if (existing) {
    const { studentCode, dateOfBirth, className, faculty, major, phone, version } = existing
    Object.assign(record, { studentCode, dateOfBirth, className, faculty, major, phone, version })
  }
}

async function submitRecord(): Promise<void> {
  const account = student.value
  if (!account) {
    return
  }
  const clean = Object.fromEntries(
    Object.entries(record).map(([key, value]) => [key, value === '' ? null : value]),
  ) as unknown as StudentProfileInput
  const saved = await saveRecord.run(() => profiles.save(account.id, clean))
  if (saved) {
    show(`Saved the student record of ${account.fullName}`)
    student.value = null
  }
}

const recordFields: { key: keyof StudentProfileInput; text: string; type?: string }[] = [
  { key: 'studentCode', text: 'Student code' },
  { key: 'dateOfBirth', text: 'Date of birth', type: 'date' },
  { key: 'className', text: 'Class' },
  { key: 'faculty', text: 'Faculty' },
  { key: 'major', text: 'Major' },
  { key: 'phone', text: 'Phone', type: 'tel' },
]
</script>

<template>
  <PageHeader title="Accounts" subtitle="People who have signed in. Search ignores accents: “nguyen” finds “Nguyễn”." />

  <div class="card">
    <div class="filters">
      <input v-model="filter.q" class="input search" type="search" placeholder="Search by name or email" aria-label="Search" />
      <select v-model="filter.role" class="select" aria-label="Role">
        <option value="">Any role</option>
        <option v-for="role in ROLES" :key="role" :value="role">{{ label(role) }}</option>
      </select>
      <select v-model="filter.departmentId" class="select" aria-label="Department">
        <option :value="null">Any department</option>
        <option v-for="department in departmentList.data.value" :key="department.id" :value="department.id">{{ department.name }}</option>
      </select>
    </div>
    <ErrorAlert :error="error" style="margin: 16px" />
    <LoadingState v-if="loading && !data" />
    <EmptyState v-else-if="data && data.items.length === 0" title="No accounts match" />
    <div v-else-if="data" class="table-wrap">
      <table class="table">
        <thead>
          <tr><th>Name</th><th>Roles</th><th>Department</th><th>Last seen</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-for="account in data.items" :key="account.id">
            <td>{{ account.fullName }}<div class="muted small">{{ account.email }}</div></td>
            <td>
              <span class="row" style="gap: 4px">
                <span v-for="role in account.roles" :key="role" class="badge plain" :class="role === 'ADMIN' ? 'danger' : role === 'STAFF' ? 'info' : 'neutral'">{{ label(role) }}</span>
              </span>
            </td>
            <td>{{ account.department?.name ?? '—' }}</td>
            <td class="nowrap muted">{{ dateTime(account.lastSeenAt) }}</td>
            <td class="nowrap">
              <button class="button small" @click="editAccess(account)">Access</button>
              <button v-if="account.roles.includes('STUDENT')" class="button small ghost" @click="editRecord(account)">Student record</button>
            </td>
          </tr>
        </tbody>
      </table>
      <PaginationBar :page="data.page" :total-pages="data.totalPages" :total-items="data.totalItems" @change="page = $event" />
    </div>
  </div>

  <ModalDialog :open="editing !== null" :title="`Access for ${editing?.fullName}`" @update:open="editing = null">
    <ErrorAlert :error="saveAccess.error.value" :shown="['roles', 'departmentId']" />
    <fieldset class="stack-small">
      <legend class="field-label">Roles</legend>
      <label v-for="role in ROLES" :key="role" class="checkbox"><input v-model="access.roles" type="checkbox" :value="role" /> {{ label(role) }}</label>
      <p v-if="fieldError(saveAccess.error.value, 'roles')" class="field-error">Roles {{ fieldError(saveAccess.error.value, 'roles') }}</p>
    </fieldset>
    <label class="field">
      <span class="field-label" :class="{ required: access.roles.includes('STAFF') }">Department</span>
      <select v-model="access.departmentId" class="select">
        <option :value="null">None</option>
        <option v-for="department in departmentList.data.value" :key="department.id" :value="department.id">{{ department.name }}</option>
      </select>
      <span class="field-help">Staff process the steps of their department.</span>
    </label>
    <template #footer>
      <button class="button" @click="editing = null">Back</button>
      <button class="button primary" :disabled="saveAccess.busy.value || access.roles.length === 0" @click="submitAccess">Save access</button>
    </template>
  </ModalDialog>

  <ModalDialog :open="student !== null" :title="`Student record of ${student?.fullName}`" @update:open="student = null">
    <LoadingState v-if="loadRecord.busy.value" />
    <ErrorAlert :error="loadRecord.error.value ?? saveRecord.error.value" :shown="recordFields.map((field) => field.key)" />
    <div v-if="!loadRecord.busy.value" class="grid-2">
      <label v-for="field in recordFields" :key="field.key" class="field">
        <span class="field-label" :class="{ required: field.key === 'studentCode' }">{{ field.text }}</span>
        <input v-model="record[field.key]" class="input" :type="field.type ?? 'text'" :class="{ invalid: fieldError(saveRecord.error.value, field.key) }" />
        <span v-if="fieldError(saveRecord.error.value, field.key)" class="field-error">{{ field.text }} {{ fieldError(saveRecord.error.value, field.key) }}</span>
      </label>
    </div>
    <template #footer>
      <button class="button" @click="student = null">Back</button>
      <button class="button primary" :disabled="saveRecord.busy.value || loadRecord.busy.value" @click="submitRecord">Save record</button>
    </template>
  </ModalDialog>
</template>

<style scoped>
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
}

.filters .select {
  width: auto;
}

.filters .search {
  flex: 1 1 260px;
}
</style>
