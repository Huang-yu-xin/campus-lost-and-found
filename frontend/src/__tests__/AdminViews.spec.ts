import { beforeEach, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import {
  approveItem,
  getAdminItem,
  listAdminItems,
  listReviewRecords,
  returnItem,
  takeDownItem,
} from '@/api/admin'
import type { ItemRecord } from '@/api/items'
import { listAdminReports } from '@/api/reports'
import AdminHomeView from '@/views/admin/AdminHomeView.vue'
import AdminItemsView from '@/views/admin/AdminItemsView.vue'

const query: Record<string, string> = {}
vi.mock('vue-router', () => ({ useRoute: () => ({ query }) }))
vi.mock('@/api/admin', () => ({
  listAdminItems: vi.fn<typeof listAdminItems>(),
  getAdminItem: vi.fn<typeof getAdminItem>(),
  listReviewRecords: vi.fn<typeof listReviewRecords>(),
  approveItem: vi.fn<typeof approveItem>(),
  returnItem: vi.fn<typeof returnItem>(),
  takeDownItem: vi.fn<typeof takeDownItem>(),
}))
vi.mock('@/api/reports', () => ({ listAdminReports: vi.fn<typeof listAdminReports>() }))

function record(id: string): ItemRecord {
  return {
    id,
    type: 'FOUND',
    category: 'OTHER',
    location: '图书馆',
    occurredDate: '2026-11-01',
    title: `测试物品${id}`,
    description: '管理端测试使用的虚构物品描述',
    images: [],
    publisher: { id: '1', nickname: '发布人' },
    publishedAt: null,
    createdAt: '2026-11-01T00:00:00Z',
    hasConfirmedClaim: false,
    status: 'PENDING_REVIEW',
    version: 1,
    reviewReason: null,
    closureReason: null,
    updatedAt: '2026-11-01T00:00:00Z',
  }
}
const stubs = { RouterLink: { template: '<a><slot /></a>' } }

beforeEach(() => {
  vi.resetAllMocks()
  for (const key of Object.keys(query)) delete query[key]
  vi.mocked(listAdminItems).mockImplementation(async (status, page = 1, pageSize = 10) => ({
    items: [record(`${status}-${page}`)],
    page,
    pageSize,
    total: { PENDING_REVIEW: 23, RETURNED: 2, PUBLISHED: 40 }[status],
  }))
  vi.mocked(listAdminReports).mockResolvedValue({ items: [], page: 1, pageSize: 1, total: 4 })
})

it('概览显示各状态数量和等待最久的待审核信息', async () => {
  const wrapper = mount(AdminHomeView, { global: { plugins: [ElementPlus], stubs } })
  await flushPromises()
  const text = wrapper.text()
  expect(text).toContain('23待审核')
  expect(text).toContain('2已退回待修改')
  expect(text).toContain('40已发布')
  expect(text).toContain('4待处理举报')
  expect(text).toContain('测试物品PENDING_REVIEW-1')
  wrapper.unmount()
})

it('物品管理按页读取，可从链接带入状态', async () => {
  query.status = 'PUBLISHED'
  const wrapper = mount(AdminItemsView, { global: { plugins: [ElementPlus], stubs } })
  await flushPromises()
  expect(listAdminItems).toHaveBeenLastCalledWith('PUBLISHED', 1, 10)
  const pagination = wrapper.findComponent({ name: 'ElPagination' })
  pagination.vm.$emit('update:current-page', 3)
  pagination.vm.$emit('current-change', 3)
  await flushPromises()
  expect(listAdminItems).toHaveBeenLastCalledWith('PUBLISHED', 3, 10)
  expect(wrapper.text()).toContain('测试物品PUBLISHED-3')
  wrapper.unmount()
})
