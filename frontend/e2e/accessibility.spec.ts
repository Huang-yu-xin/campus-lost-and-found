import type { Locator, Page } from '@playwright/test'
import { expect, test } from './fixtures'

async function tabTo(page: Page, target: Locator) {
  for (let step = 0; step < 60; step++) {
    if (await target.evaluate((element) => document.activeElement === element)) return
    await page.keyboard.press('Tab')
  }
  await expect(target).toBeFocused()
}

test('键盘可以跳过导航，页面切换更新标题、焦点和当前位置', async ({ page, mock }) => {
  mock.role = 'ANONYMOUS'
  await page.goto('/')
  const main = page.getByRole('main', { name: '校园失物招领系统' })
  await expect(main).toBeVisible()
  await page.keyboard.press('Tab')
  const skip = page.getByRole('link', { name: '跳到主要内容' })
  await expect(skip).toBeFocused()
  await page.keyboard.press('Enter')
  await expect(main).toBeFocused()

  const nav = page.getByRole('navigation', { name: '学生端导航' })
  const browse = nav.getByRole('link', { name: '浏览物品' })
  if ((page.viewportSize()?.width ?? 1440) <= 640) {
    const toggle = page.getByRole('button', { name: '展开菜单' })
    await tabTo(page, toggle)
    await page.keyboard.press('Enter')
    await expect(toggle).toHaveAttribute('aria-expanded', 'true')
  }
  await tabTo(page, browse)
  await page.keyboard.press('Enter')
  await expect(page).toHaveTitle('浏览物品 · 校园失物招领系统')
  await expect(page.getByRole('main', { name: '浏览物品' })).toBeFocused()
  await expect(nav.locator('[aria-current="page"]')).toHaveText('浏览物品')
  await expect(page.getByRole('link', { name: '登录', exact: true })).toHaveCount(1)
  await expect(page.getByRole('button', { name: '登录', exact: true })).toHaveCount(0)
})

test('登录和注册的错误关联到字段，密码显示按钮支持键盘', async ({ page, mock }) => {
  mock.role = 'ANONYMOUS'
  await page.goto('/login')
  const account = page.getByRole('textbox', { name: '账号' })
  const password = page.locator('input[autocomplete="current-password"]')
  const toggle = page.getByRole('button', { name: '显示密码' })
  await tabTo(page, account)
  await page.keyboard.press('Enter')
  await expect(account).toBeFocused()
  await expect(account).toHaveAttribute('aria-invalid', 'true')
  await expect(account).toHaveAccessibleDescription('请输入学号、校园卡号或管理员账号')
  await page.keyboard.type('20260001')
  await page.keyboard.press('Tab')
  await expect(password).toBeFocused()
  await expect(account).not.toHaveAttribute('aria-invalid', 'true')
  await page.keyboard.press('Tab')
  await expect(toggle).toBeFocused()
  await page.keyboard.press('Space')
  await expect(toggle).toHaveAttribute('aria-pressed', 'true')
  await expect(password).toHaveAttribute('type', 'text')
  await page.keyboard.press('Space')
  await expect(password).toHaveAttribute('type', 'password')

  await page.goto('/register')
  const submit = page.getByRole('button', { name: '注册', exact: true })
  await tabTo(page, submit)
  await page.keyboard.press('Enter')
  const studentId = page.getByRole('textbox', { name: '学号', exact: true })
  await expect(studentId).toBeFocused()
  await expect(studentId).toHaveAccessibleDescription('请输入学号')
})

test('搜索与翻页播报当前结果并保持键盘焦点', async ({ page, mock }) => {
  mock.role = 'ANONYMOUS'
  await page.goto('/items')
  const keyword = page.getByRole('textbox', { name: '关键词', exact: true })
  await tabTo(page, keyword)
  await page.keyboard.insertText('水杯')
  await page.keyboard.press('Enter')
  await expect
    .poll(() => mock.requests.some((request) => request.query.includes('keyword=')))
    .toBe(true)
  const status = page.getByRole('status')
  await expect(status).toContainText('共 71 条物品信息，当前第 1 页')
  const next = page
    .getByRole('navigation', { name: '物品搜索结果分页' })
    .getByRole('button', { name: /下一页/ })
  await tabTo(page, next)
  await page.keyboard.press('Enter')
  await expect(status).toContainText('当前第 2 页')
  await expect(next).toBeFocused()
})

