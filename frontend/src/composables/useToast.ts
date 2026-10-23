import { ref } from 'vue'

export interface Toast {
  id: number
  message: string
  kind: 'success' | 'danger' | 'info'
}

const toasts = ref<Toast[]>([])
let nextId = 1

/** Short confirmations shown in a corner, e.g. "Request ACAD-0042 submitted". */
export function useToast() {
  function show(message: string, kind: Toast['kind'] = 'success'): void {
    const id = nextId++
    toasts.value.push({ id, message, kind })
    setTimeout(() => dismiss(id), 5000)
  }

  function dismiss(id: number): void {
    toasts.value = toasts.value.filter((toast) => toast.id !== id)
  }

  return { toasts, show, dismiss }
}
