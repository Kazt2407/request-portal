<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'

/** A native <dialog>: focus is trapped and Escape closes it, for free. */
const props = defineProps<{ title: string; wide?: boolean }>()
const open = defineModel<boolean>('open', { required: true })
const dialog = ref<HTMLDialogElement>()

function sync(): void {
  if (!dialog.value) {
    return
  }
  if (open.value && !dialog.value.open) {
    dialog.value.showModal()
  } else if (!open.value && dialog.value.open) {
    dialog.value.close()
  }
}

watch(open, sync)
onMounted(sync)
</script>

<template>
  <dialog ref="dialog" class="modal" :class="{ wide: props.wide }" @close="open = false">
    <form method="dialog" class="visually-hidden"><button>Close</button></form>
    <div class="modal-header">
      <h2>{{ title }}</h2>
    </div>
    <div class="modal-body stack">
      <slot />
    </div>
    <div v-if="$slots.footer" class="modal-footer">
      <slot name="footer" />
    </div>
  </dialog>
</template>
