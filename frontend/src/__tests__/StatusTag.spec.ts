import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'

import StatusTag from '@/components/StatusTag.vue'

describe('StatusTag', () => {
  it('显示物品状态的中文名称', () => {
    const wrapper = mount(StatusTag, {
      props: { status: 'PENDING_REVIEW' },
      global: { plugins: [ElementPlus] },
    })
    expect(wrapper.text()).toBe('待审核')
  })

  it('显示认领状态的中文名称', () => {
    const wrapper = mount(StatusTag, {
      props: { status: 'COMPLETED' },
      global: { plugins: [ElementPlus] },
    })
    expect(wrapper.text()).toBe('已完成交接')
  })
})
