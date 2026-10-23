<script setup lang="ts">
import { accounts, profiles } from '@/api'
import { ApiError } from '@/api/http'
import { useAuth } from '@/auth'
import ErrorAlert from '@/components/ErrorAlert.vue'
import LoadingState from '@/components/LoadingState.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useLoad } from '@/composables/useLoad'
import { date, dateTime, label } from '@/format'

const { hasRole } = useAuth()
const account = useLoad(() => accounts.me())
// Only students have a student record; 404 means none has been entered yet.
const profile = useLoad(() =>
  profiles.mine().catch((error: unknown) => {
    if (error instanceof ApiError && error.status === 404) {
      return null
    }
    throw error
  }),
)
</script>

<template>
  <PageHeader title="Profile" subtitle="Your account and the details used to fill in forms." />
  <div class="grid-2">
    <section class="card">
      <div class="card-header"><h2>Account</h2></div>
      <div class="card-body">
        <LoadingState v-if="account.loading.value" />
        <ErrorAlert :error="account.error.value" />
        <dl v-if="account.data.value" class="details">
          <dt>Name</dt>
          <dd>{{ account.data.value.fullName }}</dd>
          <dt>Email</dt>
          <dd>{{ account.data.value.email }}</dd>
          <dt>Roles</dt>
          <dd>{{ account.data.value.roles.map(label).join(', ') }}</dd>
          <dt>Department</dt>
          <dd>{{ account.data.value.department?.name ?? '—' }}</dd>
          <dt>Member since</dt>
          <dd>{{ dateTime(account.data.value.createdAt) }}</dd>
        </dl>
        <p class="muted small" style="margin-top: 16px">Your name and email come from the university sign-in. Ask an administrator to change your roles.</p>
      </div>
    </section>

    <section v-if="hasRole('STUDENT')" class="card">
      <div class="card-header"><h2>Student record</h2></div>
      <div class="card-body">
        <LoadingState v-if="profile.loading.value" />
        <ErrorAlert :error="profile.error.value" />
        <dl v-if="profile.data.value" class="details">
          <dt>Student code</dt>
          <dd class="mono">{{ profile.data.value.studentCode }}</dd>
          <dt>Date of birth</dt>
          <dd>{{ date(profile.data.value.dateOfBirth) }}</dd>
          <dt>Class</dt>
          <dd>{{ profile.data.value.className ?? '—' }}</dd>
          <dt>Faculty</dt>
          <dd>{{ profile.data.value.faculty ?? '—' }}</dd>
          <dt>Major</dt>
          <dd>{{ profile.data.value.major ?? '—' }}</dd>
          <dt>Phone</dt>
          <dd>{{ profile.data.value.phone ?? '—' }}</dd>
        </dl>
        <p v-else-if="profile.data.value === null" class="muted">No student record yet. Student Affairs adds it; forms leave these details blank until then.</p>
      </div>
    </section>
  </div>
</template>
