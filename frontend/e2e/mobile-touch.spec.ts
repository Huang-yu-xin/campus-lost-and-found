import { test, expect } from './fixtures'

// 触屏与移动视口模拟，不能代替实体手机和系统软键盘验收。
test.use({ isMobile: true, hasTouch: true, viewport: { width: 390, height: 844 } })

test('触屏模拟下菜单、个人卡片与详情可达，横竖屏切换保留草稿', async ({ page, mock }) => {
  mock.role = 'STUDENT'
  await page.goto('/me/items')
  const toggle = page.getByRole('button', { name: '展开菜单' })
  await expect(toggle).toBeVisible()
  expect((await toggle.boundingBox())!.height).toBeGreaterThanOrEqual(44)
  await toggle.tap()
  const navigation = page.getByRole('navigation', { name: '学生端导航' })
  await navigation.getByRole('link', { name: '我的线索', exact: true }).tap()
  await expect(page).toHaveURL(/\/me\/clues$/)
  await expect(navigation).toBeHidden()
  const detail = page.locator('.mobile-records').getByRole('link', { name: '查看 / 留言' }).first()
  await expect(detail).toBeVisible()
  expect((await detail.boundingBox())!.height).toBeGreaterThanOrEqual(44)
  await detail.tap()
  await expect(page).toHaveURL(/\/clues\/\d+$/)
  await page.getByRole('textbox').fill('触屏模式下尚未发送的留言')
  await page.setViewportSize({ width: 844, height: 390 })
  await expect(page.getByRole('textbox')).toHaveValue('触屏模式下尚未发送的留言')
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.getByRole('textbox')).toHaveValue('触屏模式下尚未发送的留言')
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth - innerWidth))
    .toBeLessThanOrEqual(1)
})
