<script setup lang="ts">
import { ref, watch } from 'vue'
import { files } from '@/api'
import type { FileInfo } from '@/api/types'
import { useToast } from '@/composables/useToast'
import { describe } from '@/errors'
import { bytes } from '@/format'

/** Names and download links for files referenced by id. */
const props = defineProps<{ ids: string[] }>()
const infos = ref<Record<string, FileInfo | null>>({})
const { show } = useToast()

watch(
  () => props.ids,
  async (ids) => {
    for (const id of ids) {
      if (!(id in infos.value)) {
        infos.value[id] = null
        infos.value[id] = await files.get(id).catch(() => null)
      }
    }
  },
  { immediate: true },
)

async function open(id: string): Promise<void> {
  try {
    const link = await files.downloadLink(id)
    window.location.assign(link.url)
  } catch (error) {
    show(describe(error), 'danger')
  }
}
</script>

<template>
  <ul class="file-list">
    <li v-for="id in ids" :key="id">
      <button type="button" class="link" @click="open(id)">
        <span aria-hidden="true">📄</span> {{ infos[id]?.fileName ?? 'File' }}
      </button>
      <span v-if="infos[id]" class="muted small">{{ bytes(infos[id]?.size) }}</span>
    </li>
  </ul>
</template>

<style scoped>
.file-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.file-list li {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.link {
  padding: 0;
  border: 0;
  background: none;
  color: var(--accent);
  font: inherit;
  text-align: left;
  cursor: pointer;
  overflow-wrap: anywhere;
}

.link:hover {
  text-decoration: underline;
}
</style>
