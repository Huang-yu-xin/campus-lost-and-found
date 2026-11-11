import { test, expect, longNickname, student } from './fixtures'
import type { Page, TestInfo } from '@playwright/test'

async function noOverflow(page: Page) {
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth))
    .toBeLessThanOrEqual(1)
}

async function capture(page: Page, testInfo: TestInfo, label: string) {
  // Element Plus 的消息在鼠标悬停时暂停关闭；移开指针后等待真实消失。
  await page.mouse.move(0, (page.viewportSize()?.height ?? 900) - 1)
  await expect(page.locator('.el-message')).toHaveCount(0)
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }))
  await testInfo.attach(label, {
    body: await page.screenshot({ fullPage: false, animations: 'disabled' }),
    contentType: 'image/png',
  })
}

const routes = [
  ['/', '校园失物招领系统'],
  ['/items', '浏览物品'],
  ['/items/new', '发布物品信息'],
  ['/me/items/42/edit', '修改物品信息'],
  ['/items/42', '长标题测试物品'],
  ['/me/items', '我的发布'],
  ['/me/claims', '我申请的认领'],
  ['/me/received-claims', '我收到的认领申请'],
  ['/me/clues', '我的线索'],
  ['/reports', '我的举报'],
  ['/me/profile', '个人资料'],
  ['/claims/7', '认领详情'],
  ['/clues/9', '长标题测试物品'],
] as const

for (const width of [360, 390, 414, 640, 641, 768, 769, 1024, 1440]) {
  test(`学生页面 ${width}px 不产生整页横向溢出`, async ({ page, mock }, testInfo) => {
    mock.role = 'STUDENT'
    await page.setViewportSize({ width, height: 900 })
    for (const [url, heading] of routes) {
      await test.step(url, async () => {
        await page.goto(url)
        await expect(page.locator('h1')).toContainText(heading)
        await expect(page.locator('.el-skeleton')).toHaveCount(0)
        await noOverflow(page)
        if (url === '/me/claims') {
          await expect(
            page.locator(width <= 640 ? '.mobile-records' : '.desktop-records'),
          ).toBeVisible()
          await expect(
            page.locator(width <= 640 ? '.desktop-records' : '.mobile-records'),
          ).toBeHidden()
          if ([390, 1440].includes(width)) await capture(page, testInfo, `claims-${width}`)
        }
      })
    }
  })
}

test('手机菜单支持触控、键盘关闭和跳转，旋转后不会留下不可见入口', async ({
  page,
  mock,
}, testInfo) => {
  mock.role = 'STUDENT'
  await page.setViewportSize({ width: 360, height: 800 })
  await page.goto('/me/items')
  const button = page.getByRole('button', { name: '展开菜单' })
  const navigation = page.getByRole('navigation', { name: '学生端导航' })
  await expect(navigation).toBeHidden()
  if (testInfo.project.use.hasTouch) await button.tap()
  else await button.click()
  await expect(navigation).toBeVisible()
  await navigation.getByRole('link', { name: '我申请的认领', exact: true }).focus()
  await page.keyboard.press('Escape')
  await expect(button).toBeFocused()
  await expect(navigation).toBeHidden()
  await button.click()
  await navigation.getByRole('link', { name: '我申请的认领', exact: true }).click()
  await expect(page).toHaveURL(/\/me\/claims$/)
  await expect(navigation).toBeHidden()
  await page.setViewportSize({ width: 1440, height: 900 })
  await expect(navigation).toBeVisible()
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(navigation).toBeHidden()
  await noOverflow(page)
})

test('记录切换卡片与表格不重新查询，筛选与页码保持且详情可达', async ({ page, mock }) => {
  mock.role = 'STUDENT'
  await page.goto('/me/claims')
  await page.getByRole('combobox', { name: '认领状态' }).focus()
  await page.keyboard.press('ArrowDown')
  await page.getByRole('option', { name: '已确认', exact: true }).click()
  await page
    .locator('.el-pagination')
    .getByRole('button', { name: /下一页/ })
    .click()
  await expect
    .poll(() => mock.requests.filter((r) => r.path === '/me/claims').at(-1)?.query)
    .toContain('page=2')
  const requestCount = mock.requests.length
  await page.setViewportSize({ width: 360, height: 800 })
  await expect(page.locator('.mobile-records')).toBeVisible()
  await expect(
    page.locator('.el-select__selected-item').filter({ hasText: '已确认' }),
  ).toBeVisible()
  await expect(page.locator('.el-pagination .is-active')).toHaveText('2')
  await page.setViewportSize({ width: 1440, height: 900 })
  await expect(page.locator('.desktop-records')).toBeVisible()
  expect(mock.requests.length).toBe(requestCount)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.locator('.mobile-records').getByRole('link', { name: '查看 / 处理' }).first().click()
  await expect(page).toHaveURL(/\/claims\/\d+$/)
  await expect(page.getByText('会话留言', { exact: true })).toBeVisible()
})

