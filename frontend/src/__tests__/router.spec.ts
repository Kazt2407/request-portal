import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import { setUserForTests, type Role } from '@/auth'
import { router } from '@/router'

function signInAs(...roles: Role[]): void {
  setUserForTests({ id: 'u1', name: 'Test User', email: 'test@example.edu', username: 'test', roles, department: roles.includes('STAFF') ? 'ACAD' : null })
}

describe('router', () => {
  // jsdom has no scrolling; the router scrolls to the top on each navigation.
  beforeAll(() => vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined))

  afterEach(() => setUserForTests(null))

  it('sends people to the start page of their role', async () => {
    signInAs('STUDENT')
    await router.push('/')
    expect(router.currentRoute.value.name).toBe('my-requests')

    signInAs('STAFF')
    await router.push('/profile')
    await router.push('/')
    expect(router.currentRoute.value.name).toBe('queue')
  })

  it('keeps people out of pages their role may not open', async () => {
    signInAs('STUDENT')
    await router.push('/admin/accounts')
    expect(router.currentRoute.value.name).toBe('my-requests')

    signInAs('STAFF', 'ADMIN')
    await router.push('/admin/accounts')
    expect(router.currentRoute.value.name).toBe('accounts')
  })
})
