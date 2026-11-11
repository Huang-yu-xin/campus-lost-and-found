import { test, expect } from './fixtures'

test.use({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })

test('忽略媒体查询新范围语法时，手机导航与个人记录仍使用窄屏布局', async ({ page, mock }) => {
  mock.role = 'STUDENT'
  let unsupportedQueries = 0
  await page.route('**/*.css', async (route) => {
    const response = await route.fetch()
    // 模拟旧 CSS 解析器：只忽略含比较运算符的媒体查询，保留传统 min/max-width。
    const body = (await response.text()).replace(/@media[^{}]*[<>]=?[^{}]*\{/g, () => {
      unsupportedQueries += 1
      return '@media not all{'
    })
    await route.fulfill({ response, body })
  })
  await page.goto('/')
  const toggle = page.getByRole('button', { name: '展开菜单' })
  await expect(toggle).toBeVisible()
  expect((await page.locator('.site-header').boundingBox())!.height).toBeLessThan(200)
  await toggle.tap()
  await page
    .getByRole('navigation', { name: '学生端导航' })
    .getByRole('link', { name: '我申请的认领', exact: true })
    .tap()
  await expect(page.locator('.mobile-records')).toBeVisible()
  await expect(page.locator('.desktop-records')).toBeHidden()
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth - innerWidth))
    .toBeLessThanOrEqual(1)
  await page.goto('/me/profile')
  await expect(page.getByRole('textbox', { name: /新邮箱$/ })).toBeVisible()
  expect(unsupportedQueries, '构建中不能重新引入新媒体查询范围语法').toBe(0)
})
