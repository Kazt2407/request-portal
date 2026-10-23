<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { forms } from '@/api'
import type { FormFieldType, FormInput } from '@/api/types'
import ErrorAlert from '@/components/ErrorAlert.vue'
import FileList from '@/components/FileList.vue'
import FileUploader from '@/components/FileUploader.vue'
import LoadingState from '@/components/LoadingState.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useAction, useLoad } from '@/composables/useLoad'
import { useToast } from '@/composables/useToast'
import { fieldError } from '@/errors'

const props = defineProps<{ id: number | null }>()
const router = useRouter()
const { show } = useToast()
const DOCX = '.docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document'

/** Placeholders document-service fills by itself from the account, the student profile and the date. */
const AUTOMATIC = [
  'full_name', 'email', 'username', 'student_code', 'date_of_birth', 'class_name', 'faculty', 'major', 'phone',
  'today', 'day', 'month', 'year',
]

const form = reactive<FormInput>({
  title: '',
  description: '',
  audience: 'STUDENT',
  active: false,
  templateFileId: null,
  fields: [],
  version: null,
})
const placeholders = ref<string[]>([])
const upload = ref<string[]>([])

const { loading, error } = useLoad(async () => {
  if (props.id === null) {
    return
  }
  const loaded = await forms.get(props.id)
  Object.assign(form, {
    title: loaded.title,
    description: loaded.description,
    audience: loaded.audience,
    active: loaded.active,
    templateFileId: loaded.templateFileId,
    fields: loaded.fields.map((field) => ({ ...field })),
    version: loaded.version,
  })
  placeholders.value = loaded.placeholders
})

/** Placeholders in the document that nothing will fill: they print as a dotted blank. */
const unfilled = computed(() => {
  const filled = new Set(AUTOMATIC)
  for (const field of form.fields) {
    filled.add(field.key)
    if (field.type === 'NUMBER') {
      filled.add(`${field.key}_words`)
    }
  }
  return placeholders.value.filter((placeholder) => !filled.has(placeholder))
})

function addField(type: FormFieldType = 'TEXT'): void {
  form.fields.push({ key: '', label: '', hint: null, type, required: false })
}

const save = useAction()
async function submit(): Promise<void> {
  const input: FormInput = {
    ...form,
    description: form.description || null,
    templateFileId: upload.value[0] ?? form.templateFileId,
    fields: form.fields.map((field) => ({ ...field, hint: field.hint || null })),
  }
  const saved = await save.run(() => (props.id === null ? forms.create(input) : forms.update(props.id, input)))
  if (saved) {
    show(`Saved "${saved.title}"`)
    form.version = saved.version
    form.templateFileId = saved.templateFileId
    placeholders.value = saved.placeholders
    upload.value = []
    if (props.id === null) {
      await router.replace(`/admin/forms/${saved.id}`)
    }
  }
}

const err = (path: string) => fieldError(save.error.value, path)
</script>

<template>
  <LoadingState v-if="loading" />
  <ErrorAlert v-else-if="error" :error="error" />
  <form v-else class="stack" novalidate @submit.prevent="submit">
    <PageHeader :title="id === null ? 'New form' : form.title || 'Form'" :back="{ label: 'Forms', to: '/admin/forms' }">
      <template #actions>
        <button class="button primary" :disabled="save.busy.value">{{ save.busy.value ? 'Saving…' : 'Save form' }}</button>
      </template>
    </PageHeader>
    <ErrorAlert :error="save.error.value" />

    <div class="layout">
      <div class="stack">
        <section class="card">
          <div class="card-header"><h2>About</h2></div>
          <div class="card-body stack">
            <div class="field">
              <label for="title" class="required">Title</label>
              <input id="title" v-model="form.title" class="input" :class="{ invalid: err('title') }" maxlength="200" />
              <p v-if="err('title')" class="field-error">Title {{ err('title') }}</p>
            </div>
            <div class="field">
              <label for="description">Description</label>
              <textarea id="description" v-model="form.description" class="textarea" maxlength="5000"></textarea>
            </div>
            <div class="row" style="gap: 24px">
              <label class="field" style="flex: 0 1 240px">
                <span class="field-label">Who can use it</span>
                <select v-model="form.audience" class="select">
                  <option value="STUDENT">Students</option>
                  <option value="LECTURER">Lecturers</option>
                  <option value="ALL">Everyone</option>
                </select>
              </label>
              <label class="checkbox"><input v-model="form.active" type="checkbox" /> Published</label>
            </div>
          </div>
        </section>

        <section class="card">
          <div class="card-header"><h2>Word template</h2></div>
          <div class="card-body stack-small">
            <p class="muted">Write <code>${placeholder}</code> where a value goes, for example <code>${full_name}</code>.</p>
            <FileList v-if="form.templateFileId && upload.length === 0" :ids="[form.templateFileId]" />
            <FileUploader v-model="upload" purpose="FORM_TEMPLATE" :accept="DOCX" :multiple="false" :max="1" :invalid="!!err('templateFileId')" />
            <p v-if="err('templateFileId')" class="field-error">Template {{ err('templateFileId') }}</p>
          </div>
        </section>

        <section class="card">
          <div class="card-header">
            <h2>Fields people fill in</h2>
            <button type="button" class="button small" @click="addField()">Add field</button>
          </div>
          <div class="card-body stack">
            <p v-if="form.fields.length === 0" class="muted">None: the form only uses automatic values.</p>
            <div v-for="(field, index) in form.fields" :key="index" class="form-field">
              <input v-model="field.key" class="input mono" :class="{ invalid: err(`fields[${index}].key`) }" placeholder="key" aria-label="Key" />
              <input v-model="field.label" class="input" :class="{ invalid: err(`fields[${index}].label`) }" placeholder="Label" aria-label="Label" />
              <select v-model="field.type" class="select" aria-label="Type">
                <option value="TEXT">Text</option>
                <option value="NUMBER">Number</option>
                <option value="DATE">Date</option>
              </select>
              <label class="checkbox"><input v-model="field.required" type="checkbox" /> Required</label>
              <button type="button" class="button ghost small danger" @click="form.fields.splice(index, 1)">Remove</button>
              <p v-if="err(`fields[${index}].key`) || err(`fields[${index}].label`)" class="field-error wide">
                {{ err(`fields[${index}].key`) ?? err(`fields[${index}].label`) }}
              </p>
            </div>
          </div>
        </section>
      </div>

      <aside class="card card-body stack-small">
        <h2>Placeholders in the template</h2>
        <p v-if="placeholders.length === 0" class="muted small">Shown after the template is saved.</p>
        <ul v-else class="placeholders">
          <li v-for="placeholder in placeholders" :key="placeholder">
            <code>{{ placeholder }}</code>
            <span v-if="unfilled.includes(placeholder)" class="badge warning plain">not filled</span>
            <span v-else-if="AUTOMATIC.includes(placeholder)" class="badge neutral plain">automatic</span>
          </li>
        </ul>
        <p v-if="unfilled.length" class="muted small">Add a field for each placeholder marked “not filled”, or it prints as a blank.</p>
        <p class="muted small">A number field <code>amount</code> also fills <code>amount_words</code> in Vietnamese.</p>
      </aside>
    </div>
  </form>
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

.form-field {
  display: grid;
  grid-template-columns: 160px minmax(0, 1fr) 110px auto auto;
  align-items: center;
  gap: 8px;
}

@media (max-width: 760px) {
  .form-field {
    grid-template-columns: 1fr 1fr;
  }
}

.form-field .wide {
  grid-column: 1 / -1;
}

.placeholders {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.placeholders li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
</style>
