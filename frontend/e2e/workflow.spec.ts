import { expect, test } from '@playwright/test'
import { pdf, requestCode, signIn, toast } from './support'

test('a transcript request goes from the student through two departments and back', async ({ browser }) => {
  const student = await signIn(browser, 'student')
  await student.getByRole('link', { name: 'New request' }).first().click()
  await student.getByRole('link', { name: /Official transcript/ }).click()
  await student.getByLabel('Number of copies').selectOption('2')
  await student.getByLabel('Language').selectOption('English')
  await student.getByLabel('Reason').fill('Exchange programme application')
  await student.getByRole('button', { name: 'Submit request' }).click()

  await expect(student).toHaveURL(/\/requests\/\d+$/)
  const url = new URL(student.url()).pathname
  const code = await requestCode(student)
  expect(code).toMatch(/^ACAD-\d+$/)
  await expect(toast(student, `Request ${code} submitted`)).toBeVisible()

  // Academic Affairs finds it in their queue and completes step 1, which needs a scan.
  const academic = await signIn(browser, 'staff.academic')
  await academic.getByRole('searchbox', { name: 'Search' }).fill(code)
  await academic.getByRole('link', { name: code }).click()
  await academic.getByRole('button', { name: 'Complete step 1' }).click()
  const dialog = academic.getByRole('dialog')
  await expect(dialog.getByRole('button', { name: 'Complete step' })).toBeDisabled()
  await dialog.locator('input[type=file]').setInputFiles(pdf('Bảng điểm - đã đóng dấu.pdf', 'stamped transcript'))
  await expect(dialog.getByText('Uploaded')).toBeVisible()
  await dialog.getByLabel(/Note/).fill('Signed and stamped')
  await dialog.getByRole('button', { name: 'Complete step' }).click()
  await expect(toast(academic, 'Step completed')).toBeVisible()
  await expect(academic.getByRole('button', { name: /Complete step/ })).toHaveCount(0)

  // Finance completes the last step and records that the student collected the transcript.
  const finance = await signIn(browser, 'staff.finance')
  await finance.goto(url)
  await finance.getByRole('button', { name: 'Complete step 2' }).click()
  await finance.getByRole('dialog').getByRole('button', { name: 'Complete step' }).click()
  await finance.getByRole('button', { name: 'Mark as collected' }).click()
  await expect(finance.getByText('Collected', { exact: true })).toBeVisible()

  // The student sees the outcome, the stamped scan and can download the request as a Word document.
  await student.reload()
  await expect(student.getByText('Completed', { exact: true }).first()).toBeVisible()
  await expect(student.getByRole('button', { name: /Bảng điểm - đã đóng dấu\.pdf/ })).toBeVisible()
  const download = student.waitForEvent('download')
  await student.getByRole('button', { name: 'Download as Word' }).click()
  expect((await download).suggestedFilename()).toBe(`${code}.docx`)
})

test("a tuition receipt request carries the student's proof, which staff download as a ZIP", async ({ browser }) => {
  const student = await signIn(browser, 'student')
  await student.goto('/new')
  await student.getByRole('link', { name: /Tuition fee receipt/ }).click()
  await student.getByLabel('School year').selectOption({ index: 1 })
  await student.locator('input[type=file]').setInputFiles(pdf('Ủy nhiệm chi.pdf', 'bank transfer'))
  await expect(student.getByText('Uploaded')).toBeVisible()
  await student.getByRole('button', { name: 'Submit request' }).click()
  await expect(student).toHaveURL(/\/requests\/\d+$/)
  const code = await requestCode(student)
  await expect(student.getByRole('button', { name: /Ủy nhiệm chi\.pdf/ })).toBeVisible()

  const finance = await signIn(browser, 'staff.finance')
  await finance.getByRole('searchbox', { name: 'Search' }).fill(code)
  await finance.getByRole('checkbox', { name: `Select ${code}` }).check()
  await finance.getByRole('button', { name: 'Download files' }).click()
  const dialog = finance.getByRole('dialog')
  await expect(dialog.getByText('1 of 1 selected')).toBeVisible()
  await dialog.getByRole('button', { name: 'Build ZIP' }).click()
  await expect(dialog.getByRole('link', { name: 'Download ZIP' })).toBeVisible({ timeout: 20_000 })
})

test('missing required values are reported next to their fields', async ({ browser }) => {
  const student = await signIn(browser, 'student')
  await student.goto('/new')
  await student.getByRole('link', { name: /Official transcript/ }).click()
  await student.getByRole('button', { name: 'Submit request' }).click()

  await expect(student.getByText('Please correct the highlighted fields.')).toBeVisible()
  await expect(student.getByText(/^Number of copies .*required/)).toBeVisible()
  await expect(student).toHaveURL(/\/new\/\d+$/)
})

test('an admin creates a request template with fields and steps', async ({ browser }) => {
  const admin = await signIn(browser, 'admin')
  const title = `Graduation certificate copy ${Date.now()}`
  await admin.goto('/admin/templates/new')
  await admin.getByLabel('Title').fill(title)
  await admin.getByLabel('Owning department').selectOption('ACAD')
  await admin.getByRole('button', { name: 'Add field' }).click()
  await admin.getByLabel('Label', { exact: true }).fill('Số bản sao')
  await admin.getByLabel('Label', { exact: true }).blur()
  await expect(admin.getByLabel('Key', { exact: true })).toHaveValue('so_ban_sao')
  await admin.getByLabel('Type').selectOption('SELECT')
  await admin.getByLabel('Options, one per line').fill('1\n2\n3')
  await admin.getByLabel('What they do').fill('Copy and certify the certificate')
  await admin.getByRole('button', { name: 'Save template' }).first().click()

  await expect(toast(admin, `Saved "${title}"`)).toBeVisible()
  await expect(admin).toHaveURL(/\/admin\/templates\/\d+$/)
  await admin.reload()
  await expect(admin.getByLabel('Label', { exact: true })).toHaveValue('Số bản sao')
  await expect(admin.getByLabel('Options, one per line')).toHaveValue('1\n2\n3')
})
