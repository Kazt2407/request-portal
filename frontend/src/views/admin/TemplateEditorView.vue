<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { departments, templates } from '@/api'
import type { FieldType, TemplateField, TemplateInput, TemplateStep } from '@/api/types'
import ErrorAlert from '@/components/ErrorAlert.vue'
import LoadingState from '@/components/LoadingState.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useAction, useLoad } from '@/composables/useLoad'
import { useToast } from '@/composables/useToast'
import { fieldError } from '@/errors'

const props = defineProps<{ id: number | null }>()
const router = useRouter()
const { show } = useToast()

const FIELD_TYPES: { value: FieldType; text: string }[] = [
  { value: 'TEXT', text: 'Short text' },
  { value: 'TEXTAREA', text: 'Long text' },
  { value: 'SELECT', text: 'Choice from a list' },
  { value: 'FILE', text: 'Files' },
  { value: 'IMAGE', text: 'Images' },
  { value: 'TERM', text: 'Academic term' },
  { value: 'SCHOOL_YEAR', text: 'School year' },
]

const form = reactive<TemplateInput>({
  title: '',
  description: '',
  departmentCode: '',
  audience: 'STUDENT',
  status: 'INACTIVE',
  showProcessingTime: true,
  fields: [],
  steps: [],
  version: null,
})
/** Options are edited as one per line. */
const optionText = ref<string[]>([])

const { data: departmentList, loading, error } = useLoad(async () => {
  const list = await departments.list()
  if (props.id !== null) {
    const template = await templates.get(props.id)
    Object.assign(form, {
      title: template.title,
      description: template.description,
      departmentCode: template.departmentCode,
      audience: template.audience,
      status: template.status,
      showProcessingTime: template.showProcessingTime,
      fields: template.fields.map((field) => ({ ...field })),
      steps: template.steps.map((step) => ({ ...step })),
      version: template.version,
    })
    optionText.value = template.fields.map((field) => field.options.join('\n'))
  } else {
    addStep(list[0]?.code ?? '')
  }
  return list
})

function addField(): void {
  form.fields.push({
    key: '',
    label: '',
    description: null,
    hint: null,
    type: 'TEXT',
    required: true,
    options: [],
    uploadFolder: null,
    uploadNote: null,
  })
  optionText.value.push('')
}

function addStep(departmentCode = form.departmentCode): void {
  form.steps.push({ departmentCode, description: '', slaDays: 3, requiresAttachment: false })
}

function move<T>(list: T[], index: number, offset: number, parallel?: string[]): void {
  const target = index + offset
  if (target < 0 || target >= list.length) {
    return
  }
  ;[list[index], list[target]] = [list[target] as T, list[index] as T]
  if (parallel) {
    ;[parallel[index], parallel[target]] = [parallel[target] ?? '', parallel[index] ?? '']
  }
}

function removeField(index: number): void {
  form.fields.splice(index, 1)
  optionText.value.splice(index, 1)
}

/** Suggests a key from the label, e.g. "Số bản sao" becomes "so_ban_sao". */
function suggestKey(field: TemplateField): void {
  if (field.key) {
    return
  }
  field.key = field.label
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/đ/gi, 'd')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '')
    .replace(/^(\d)/, 'f_$1')
    .slice(0, 50)
}

const save = useAction()
async function submit(): Promise<void> {
  const input: TemplateInput = {
    ...form,
    description: form.description || null,
    fields: form.fields.map((field: TemplateField, index) => ({
      ...field,
      hint: field.hint || null,
      description: field.description || null,
      options:
        field.type === 'SELECT'
          ? (optionText.value[index] ?? '').split('\n').map((option) => option.trim()).filter(Boolean)
          : [],
      uploadFolder: field.type === 'FILE' || field.type === 'IMAGE' ? field.uploadFolder || null : null,
      uploadNote: field.type === 'FILE' || field.type === 'IMAGE' ? field.uploadNote || null : null,
    })),
    steps: form.steps.map(({ departmentCode, description, slaDays, requiresAttachment }: TemplateStep) => ({
      departmentCode,
      description,
      slaDays: Number(slaDays),
      requiresAttachment,
    })),
  }
  const saved = await save.run(() => (props.id === null ? templates.create(input) : templates.update(props.id, input)))
  if (saved) {
    show(`Saved "${saved.title}"`)
    if (props.id === null) {
      await router.replace(`/admin/templates/${saved.id}`)
    }
    form.version = saved.version
  }
}

