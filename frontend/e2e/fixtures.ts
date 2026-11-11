import { expect, test as base, type Page } from '@playwright/test'
import type { Me } from '../src/types/domain'

export const longNickname = '用于手机适配检查的超长用户昵称及用户身份展示'
export const timestamp = '2026-11-10T08:00:00Z'
export const student: Me = {
  id: '1',
  studentId: '20260001',
  realName: '测试甲',
  campusCardNo: '10001',
  nickname: longNickname,
  email: 'a-very-long-email-address-for-layout@example.edu.cn',
  phone: null,
  role: 'STUDENT',
  version: 1,
}
const image = { id: '1', url: '/layout-test-image.svg', mediaType: 'image/svg+xml', sizeBytes: 200 }
export const item = {
  id: '42',
  type: 'FOUND',
  category: 'OTHER',
  title: '长标题测试物品'.repeat(5),
  location: '这是很长的发生地点用于验证换行'.repeat(3),
  occurredDate: '2026-11-01',
  description: '用于适配测试的物品描述。'.repeat(20),
  imageIds: ['1'],
  images: [image, { ...image, id: '2' }, { ...image, id: '3' }],
  publisher: { id: '1', nickname: longNickname },
  publishedAt: timestamp,
  createdAt: timestamp,
  hasConfirmedClaim: false,
  status: 'PUBLISHED',
  version: 1,
  reviewReason: null,
  closureReason: null,
  updatedAt: timestamp,
}
const claim = {
  id: '7',
  itemId: '42',
  applicant: { id: '2', nickname: longNickname },
  publisher: item.publisher,
  description: '手机端也需要完整显示的认领特征说明。'.repeat(8),
  status: 'CONFIRMED',
  reason: null,
  version: 1,
  createdAt: timestamp,
  updatedAt: timestamp,
}
const clue = {
  id: '9',
  lostItemId: '43',
  provider: claim.applicant,
  publisher: item.publisher,
  content: '用于窄屏测试的线索内容和超长英文字符串：' + 'LongUnbrokenWord'.repeat(12),
  createdAt: timestamp,
}
const report = {
  id: '8',
  itemId: '42',
  claimId: '7',
  clueId: null,
  reason: '用于检查排版的举报理由。'.repeat(12),
  status: 'PENDING',
  action: null,
  resolution: null,
  version: 1,
  createdAt: timestamp,
  resolvedAt: null,
}

export interface MockState {
  role: 'STUDENT' | 'ADMIN' | 'ANONYMOUS'
  mode: 'normal' | 'empty' | 'error'
  requests: { method: string; path: string; query: string; body: unknown }[]
  unhandled: string[]
}

