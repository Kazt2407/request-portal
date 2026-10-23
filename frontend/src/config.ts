/** Settings that differ per environment, read at startup so one build runs everywhere. */
export interface AppConfig {
  keycloakUrl: string
  realm: string
  clientId: string
}

export async function loadConfig(): Promise<AppConfig> {
  const response = await fetch('/config.json', { cache: 'no-store' })
  if (!response.ok) {
    throw new Error(`Could not load /config.json (${response.status})`)
  }
  return (await response.json()) as AppConfig
}
