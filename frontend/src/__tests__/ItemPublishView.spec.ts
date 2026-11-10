import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import ElementPlus, { ElMessageBox } from 'element-plus'
import { createItem, editItem, getOwnItem, type ItemRecord } from '@/api/items'
import ItemPublishView from '@/views/item/ItemPublishView.vue'

const route = reactive({
  name: 'item-publish',
  fullPath: '/items/new',
  params: {} as Record<string, string>,
})
vi.mock('vue-router', () => ({ useRoute: () => route }))
vi.mock('@/api/items', () => ({
  createItem: vi.fn<typeof createItem>(),
  editItem: vi.fn<typeof editItem>(),
  getOwnItem: vi.fn<typeof getOwnItem>(),
  uploadImage: vi.fn<typeof import('@/api/items').uploadImage>(),
}))
const record: ItemRecord = {
  id: '42',
  type: 'FOUND',
  category: 'OTHER',
  title: '黑色水杯',
  location: '图书馆',
  occurredDate: '2026-10-01',
  description: '发现一个带有标识的黑色物品',
  images: [],
  publisher: { id: '1', nickname: '测试' },
  publishedAt: null,
  createdAt: '2026-10-01T00:00:00Z',
  hasConfirmedClaim: false,
  status: 'PENDING_REVIEW',
  version: 3,
  reviewReason: null,
  closureReason: null,
  updatedAt: '2026-10-01T00:00:00Z',
}
beforeEach(() => {
  vi.resetAllMocks()
  Object.assign(route, { name: 'item-publish', fullPath: '/items/new', params: {} })
  vi.spyOn(ElMessageBox, 'alert').mockResolvedValue(
    'confirm' as Awaited<ReturnType<typeof ElMessageBox.alert>>,
  )
})
function mountPage() {
  return mount(ItemPublishView, { global: { plugins: [ElementPlus], stubs: { RouterLink: true } } })
}

describe('物品提交页面', () => {
  it('请求未结束时阻止重复提交，失败后重试沿用原编号', async () => {
    let rejectRequest: (reason: Error) => void = () => {}
    vi.mocked(createItem)
      .mockReturnValueOnce(
        new Promise((_, reject) => {
          rejectRequest = reject
        }),
      )
      .mockResolvedValueOnce(record)
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.get('#item-title').setValue(record.title)
    await wrapper.get('#item-location').setValue(record.location)
    await wrapper.get('#item-date').setValue(record.occurredDate)
    await wrapper.get('#item-description').setValue(record.description)
    await wrapper.get('form').trigger('submit')
    await wrapper.get('form').trigger('submit')
    expect(createItem).toHaveBeenCalledTimes(1)
    expect((wrapper.get('button[type="submit"]').element as HTMLButtonElement).disabled).toBe(true)
    rejectRequest(new Error('网络断开'))
    await flushPromises()
    expect((wrapper.get('#item-title').element as HTMLInputElement).value).toBe(record.title)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(createItem).toHaveBeenCalledTimes(2)
    expect(vi.mocked(createItem).mock.calls[0]?.[1]).toBe(vi.mocked(createItem).mock.calls[1]?.[1])
    expect(wrapper.text()).toContain('已提交')
    expect(wrapper.find('form').exists()).toBe(false)
    wrapper.unmount()
  })

  it('编辑携带版本号并锁定信息类型', async () => {
    Object.assign(route, {
      name: 'item-edit',
      fullPath: '/me/items/42/edit',
      params: { itemId: '42' },
    })
    vi.mocked(getOwnItem).mockResolvedValue(record)
    vi.mocked(editItem).mockResolvedValue({ ...record, version: 4 })
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.get('#item-title').setValue('修改后的水杯')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(editItem).toHaveBeenCalledWith(
      '42',
      expect.objectContaining({ expectedVersion: 3, title: '修改后的水杯' }),
    )
    expect(vi.mocked(editItem).mock.calls[0]?.[1]).not.toHaveProperty('type')
    expect(createItem).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('下架信息不显示可提交的编辑表单', async () => {
    Object.assign(route, {
      name: 'item-edit',
      fullPath: '/me/items/42/edit',
      params: { itemId: '42' },
    })
    vi.mocked(getOwnItem).mockResolvedValue({ ...record, status: 'OFF_SHELF' })
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.text()).toContain('不能修改')
    wrapper.unmount()
  })
})