async function installApiMock(page: Page): Promise<MockState> {
  const state: MockState = { role: 'STUDENT', mode: 'normal', requests: [], unhandled: [] }
  await page.route('**/layout-test-image.svg', (route) =>
    route.fulfill({
      contentType: 'image/svg+xml',
      body: '<svg xmlns="http://www.w3.org/2000/svg" width="320" height="240"><rect width="320" height="240" fill="#e8f2fb"/><text x="90" y="125" font-size="24">测试物品图片</text></svg>',
    }),
  )
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/api/v1', '')
    const method = request.method()
    const body =
      request.postData() && request.headers()['content-type']?.includes('application/json')
        ? request.postDataJSON()
        : null
    state.requests.push({ method, path, query: url.search, body })
    const reply = (data: unknown, status = 200) =>
      route.fulfill({
        status,
        json: { code: 'OK', message: '成功', data, requestId: 'responsive-fixture' },
      })
    const failure = (status: number, code: string, message: string) =>
      route.fulfill({ status, json: { code, message, requestId: 'responsive-fixture' } })
    const pageNumber = Number(url.searchParams.get('page') || 1)
    const pageSize = Number(url.searchParams.get('pageSize') || 10)
    const list = (row: Record<string, unknown>) => {
      const total = state.mode === 'empty' ? 0 : 71
      return {
        items: Array.from(
          { length: Math.max(0, Math.min(pageSize, total - (pageNumber - 1) * pageSize)) },
          (_, i) => ({ ...row, id: String(Number(row.id) + (pageNumber - 1) * pageSize + i) }),
        ),
        page: pageNumber,
        pageSize,
        total,
      }
    }
    if (path === '/auth/csrf')
      return reply({ token: 'responsive-fixture-token', headerName: 'X-CSRF-TOKEN' })
    if (path === '/auth/login') {
      state.role = 'STUDENT'
      return reply(student)
    }
    if (path === '/auth/logout') {
      state.role = 'ANONYMOUS'
      return reply(null)
    }
    if (path === '/me' && state.role === 'ANONYMOUS')
      return failure(401, 'UNAUTHENTICATED', '请先登录')
    if (path === '/me')
      return reply({ ...student, role: state.role, ...(method === 'PATCH' ? body : {}) })
    if (state.mode === 'error')
      return failure(503, 'SERVICE_UNAVAILABLE', '布局测试：服务暂不可用，请稍后重试。'.repeat(8))
    if (path.includes('email-codes'))
      return reply({ deliveryMode: 'DEV_FILE', retryAfterSeconds: 60, expiresInSeconds: 300 })
    if (path === '/auth/register') return reply(student, 201)
    if (path === '/me/password') {
      state.role = 'ANONYMOUS'
      return reply(null)
    }
    if (path === '/me/email') return reply({ ...student, email: body.email, version: 2 })
    if (['/me/claims', '/me/received-claims'].includes(path)) return reply(list(claim))
    if (['/me/clues', '/me/received-clues'].includes(path)) return reply(list(clue))
    if (['/me/reports', '/admin/reports'].includes(path))
      return reply(list({ ...report, status: url.searchParams.get('status') || report.status }))
    if (['/items', '/me/items', '/admin/items'].includes(path) && method === 'GET')
      return reply(list({ ...item, status: url.searchParams.get('status') || item.status }))
    if (/^\/(me\/|admin\/)?items\/\d+$/.test(path))
      return reply(method === 'GET' ? item : { ...item, ...body })
    if (path === '/items' && method === 'POST')
      return reply({ ...item, ...body, status: 'PENDING_REVIEW' }, 201)
    if (/^\/claims\/\d+$/.test(path)) return reply({ claim, item })
    if (/^\/clues\/\d+$/.test(path))
      return reply({ clue, item: { ...item, id: '43', type: 'LOST' } })
    if (/^\/claims\/\d+\/contact-permission$/.test(path))
      return reply({
        claimId: '7',
        phoneVisible: false,
        emailVisible: false,
        effective: true,
        version: 1,
        updatedAt: timestamp,
      })
    if (path.endsWith('/counterparty-contact')) return reply({ claimId: '7', email: student.email })
    if (/\/(claims|clues)\/\d+\/messages$/.test(path)) {
      const message = {
        id: '1',
        sender: claim.applicant,
        content: `第${pageNumber}页留言：` + '用于验证长留言在手机上换行'.repeat(12),
        createdAt: timestamp,
      }
      return reply(method === 'GET' ? list(message) : { ...message, content: body.content })
    }
    if (/^\/admin\/reports\/\d+$/.test(path)) return reply(report)
    if (path.endsWith('/conversation'))
      return reply({
        kind: 'CLAIM',
        contextId: '7',
        evidenceText: claim.description,
        ...list({
          id: '1',
          sender: claim.applicant,
          content: '会话内容'.repeat(50),
          createdAt: timestamp,
        }),
      })
    if (path.endsWith('/review-records'))
      return reply(
        list({
          id: '1',
          itemId: '42',
          action: 'RETURN',
          reason: '审核原因'.repeat(20),
          operatorId: '3',
          createdAt: timestamp,
        }),
      )
    if (path.endsWith('/resolve'))
      return reply({ ...report, status: 'RESOLVED', resolution: body.reason })
    if (path === '/reports') return reply(report, 201)
    if (path === '/uploads/images') return reply(image)
    state.unhandled.push(`${method} ${path}`)
    return failure(404, 'NOT_FOUND', `未定义的布局测试接口 ${path}`)
  })
  return state
}

export const test = base.extend<{ mock: MockState }>({
  mock: async ({ page }, use) => {
    const pageErrors: string[] = []
    page.on('pageerror', (error) => pageErrors.push(error.message))
    const state = await installApiMock(page)
    await use(state)
    expect(state.unhandled, '布局测试不能静默忽略未知接口').toEqual([])
    expect(pageErrors, '浏览器不能出现未处理的脚本错误').toEqual([])
  },
})
export { expect }
