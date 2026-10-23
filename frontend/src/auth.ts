import Keycloak, { type KeycloakTokenParsed } from 'keycloak-js'
import { computed, shallowRef } from 'vue'
import type { AppConfig } from './config'

export type Role = 'STUDENT' | 'LECTURER' | 'STAFF' | 'ADMIN'

export interface User {
  id: string
  name: string
  email: string
  username: string
  roles: Role[]
  /** Department code of staff, e.g. ACAD. */
  department: string | null
}

interface PortalToken extends KeycloakTokenParsed {
  name?: string
  given_name?: string
  family_name?: string
  email?: string
  preferred_username?: string
  roles?: string[]
  department?: string
}

let keycloak: Keycloak | undefined
const currentUser = shallowRef<User | null>(null)

/** Signs in through Keycloak (authorization code with PKCE) before the app starts. */
export async function initAuth(config: AppConfig): Promise<void> {
  keycloak = new Keycloak({ url: config.keycloakUrl, realm: config.realm, clientId: config.clientId })
  keycloak.onAuthRefreshSuccess = () => (currentUser.value = userFrom(keycloak?.tokenParsed))
  keycloak.onTokenExpired = () => void keycloak?.updateToken(30).catch(() => keycloak?.login())
  await keycloak.init({ onLoad: 'login-required', pkceMethod: 'S256', checkLoginIframe: false })
  currentUser.value = userFrom(keycloak.tokenParsed)
}

/** A token valid for at least 30 more seconds; sends the user back to sign in if the session ended. */
export async function accessToken(): Promise<string> {
  if (!keycloak) {
    throw new Error('Not signed in')
  }
  try {
    await keycloak.updateToken(30)
  } catch {
    await keycloak.login()
  }
  return keycloak.token ?? ''
}

export function logout(): void {
  void keycloak?.logout({ redirectUri: window.location.origin })
}

export function userFrom(token: PortalToken | undefined): User | null {
  if (!token?.sub) {
    return null
  }
  const known: Role[] = ['STUDENT', 'LECTURER', 'STAFF', 'ADMIN']
  return {
    id: token.sub,
    // Vietnamese names put the family name first: Nguyễn Văn An, not Văn An Nguyễn.
    name:
      token.family_name && token.given_name
        ? `${token.family_name} ${token.given_name}`
        : (token.name ?? token.preferred_username ?? ''),
    email: token.email ?? '',
    username: token.preferred_username ?? '',
    roles: (token.roles ?? []).filter((role): role is Role => known.includes(role as Role)),
    department: token.department ?? null,
  }
}

/** The signed-in user. Components only render after sign-in, so the user is always present. */
export function useAuth() {
  const user = computed(() => currentUser.value as User)
  const hasRole = (...roles: Role[]) => roles.some((role) => currentUser.value?.roles.includes(role))
  return { user, hasRole, logout }
}

/** For tests: sign a user in without Keycloak. */
export function setUserForTests(user: User | null): void {
  currentUser.value = user
}
