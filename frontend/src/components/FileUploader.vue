<script setup lang="ts">
import { ref } from 'vue'
import type { FileInfo, FilePurpose } from '@/api/types'
import { uploadFile } from '@/api/upload'
import { describe } from '@/errors'
import { bytes } from '@/format'

/** Uploads straight to storage and keeps the ids of the uploaded files in v-model. */
const props = withDefaults(
  defineProps<{ purpose: FilePurpose; accept?: string; multiple?: boolean; max?: number; invalid?: boolean; inputId?: string }>(),
  { accept: '.pdf,.jpg,.jpeg,.png,.webp,.doc,.docx,.xls,.xlsx', multiple: true, max: 10, inputId: undefined },
)
const ids = defineModel<string[]>({ default: () => [] })
const emit = defineEmits<{ uploaded: [file: FileInfo] }>()

interface Item {
  key: number
  name: string
  size: number
  progress: number
  id?: string
  error?: string
}

const items = ref<Item[]>([])
const input = ref<HTMLInputElement>()
let nextKey = 1

async function choose(event: Event): Promise<void> {
  const chosen = Array.from((event.target as HTMLInputElement).files ?? [])
  if (input.value) {
    input.value.value = ''
  }
  const room = props.max - items.value.filter((item) => !item.error).length
  for (const file of chosen.slice(0, Math.max(room, 0))) {
    items.value.push({ key: nextKey++, name: file.name, size: file.size, progress: 0 })
    const item = items.value[items.value.length - 1] as Item
    uploadFile(file, props.purpose, (fraction) => (item.progress = fraction))
      .then((info) => {
        item.id = info.id
        item.progress = 1
        ids.value = [...ids.value, info.id]
        emit('uploaded', info)
      })
      .catch((error: unknown) => (item.error = describe(error)))
  }
}

function remove(item: Item): void {
  items.value = items.value.filter((other) => other !== item)
  if (item.id) {
    ids.value = ids.value.filter((id) => id !== item.id)
  }
}

</script>

<template>
  <div class="uploader">
    <label class="drop" :class="{ invalid }">
      <input :id="inputId" ref="input" type="file" :accept="accept" :multiple="multiple" class="visually-hidden" @change="choose" />
      <span><strong>Choose {{ multiple ? 'files' : 'a file' }}</strong> <span class="muted">PDF, images or Office documents, up to 20 MB</span></span>
    </label>
    <ul v-if="items.length" class="files">
      <li v-for="item in items" :key="item.key" class="file">
        <div class="file-name">
          <span>{{ item.name }}</span>
          <span class="muted small">{{ bytes(item.size) }}</span>
          <span v-if="item.id" class="badge success plain">Uploaded</span>
          <span v-else-if="item.error" class="field-error">{{ item.error }}</span>
        </div>
        <div v-if="!item.id && !item.error" class="progress"><div :style="{ width: `${item.progress * 100}%` }"></div></div>
        <button type="button" class="button ghost small" @click="remove(item)">Remove</button>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.uploader {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.drop {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 14px;
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius-small);
  background: var(--surface-sunken);
  text-align: center;
  cursor: pointer;
}

.drop:hover,
.drop:focus-within {
  border-color: var(--accent);
}

.drop.invalid {
  border-color: var(--danger);
}

.files {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.file {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: 4px 8px;
  padding: 8px 10px;
  border: 1px solid var(--border);
  border-radius: var(--radius-small);
}

.file-name {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
  overflow-wrap: anywhere;
}

.file .progress {
  grid-column: 1 / -1;
  grid-row: 2;
}
</style>
