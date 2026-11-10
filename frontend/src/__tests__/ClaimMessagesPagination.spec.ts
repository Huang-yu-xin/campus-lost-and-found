import { beforeEach, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { getClaim, getContactPermission, listMessages, sendMessage } from '@/api/claim'
import ClaimDetailView from '@/views/claim/ClaimDetailView.vue'
vi.mock('vue-router', () => ({ useRoute: () => ({ params: { claimId: '7' } }) }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ me: { id: '2' } }) }))
vi.mock('@/api/claim', () => ({
  getClaim: vi.fn<typeof getClaim>(),
  getContactPermission: vi.fn<typeof getContactPermission>(),
  listMessages: vi.fn<typeof listMessages>(),
  sendMessage: vi.fn<typeof sendMessage>(),
}))
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(getClaim).mockResolvedValue({
    claim: {
      id: 7,
      itemId: 42,
      applicant: { id: 2, nickname: '申请人' },
      publisher: { id: 1, nickname: '发布人' },
      description: '物品描述',
      status: 'PENDING',
      reason: null,
      version: 1,
      createdAt: '',
      updatedAt: '',
    },
    item: {
      id: 42,
      type: 'FOUND',
      category: 'OTHER',
      location: '图书馆',
      title: '水杯',
      description: '描述',
      status: 'PUBLISHED',
      occurredDate: null,
      publishedAt: null,
      createdAt: '',
    },
  })
  vi.mocked(getContactPermission).mockResolvedValue({
    claimId: 7,
    phoneVisible: false,
    emailVisible: false,
    effective: false,
    version: 0,
    updatedAt: null,
  })
  vi.mocked(listMessages).mockImplementation(async (_, page = 1) => ({
    items: [
      {
        id: page,
        sender: { id: 2, nickname: '申请人' },
        content: `第${page}页留言`,
        createdAt: '2026-11-10T00:00:00Z',
      },
    ],
    page,
    pageSize: 10,
    total: 61,
  }))
})
it('超过50条留言仍能翻到末页，发送后展示最新留言所在页', async () => {
  const wrapper = mount(ClaimDetailView, {
    global: { plugins: [ElementPlus], stubs: { RouterLink: { template: '<a><slot /></a>' } } },
  })
  await flushPromises()
  expect(wrapper.text()).toContain('第1页留言')
  const pagination = wrapper.findComponent({ name: 'ElPagination' })
  pagination.vm.$emit('update:current-page', 7)
  pagination.vm.$emit('current-change', 7)
  await flushPromises()
  expect(listMessages).toHaveBeenLastCalledWith(7, 7, 10)
  expect(wrapper.text()).toContain('第7页留言')
  await wrapper.get('textarea').setValue('新的留言')
  const send = wrapper.findAll('button').find((button) => button.text().includes('发送'))!
  await send.trigger('click')
  await flushPromises()
  expect(sendMessage).toHaveBeenCalledWith(7, '新的留言')
  expect(listMessages).toHaveBeenLastCalledWith(7, 7, 10)
  wrapper.unmount()
})
