import { beforeEach, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { listOwnItems, withdrawItem } from '@/api/items'
import { resolveLostItem } from '@/api/clues'
import MyItemsView from '@/views/item/MyItemsView.vue'
vi.mock('@/api/items', () => ({
  listOwnItems: vi.fn<typeof listOwnItems>(),
  withdrawItem: vi.fn<typeof withdrawItem>(),
}))
vi.mock('@/api/clues', () => ({ resolveLostItem: vi.fn<typeof resolveLostItem>() }))
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(listOwnItems).mockImplementation(async (page) => ({
    items: [],
    page,
    pageSize: 10,
    total: 25,
  }))
})
it('按状态筛选我的发布，切换后回到第一页，清空后查询全部', async () => {
  const wrapper = mount(MyItemsView, {
    global: { plugins: [ElementPlus], stubs: { RouterLink: { template: '<a><slot /></a>' } } },
  })
  await flushPromises()
  expect(listOwnItems).toHaveBeenLastCalledWith(1, undefined)
  const pagination = wrapper.findComponent({ name: 'ElPagination' })
  pagination.vm.$emit('update:current-page', 3)
  pagination.vm.$emit('current-change', 3)
  await flushPromises()
  expect(listOwnItems).toHaveBeenLastCalledWith(3, undefined)
  const select = wrapper.findComponent({ name: 'ElSelect' })
  select.vm.$emit('update:modelValue', 'ARCHIVED')
  await flushPromises()
  expect(listOwnItems).toHaveBeenLastCalledWith(1, 'ARCHIVED')
  expect(wrapper.text()).toContain('暂无已归档的发布记录。')
  select.vm.$emit('update:modelValue', '')
  await flushPromises()
  expect(listOwnItems).toHaveBeenLastCalledWith(1, undefined)
  wrapper.unmount()
})
