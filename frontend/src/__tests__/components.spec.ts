import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { ApiError } from '@/api/http'
import type { TemplateField } from '@/api/types'
import DynamicField from '@/components/DynamicField.vue'
import ErrorAlert from '@/components/ErrorAlert.vue'
import StatusBadge from '@/components/StatusBadge.vue'

const field = (overrides: Partial<TemplateField>): TemplateField => ({
  key: 'language',
  label: 'Language',
  description: null,
  hint: null,
  type: 'TEXT',
  required: true,
  options: [],
  uploadFolder: null,
  uploadNote: null,
  ...overrides,
})

describe('DynamicField', () => {
  it('renders a choice field as a select with its options', async () => {
    const wrapper = mount(DynamicField, {
      props: { field: field({ type: 'SELECT', options: ['Vietnamese', 'English'] }), terms: [], schoolYears: [], modelValue: '' },
    })

    const select = wrapper.get('select')
    expect(select.findAll('option').map((option) => option.text())).toEqual(['Choose…', 'Vietnamese', 'English'])
    await select.setValue('English')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['English'])
  })

  it('offers the academic terms for a term field and shows the error after the label', () => {
    const wrapper = mount(DynamicField, {
      props: {
        field: field({ key: 'term', label: 'Term', type: 'TERM' }),
        terms: [{ id: 1, code: '2026-2027-1', schoolYear: '2026-2027', termNumber: 1, name: 'Semester 1', startsOn: '2026-09-01', endsOn: '2027-01-15' }],
        schoolYears: [],
        modelValue: '',
        error: 'is required',
      },
    })

    expect(wrapper.get('select').text()).toContain('Semester 1 (2026-2027)')
    expect(wrapper.get('.field-error').text()).toBe('Term is required')
  })
})

describe('StatusBadge', () => {
  it('turns the status into words, and an overdue step into a warning', () => {
    expect(mount(StatusBadge, { props: { status: 'IN_PROGRESS' } }).text()).toBe('In progress')
    const overdue = mount(StatusBadge, { props: { status: 'IN_PROGRESS', overdue: true } })
    expect(overdue.text()).toBe('Overdue')
    expect(overdue.classes()).toContain('warning')
  })
})

describe('ErrorAlert', () => {
  it('lists field errors that no field shows', () => {
    const error = new ApiError(400, 'Bad Request', undefined, { reason: 'is required', extra: 'is not a field of this template' })

    const wrapper = mount(ErrorAlert, { props: { error, shown: ['reason'] } })

    expect(wrapper.text()).toContain('Please correct the highlighted fields.')
    expect(wrapper.findAll('li').map((item) => item.text())).toEqual(['extra: is not a field of this template'])
  })
})