for (const url of ['/me/clues', '/reports']) {
  test(`${url} 的手机卡片保留正文、状态或方向及分页`, async ({ page, mock }) => {
    mock.role = 'STUDENT'
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto(url)
    await expect(page.locator('.mobile-records')).toBeVisible()
    await expect(page.locator('.desktop-records')).toBeHidden()
    await expect(page.locator('.mobile-records .record-card').first()).toContainText(
      url.includes('clues') ? 'LongUnbrokenWord' : '等待管理员处理',
    )
    if (url.includes('clues')) {
      await page.getByText('我收到的线索', { exact: true }).click()
      await expect.poll(() => mock.requests.some((r) => r.path === '/me/received-clues')).toBe(true)
      await expect(page.locator('.mobile-records .record-card').first()).toContainText('提供者')
    }
    await page
      .locator('.el-pagination')
      .getByRole('button', { name: /下一页/ })
      .click()
    await expect(page.locator('.el-pagination .is-active')).toHaveText('2')
    await noOverflow(page)
  })
}

test('个人资料的输入、验证码倒计时与保存操作跨宽度保持', async ({ page, mock }, testInfo) => {
  mock.role = 'STUDENT'
  await page.setViewportSize({ width: 360, height: 800 })
  await page.goto('/me/profile')
  const nickname = page.getByRole('textbox', { name: '昵称（公开显示）' })
  await nickname.fill('修改后尚未保存的昵称')
  await page.getByRole('textbox', { name: /新邮箱$/ }).fill('layout-test@example.com')
  await page.getByRole('button', { name: '获取验证码' }).click()
  await expect(page.getByRole('button', { name: /秒后重发/ })).toBeDisabled()
  await page.setViewportSize({ width: 1440, height: 900 })
  await expect(nickname).toHaveValue('修改后尚未保存的昵称')
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.getByRole('textbox', { name: /新邮箱$/ })).toHaveValue(
    'layout-test@example.com',
  )
  await expect(page.getByRole('button', { name: /秒后重发/ })).toBeDisabled()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect
    .poll(() => mock.requests.find((r) => r.method === 'PATCH' && r.path === '/me')?.body)
    .toMatchObject({ nickname: '修改后尚未保存的昵称' })
  await noOverflow(page)
  await capture(page, testInfo, 'profile-390')
})

test('手机注册校验、验证码和登录跳转可操作，输入跨宽度保持', async ({ page, mock }, testInfo) => {
  mock.role = 'ANONYMOUS'
  await page.setViewportSize({ width: 360, height: 800 })
  await page.goto('/register')
  await page.getByRole('button', { name: '注册', exact: true }).click()
  await expect(page.getByText('请输入学号', { exact: true })).toBeVisible()
  await page.getByRole('textbox', { name: /学号$/ }).fill('20260001')
  await page.getByRole('textbox', { name: /姓名$/ }).fill('测试甲')
  await page.getByRole('textbox', { name: /校园卡号$/ }).fill('10001')
  await page.getByRole('textbox', { name: /邮箱$/ }).fill(student.email)
  await page.getByRole('button', { name: '获取验证码' }).click()
  await expect(page.getByRole('button', { name: /秒后重发/ })).toBeDisabled()
  await page.getByRole('textbox', { name: /验证码$/ }).fill('123456')
  await page.locator('input[autocomplete="new-password"]').first().fill('LayoutTest123')
  await page.locator('input[autocomplete="new-password"]').last().fill('LayoutTest123')
  await page.setViewportSize({ width: 1440, height: 900 })
  await expect(page.getByRole('textbox', { name: /学号$/ })).toHaveValue('20260001')
  await page.setViewportSize({ width: 390, height: 844 })
  await noOverflow(page)
  await page.locator('h1').click()
  await expect(page.locator('.el-form-item__error')).toHaveCount(0)
  await capture(page, testInfo, 'register-390')
  await page.getByRole('button', { name: '注册', exact: true }).click()
  await expect(page).toHaveURL(/\/login\?account=20260001/)
  await page.locator('input[autocomplete="current-password"]').fill('LayoutTest123')
  await page.getByRole('button', { name: '登录', exact: true }).last().click()
  await expect(page.getByRole('link', { name: longNickname, exact: true })).toBeVisible()
})

