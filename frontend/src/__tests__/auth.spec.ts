import { describe, expect, it } from 'vitest'
import { userFrom } from '@/auth'

describe('userFrom', () => {
  it('reads the user from the access token, family name first', () => {
    const user = userFrom({
      sub: 'a1000000-0000-4000-8000-000000000003',
      name: 'Văn Cường Lê',
      given_name: 'Văn Cường',
      family_name: 'Lê',
      email: 'cuong.le@example.edu',
      preferred_username: 'staff.academic',
      roles: ['STAFF', 'offline_access', 'default-roles-request-portal'],
      department: 'ACAD',
    })

    expect(user).toEqual({
      id: 'a1000000-0000-4000-8000-000000000003',
      name: 'Lê Văn Cường',
      email: 'cuong.le@example.edu',
      username: 'staff.academic',
      roles: ['STAFF'],
      department: 'ACAD',
    })
  })

  it('needs a subject', () => {
    expect(userFrom(undefined)).toBeNull()
    expect(userFrom({ name: 'Nobody' })).toBeNull()
  })
})