const err = (path: string) => fieldError(save.error.value, path)
</script>

<template>
  <LoadingState v-if="loading" />
  <ErrorAlert v-else-if="error" :error="error" />
  <form v-else-if="departmentList" class="stack" novalidate @submit.prevent="submit">
    <PageHeader :title="id === null ? 'New request template' : form.title || 'Request template'" :back="{ label: 'Templates', to: '/admin/templates' }">
      <template #actions>
        <button class="button primary" :disabled="save.busy.value">{{ save.busy.value ? 'Saving…' : 'Save template' }}</button>
      </template>
    </PageHeader>
    <ErrorAlert :error="save.error.value" />

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
        <div class="grid-2">
          <div class="field">
            <label for="department" class="required">Owning department</label>
            <select id="department" v-model="form.departmentCode" class="select" :class="{ invalid: err('departmentCode') }">
              <option value="" disabled>Choose…</option>
              <option v-for="department in departmentList" :key="department.code" :value="department.code">{{ department.name }} ({{ department.code }})</option>
            </select>
            <p class="field-help">Request codes start with it, e.g. {{ form.departmentCode || 'ACAD' }}-0042.</p>
            <p v-if="err('departmentCode')" class="field-error">Department {{ err('departmentCode') }}</p>
          </div>
          <div class="field">
            <label for="audience">Who can request it</label>
            <select id="audience" v-model="form.audience" class="select">
              <option value="STUDENT">Students</option>
              <option value="LECTURER">Lecturers</option>
              <option value="ALL">Students and lecturers</option>
            </select>
          </div>
        </div>
        <div class="row" style="gap: 24px">
          <label class="checkbox"><input v-model="form.status" type="checkbox" true-value="ACTIVE" false-value="INACTIVE" /> Active (people can submit it)</label>
          <label class="checkbox"><input v-model="form.showProcessingTime" type="checkbox" /> Show the expected processing time in emails</label>
        </div>
      </div>
    </section>

    <section class="card">
      <div class="card-header">
        <h2>Form fields</h2>
        <button type="button" class="button small" @click="addField">Add field</button>
      </div>
      <div class="card-body stack">
        <p v-if="form.fields.length === 0" class="muted">No fields: the request is submitted without details.</p>
        <fieldset v-for="(field, index) in form.fields" :key="index" class="item">
          <div class="item-header row">
            <strong>{{ index + 1 }}. {{ field.label || 'New field' }}</strong>
            <div class="spacer"></div>
            <button type="button" class="button ghost small icon-button" :disabled="index === 0" aria-label="Move up" @click="move(form.fields, index, -1, optionText)">↑</button>
            <button type="button" class="button ghost small icon-button" :disabled="index === form.fields.length - 1" aria-label="Move down" @click="move(form.fields, index, 1, optionText)">↓</button>
            <button type="button" class="button ghost small danger" @click="removeField(index)">Remove</button>
          </div>
          <div class="grid-3">
            <div class="field">
              <label :for="`label-${index}`" class="required">Label</label>
              <input :id="`label-${index}`" v-model="field.label" class="input" :class="{ invalid: err(`fields[${index}].label`) }" @blur="suggestKey(field)" />
              <p v-if="err(`fields[${index}].label`)" class="field-error">{{ err(`fields[${index}].label`) }}</p>
            </div>
            <div class="field">
              <label :for="`key-${index}`" class="required">Key</label>
              <input :id="`key-${index}`" v-model="field.key" class="input mono" :class="{ invalid: err(`fields[${index}].key`) }" placeholder="copies" />
              <p v-if="err(`fields[${index}].key`)" class="field-error">{{ err(`fields[${index}].key`) }}</p>
            </div>
            <div class="field">
              <label :for="`type-${index}`">Type</label>
              <select :id="`type-${index}`" v-model="field.type" class="select">
                <option v-for="type in FIELD_TYPES" :key="type.value" :value="type.value">{{ type.text }}</option>
              </select>
            </div>
          </div>
          <div class="grid-2">
            <div class="field">
              <label :for="`hint-${index}`">Hint</label>
              <input :id="`hint-${index}`" v-model="field.hint" class="input" maxlength="200" />
            </div>
            <div class="field">
              <label :for="`description-${index}`">Description</label>
              <input :id="`description-${index}`" v-model="field.description" class="input" maxlength="2000" />
            </div>
          </div>
          <div v-if="field.type === 'SELECT'" class="field">
            <label :for="`options-${index}`" class="required">Options, one per line</label>
            <textarea :id="`options-${index}`" v-model="optionText[index]" :rows="Math.max(3, (optionText[index] ?? '').split('\n').length + 1)" class="textarea" :class="{ invalid: err(`fields[${index}].options`) }"></textarea>
            <p v-if="err(`fields[${index}].options`)" class="field-error">{{ err(`fields[${index}].options`) }}</p>
          </div>
          <div v-if="field.type === 'FILE' || field.type === 'IMAGE'" class="grid-2">
            <div class="field">
              <label :for="`folder-${index}`">Upload folder</label>
              <input :id="`folder-${index}`" v-model="field.uploadFolder" class="input" maxlength="120" placeholder="evidence" />
            </div>
            <div class="field">
              <label :for="`note-${index}`">Upload note</label>
              <input :id="`note-${index}`" v-model="field.uploadNote" class="input" maxlength="2000" placeholder="A scan of both sides" />
            </div>
          </div>
          <label class="checkbox"><input v-model="field.required" type="checkbox" /> Required</label>
        </fieldset>
      </div>
    </section>

    <section class="card">
      <div class="card-header">
        <h2>Processing steps</h2>
        <button type="button" class="button small" @click="addStep()">Add step</button>
      </div>
      <div class="card-body stack">
        <p v-if="err('steps')" class="field-error">Steps {{ err('steps') }}</p>
        <fieldset v-for="(step, index) in form.steps" :key="index" class="item">
          <div class="item-header row">
            <strong>Step {{ index + 1 }}</strong>
            <div class="spacer"></div>
            <button type="button" class="button ghost small icon-button" :disabled="index === 0" aria-label="Move up" @click="move(form.steps, index, -1)">↑</button>
            <button type="button" class="button ghost small icon-button" :disabled="index === form.steps.length - 1" aria-label="Move down" @click="move(form.steps, index, 1)">↓</button>
            <button type="button" class="button ghost small danger" :disabled="form.steps.length === 1" @click="form.steps.splice(index, 1)">Remove</button>
          </div>
          <div class="grid-3 steps">
            <div class="field">
              <label :for="`step-department-${index}`">Department</label>
              <select :id="`step-department-${index}`" v-model="step.departmentCode" class="select" :class="{ invalid: err(`steps[${index}].departmentCode`) }">
                <option v-for="department in departmentList" :key="department.code" :value="department.code">{{ department.name }}</option>
              </select>
            </div>
            <div class="field">
              <label :for="`step-description-${index}`" class="required">What they do</label>
              <input :id="`step-description-${index}`" v-model="step.description" class="input" :class="{ invalid: err(`steps[${index}].description`) }" maxlength="500" />
              <p v-if="err(`steps[${index}].description`)" class="field-error">{{ err(`steps[${index}].description`) }}</p>
            </div>
            <div class="field">
              <label :for="`step-sla-${index}`">Deadline (days)</label>
              <input :id="`step-sla-${index}`" v-model.number="step.slaDays" class="input" type="number" min="1" max="365" />
            </div>
          </div>
          <label class="checkbox"><input v-model="step.requiresAttachment" type="checkbox" /> Staff must attach a file to complete it</label>
        </fieldset>
      </div>
    </section>

    <div class="row">
      <button class="button primary" :disabled="save.busy.value">{{ save.busy.value ? 'Saving…' : 'Save template' }}</button>
      <RouterLink to="/admin/templates" class="button ghost">Back to templates</RouterLink>
    </div>
  </form>
</template>

<style scoped>
.item {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px 16px 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface-sunken);
}

.item-header {
  gap: 4px;
}

.grid-3 {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.grid-3.steps {
  grid-template-columns: minmax(0, 1fr) minmax(0, 2fr) 120px;
}

@media (max-width: 760px) {
  .grid-3,
  .grid-3.steps {
    grid-template-columns: 1fr;
  }
}
</style>