test('发布表单将焦点移到有误的字段，提交后可从结果继续操作', async ({ page, mock }) => {
  await page.goto('/items/new')
  await page.getByLabel('发生日期').fill('2026-11-01')
  const submit = page.getByRole('button', { name: '提交审核' })
  await tabTo(page, submit)
  await page.keyboard.press('Enter')
  const title = page.getByRole('textbox', { name: /标题/ })
  await expect(title).toBeFocused()
  await expect(title).toHaveAttribute('aria-invalid', 'true')
  await expect(title).toHaveAccessibleDescription('标题需填写 1～50 字')
  await page.keyboard.insertText('遗失水杯')
  await page.keyboard.press('Tab')
  await expect(page.getByRole('textbox', { name: /发生地点/ })).toBeFocused()
  await page.keyboard.insertText('图书馆')
  await tabTo(page, page.getByRole('textbox', { name: /物品描述/ }))
  await page.keyboard.insertText('在图书馆一层窗边遗失的黑色保温杯')
  await tabTo(page, submit)
  await page.keyboard.press('Enter')
  const success = page.getByRole('dialog', { name: '提交成功' })
  await expect(success).toBeVisible()
  await expect(success.getByRole('button', { name: '知道了' })).toBeFocused()
  await page.keyboard.press('Enter')
  await expect(page.getByRole('heading', { name: '已提交，等待审核' })).toBeFocused()
  await tabTo(page, page.getByRole('link', { name: '查看我的发布' }))
  await page.keyboard.press('Enter')
  await expect(page.getByRole('main', { name: '我的发布' })).toBeFocused()
  expect(
    mock.requests.filter(({ path, method }) => path === '/items' && method === 'POST'),
  ).toHaveLength(1)
})

test('认领与线索留言可用键盘提交，并提供状态反馈', async ({ page, mock }) => {
  for (const [url, label, endpoint] of [
    ['/claims/7', '填写留言', '/claims/7/messages'],
    ['/clues/9', '会话留言（1～1000 字）', '/clues/9/messages'],
  ]) {
    await page.goto(url!)
    const message = page.getByRole('textbox', { name: label, exact: true })
    await expect(message).toBeVisible()
    await tabTo(page, message)
    await page.keyboard.insertText('确认见面地点')
    await tabTo(page, page.getByRole('button', { name: '发送留言' }))
    await page.keyboard.press('Enter')
    await expect(page.getByRole('status').filter({ hasText: '留言已发送' })).toBeAttached()
    expect(
      mock.requests.filter(({ path, method }) => path === endpoint && method === 'POST'),
    ).toHaveLength(1)
  }
})

test('举报弹窗包含字段名称和校验反馈，Escape 返回触发按钮', async ({ page, mock }) => {
  await page.goto('/items/42')
  const report = page.getByRole('button', { name: '举报这条信息' })
  await expect(report).toBeVisible()
  await tabTo(page, report)
  await page.keyboard.press('Enter')
  const dialog = page.getByRole('dialog', { name: '举报物品信息' })
  const reason = dialog.getByRole('textbox', { name: /请填写举报理由/ })
  await expect(dialog).toHaveAttribute('aria-modal', 'true')
  await expect(reason).toBeFocused()
  await page.keyboard.press('Enter')
  await expect(reason).toHaveAccessibleDescription('请填写 1～500 字的举报理由')
  await page.keyboard.press('Escape')
  await expect(dialog).toBeHidden()
  await expect(report).toBeFocused()
  expect(mock.requests.some(({ path, method }) => path === '/reports' && method === 'POST')).toBe(
    false,
  )
  await page.keyboard.press('Enter')
  await expect(reason).toBeFocused()
  await page.keyboard.insertText('这条测试信息需要复核')
  await tabTo(page, dialog.getByRole('button', { name: '确定' }))
  await page.keyboard.press('Enter')
  await expect(page.getByRole('alert').filter({ hasText: '举报已提交' })).toBeVisible()
  await expect(report).toBeFocused()
})

test('管理表格保留表头，弹层限制焦点并可返回操作入口', async ({ page, mock }) => {
  mock.role = 'ADMIN'
  await page.setViewportSize({ width: 390, height: 850 })
  await page.goto('/admin/items')
  const table = page.getByRole('region', { name: '物品审核列表' }).getByRole('table')
  await expect(table.getByRole('columnheader', { name: '标题' })).toBeVisible()
  await expect(table.getByRole('cell', { name: /长标题测试物品/ }).first()).toBeAttached()
  const details = table.getByRole('button', { name: '查看详情', exact: true }).first()
  await tabTo(page, details)
  await page.keyboard.press('Enter')
  const dialog = page.getByRole('dialog', { name: '物品审核详情' })
  await expect(dialog).toBeVisible()
  for (const key of ['Tab', 'Shift+Tab', 'Tab', 'Tab', 'Tab']) {
    await page.keyboard.press(key)
    await expect.poll(() => dialog.evaluate((el) => el.contains(document.activeElement))).toBe(true)
  }
  await page.keyboard.press('Escape')
  await expect(dialog).toBeHidden()
  await expect(details).toBeFocused()
})

test('强制色下焦点、控件边界和当前导航仍可辨识', async ({ page, mock }) => {
  mock.role = 'ANONYMOUS'
  await page.emulateMedia({ forcedColors: 'active' })
  await page.goto('/login')
  const toggle = page.getByRole('button', { name: '显示密码' })
  await expect(toggle).toBeVisible()
  await tabTo(page, toggle)
  await expect(toggle).toHaveCSS('outline-style', 'solid')
  await expect(toggle).toHaveCSS('outline-width', '2px')
  await expect(toggle).toHaveCSS('border-top-width', '1px')
  await page.goto('/items')
  const current = page.locator('nav a[aria-current="page"]')
  await expect(current).toHaveCSS('text-decoration-line', 'underline')
  await expect(current).toHaveCSS('border-top-width', '2px')
  expect(await page.evaluate(() => matchMedia('(forced-colors: active)').matches)).toBe(true)
})
