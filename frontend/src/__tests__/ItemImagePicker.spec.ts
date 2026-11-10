import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import ItemImagePicker from '@/components/ItemImagePicker.vue'
import { uploadImage, type ItemImage } from '@/api/items'

vi.mock('@/api/items', () => ({ uploadImage: vi.fn<typeof uploadImage>() }))
const image = (id: string): ItemImage => ({
  id,
  url: `/api/v1/images/${id}`,
  mediaType: 'image/png',
  sizeBytes: 10,
})
beforeEach(() => vi.resetAllMocks())

describe('物品图片选择', () => {
  it('第四张图片不发送上传请求', async () => {
    const wrapper = mount(ItemImagePicker, {
      props: { modelValue: [image('1'), image('2')] },
      global: { plugins: [ElementPlus] },
    })
    const input = wrapper.get('input')
    Object.defineProperty(input.element, 'files', {
      value: [new File(['a'], 'a.png'), new File(['b'], 'b.png')],
    })
    await input.trigger('change')
    expect(uploadImage).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('最多三张')
    wrapper.unmount()
  })

  it('超出 2 MB 的图片在上传前提示', async () => {
    const wrapper = mount(ItemImagePicker, {
      props: { modelValue: [] },
      global: { plugins: [ElementPlus] },
    })
    const input = wrapper.get('input')
    Object.defineProperty(input.element, 'files', {
      value: [new File([new Uint8Array(2097153)], 'large.png')],
    })
    await input.trigger('change')
    expect(uploadImage).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('不能超过 2 MB')
    wrapper.unmount()
  })

  it('部分上传失败时保留已成功图片并解除忙碌状态', async () => {
    vi.mocked(uploadImage)
      .mockResolvedValueOnce(image('10'))
      .mockRejectedValueOnce(new Error('失败'))
    const wrapper = mount(ItemImagePicker, {
      props: { modelValue: [] },
      global: { plugins: [ElementPlus] },
    })
    const input = wrapper.get('input')
    Object.defineProperty(input.element, 'files', {
      value: [new File(['a'], 'a.png'), new File(['b'], 'b.png')],
    })
    await input.trigger('change')
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([[image('10')]])
    expect(wrapper.emitted('busy')).toEqual([[true], [false]])
    expect((input.element as HTMLInputElement).disabled).toBe(false)
    wrapper.unmount()
  })
})
