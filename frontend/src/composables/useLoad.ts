import { onMounted, ref, shallowRef, type Ref } from 'vue'

/** Loads data for a view, tracking loading and errors; call reload() after a change. */
export function useLoad<T>(load: () => Promise<T>, options: { immediate?: boolean } = {}) {
  const data = shallowRef<T>() as Ref<T | undefined>
  const loading = ref(false)
  const error = shallowRef<unknown>(null)

  async function reload(): Promise<void> {
    loading.value = true
    error.value = null
    try {
      data.value = await load()
    } catch (caught) {
      error.value = caught
    } finally {
      loading.value = false
    }
  }

  if (options.immediate !== false) {
    onMounted(reload)
  }
  return { data, loading, error, reload }
}

/** Runs an action such as a form submission, keeping its error for display. */
export function useAction() {
  const busy = ref(false)
  const error = shallowRef<unknown>(null)

  async function run<T>(action: () => Promise<T>): Promise<T | undefined> {
    busy.value = true
    error.value = null
    try {
      return await action()
    } catch (caught) {
      error.value = caught
      return undefined
    } finally {
      busy.value = false
    }
  }

  return { busy, error, run }
}
