<script setup lang="ts">
import { computed } from 'vue'
import type { TemplateField, Term } from '@/api/types'
import FileUploader from './FileUploader.vue'

/** One template field rendered as the input its type needs. */
const props = defineProps<{ field: TemplateField; terms: Term[]; schoolYears: string[]; error?: string }>()
const value = defineModel<string | string[]>()
const id = computed(() => `field-${props.field.key}`)
const text = computed({
  get: () => (typeof value.value === 'string' ? value.value : ''),
  set: (next: string) => (value.value = next),
})
const fileIds = computed({
  get: () => (Array.isArray(value.value) ? value.value : []),
  set: (next: string[]) => (value.value = next),
})
</script>

<template>
  <div class="field">
    <label :for="id" :class="{ required: field.required }">{{ field.label }}</label>
    <p v-if="field.description" class="field-help pre-line">{{ field.description }}</p>

    <textarea v-if="field.type === 'TEXTAREA'" :id="id" v-model="text" class="textarea" :class="{ invalid: error }" :placeholder="field.hint ?? ''" maxlength="5000"></textarea>
    <select v-else-if="field.type === 'SELECT'" :id="id" v-model="text" class="select" :class="{ invalid: error }">
      <option value="">Choose…</option>
      <option v-for="option in field.options" :key="option" :value="option">{{ option }}</option>
    </select>
    <select v-else-if="field.type === 'TERM'" :id="id" v-model="text" class="select" :class="{ invalid: error }">
      <option value="">Choose a term…</option>
      <option v-for="term in terms" :key="term.code" :value="term.code">{{ term.name }} ({{ term.schoolYear }})</option>
    </select>
    <select v-else-if="field.type === 'SCHOOL_YEAR'" :id="id" v-model="text" class="select" :class="{ invalid: error }">
      <option value="">Choose a school year…</option>
      <option v-for="year in schoolYears" :key="year" :value="year">{{ year }}</option>
    </select>
    <FileUploader
      v-else-if="field.type === 'FILE' || field.type === 'IMAGE'"
      v-model="fileIds"
      :input-id="id"
      purpose="REQUEST_FIELD"
      :accept="field.type === 'IMAGE' ? 'image/jpeg,image/png,image/webp' : undefined"
      :invalid="!!error"
    />
    <input v-else :id="id" v-model="text" class="input" :class="{ invalid: error }" :placeholder="field.hint ?? ''" maxlength="1000" />

    <p v-if="field.uploadNote" class="field-help pre-line">{{ field.uploadNote }}</p>
    <p v-if="field.hint && (field.type === 'FILE' || field.type === 'IMAGE' || field.type === 'SELECT')" class="field-help">{{ field.hint }}</p>
    <p v-if="error" class="field-error">{{ field.label }} {{ error }}</p>
  </div>
</template>
