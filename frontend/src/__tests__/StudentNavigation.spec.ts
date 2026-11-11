import { afterEach, expect, it, vi } from 'vitest'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'

import StudentLayout from '@/layouts/StudentLayout.vue'
import { useAuthStore } from '@/stores/auth'

afterEach(() => vi.unstubAllGlobals())

it('手机菜单支持展开、Escape 返回焦点，页面跳转后关闭且保留登录状态', async () => {
  vi.stubGlobal('matchMedia', () => ({
    matches: true,
    addEventListener: vi.fn<() => void>(),
    removeEventListener: vi.fn<() => void>(),
  }))
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { template: '<p>首页</p>' } },
      ...[
        'items',
        'item-publish',
        'my-items',
        'my-claims',
        'claim-received',
        'my-reports',
        'my-clues',
        'profile',
      ].map((name) => ({
        path: `/${name}`,
        name,
        component: { template: '<p>记录</p>' },
      })),
    ],
  })
  await router.push('/')
  const pinia = createPinia()
  const auth = useAuthStore(pinia)
  auth.setMe({
    id: '1',
    studentId: '1',
    realName: '测试甲',
    campusCardNo: '1',
    nickname: '测试用户',
    email: 'test@example.com',
    phone: null,
    role: 'STUDENT',
    version: 1,
  })
  const wrapper = mount(StudentLayout, {
    attachTo: document.body,
    global: { plugins: [pinia, router, ElementPlus] },
  })
  const toggle = wrapper.get('button[aria-controls="student-navigation"]')
  expect(toggle.attributes('aria-expanded')).toBe('false')
  await toggle.trigger('click')
  expect(toggle.attributes('aria-expanded')).toBe('true')
  await wrapper.get('#student-navigation').trigger('keydown', { key: 'Escape' })
  expect(toggle.attributes('aria-expanded')).toBe('false')
  expect(document.activeElement).toBe(toggle.element)
  await toggle.trigger('click')
  await router.push('/my-claims')
  await flushPromises()
  expect(toggle.attributes('aria-expanded')).toBe('false')
  expect(auth.me?.nickname).toBe('测试用户')
  wrapper.unmount()
})