test('留言输入与页码跨宽度保持，发送只调用一次', async ({ page, mock }) => {
  mock.role = 'STUDENT'
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/claims/7')
  await page.getByRole('textbox').fill('适配检查的留言草稿')
  await page
    .locator('.el-pagination')
    .getByRole('button', { name: /下一页/ })
    .click()
  await expect(page.locator('.el-pagination .is-active')).toHaveText('2')
  await page.setViewportSize({ width: 1440, height: 900 })
  await expect(page.getByRole('textbox')).toHaveValue('适配检查的留言草稿')
  await expect(page.locator('.el-pagination .is-active')).toHaveText('2')
  await page.setViewportSize({ width: 360, height: 800 })
  await page.getByRole('button', { name: '发送留言', exact: true }).click()
  await expect
    .poll(
      () =>
        mock.requests.filter((r) => r.method === 'POST' && r.path === '/claims/7/messages').length,
    )
    .toBe(1)
  await noOverflow(page)
})

for (const width of [360, 390, 768, 769, 1440]) {
  test(`管理端 ${width}px 表格局部滚动，详情、会话与处理弹层可操作`, async ({
    page,
    mock,
  }, testInfo) => {
    mock.role = 'ADMIN'
    await page.setViewportSize({ width, height: 900 })
    await page.goto('/admin')
    await expect(page.locator('.stats')).toBeVisible()
    await noOverflow(page)
    await page.goto('/admin/items')
    await expect(page.locator('.el-table__body')).toBeVisible()
    await noOverflow(page)
    const wrap = page.locator('.el-table__body-wrapper .el-scrollbar__wrap')
    await wrap.evaluate((el) => {
      el.scrollLeft = el.scrollWidth
    })
    await page.getByRole('button', { name: '查看详情', exact: true }).first().click()
    let dialog = page.getByRole('dialog', { name: '物品审核详情' })
    await expect(dialog).toBeVisible()
    await expect
      .poll(() =>
        dialog.evaluate(
          (el) =>
            el.getBoundingClientRect().right <= innerWidth + 1 &&
            el.getBoundingClientRect().left >= -1,
        ),
      )
      .toBe(true)
    await dialog.getByRole('button', { name: /关闭|Close/ }).click()
    await page.goto('/admin/reports')
    await expect(page.locator('.el-table__body')).toBeVisible()
    await noOverflow(page)
    await page.locator('.el-table__body-wrapper .el-scrollbar__wrap').evaluate((el) => {
      el.scrollLeft = el.scrollWidth
    })
    await page.getByRole('button', { name: '详情', exact: true }).first().click()
    const drawer = page.getByRole('dialog', { name: '举报详情' })
    await expect(drawer).toBeVisible()
    await expect
      .poll(() =>
        drawer.evaluate(
          (el) =>
            el.getBoundingClientRect().left >= -1 &&
            el.getBoundingClientRect().right <= innerWidth + 1,
        ),
      )
      .toBe(true)
    await drawer.getByRole('button', { name: /关闭|Close/ }).click()
    await expect(drawer).toBeHidden()
    await page.getByRole('button', { name: '会话', exact: true }).first().click()
    dialog = page.getByRole('dialog', { name: '举报关联会话' })
    await expect(dialog.locator('.message-list')).toBeVisible()
    await expect
      .poll(() =>
        dialog.evaluate(
          (el) =>
            el.getBoundingClientRect().right <= innerWidth + 1 &&
            el.scrollWidth <= el.clientWidth + 1,
        ),
      )
      .toBe(true)
    await dialog.getByRole('button', { name: '关闭', exact: true }).click()
    await page.getByRole('button', { name: '处理', exact: true }).first().click()
    dialog = page.getByRole('dialog', { name: '处理举报' })
    await expect(dialog).toBeVisible()
    await dialog.getByRole('textbox', { name: '处理结论' }).fill('这是适配测试的处理结论')
    if ([390, 1440].includes(width)) await capture(page, testInfo, `admin-dialog-${width}`)
    await expect
      .poll(() =>
        dialog.evaluate(
          (el) =>
            el.getBoundingClientRect().right <= innerWidth + 1 &&
            el.scrollWidth <= el.clientWidth + 1,
        ),
      )
      .toBe(true)
    await dialog.getByRole('button', { name: '提交处理', exact: true }).click()
    await expect
      .poll(() => mock.requests.some((r) => r.method === 'POST' && r.path.endsWith('/resolve')))
      .toBe(true)
  })
}

for (const mode of ['empty', 'error'] as const) {
  test(`手机 ${mode} 状态内容不溢出且不显示旧记录`, async ({ page, mock }) => {
    mock.role = 'STUDENT'
    mock.mode = mode
    await page.setViewportSize({ width: 360, height: 800 })
    await page.goto('/me/claims')
    await expect(
      page.getByText(
        mode === 'empty'
          ? '暂无符合条件的认领申请'
          : '布局测试：服务暂不可用，请稍后重试。'.repeat(8),
        { exact: true },
      ),
    ).toBeVisible()
    await expect(page.locator('.record-card')).toHaveCount(0)
    await noOverflow(page)
  })
}
