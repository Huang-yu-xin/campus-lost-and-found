import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import ElementPlus from 'element-plus'
import { getItem, type PublicItem } from '@/api/items'
import { createClaim } from '@/api/claim'
import ItemDetailView from '@/views/item/ItemDetailView.vue'

const push = vi.fn<(location: unknown) => void>()
const auth = reactive({ isLoggedIn: true, isAdmin: false, me: { id: '2' } })
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { itemId: '42' }, fullPath: '/items/42' }),
  useRouter: () => ({ push }),
}))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => auth }))
vi.mock('@/api/items', () => ({ getItem: vi.fn<typeof getItem>() }))
vi.mock('@/api/claim', () => ({ createClaim: vi.fn<typeof createClaim>() }))
vi.mock('@/api/reports', () => ({
  createReport: vi.fn<typeof import('@/api/reports').createReport>(),
}))

const item: PublicItem = {
  id: '42',
  type: 'FOUND',
  category: 'OTHER',
  title: '测试水杯',
  location: '图书馆',
  occurredDate: '2026-11-01',
  description: '这是用于认领联调的虚构水杯描述',
  publisher: { id: '1', nickname: '发布人' },
  images: [],
  hasConfirmedClaim: false,
  publishedAt: '2026-11-01T00:00:00Z',
  createdAt: '2026-11-01T00:00:00Z',
}
beforeEach(() => {
  vi.resetAllMocks()
  Object.assign(auth, { isLoggedIn: true, isAdmin: false, me: { id: '2' } })
  vi.mocked(getItem).mockResolvedValue(item)
})
function mountPage() {
  return mount(ItemDetailView, {
    global: { plugins: [ElementPlus], stubs: { RouterLink: { template: '<a><slot /></a>' } } },
  })
}
describe('物品详情与认领页面衔接', () => {
  it('从乙的物品详情提交认领后跳转到丙的私有详情', async () => {
    vi.mocked(createClaim).mockResolvedValue({
      id: 7,
      itemId: 42,
      applicant: { id: 2, nickname: '申请人' },
      publisher: { id: 1, nickname: '发布人' },
      description: '杯子底部有一张测试标签和编号',
      status: 'PENDING',
      reason: null,
      version: 1,
      createdAt: '',
      updatedAt: '',
    })
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.get('#claim-description').setValue('杯子底部有一张测试标签和编号')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(createClaim).toHaveBeenCalledWith('42', '杯子底部有一张测试标签和编号')
    expect(push).toHaveBeenCalledWith({ name: 'claim-detail', params: { claimId: '7' } })
    wrapper.unmount()
  })
  it('物品发布者不能通过页面认领自己的信息', async () => {
    auth.me = { id: '1' }
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.text()).toContain('这是你发布的信息')
    wrapper.unmount()
  })
  it('未登录或已有确认认领人时不提供提交表单', async () => {
    auth.isLoggedIn = false
    const guest = mountPage()
    await flushPromises()
    expect(guest.text()).toContain('登录后申请认领')
    expect(guest.find('form').exists()).toBe(false)
    guest.unmount()
    auth.isLoggedIn = true
    vi.mocked(getItem).mockResolvedValue({ ...item, hasConfirmedClaim: true })
    const claimed = mountPage()
    await flushPromises()
    expect(claimed.text()).toContain('已确认认领人')
    expect(claimed.find('form').exists()).toBe(false)
    claimed.unmount()
  })
})
