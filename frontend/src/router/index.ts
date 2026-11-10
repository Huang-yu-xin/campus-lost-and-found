import { createRouter, createWebHistory } from 'vue-router'

import StudentLayout from '@/layouts/StudentLayout.vue'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    /** 需要登录才能访问。 */
    requiresAuth?: boolean
    /** 需要管理员角色。 */
    requiresAdmin?: boolean
  }
}

// 页面路由清单见 docs/开发实施手册.md 9.1；各负责人在对应布局的 children 中添加自己的页面
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: StudentLayout,
      children: [
        { path: '', name: 'home', component: () => import('@/views/HomeView.vue') },
        {
          path: 'claims',
          name: 'claim-received',
          component: () => import('@/views/claim/ClaimListView.vue'),
        },
        {
          path: 'reports',
          name: 'my-reports',
          component: () => import('@/views/report/MyReportsView.vue'),
        },
        // 认领详情：不设 requiresAuth，未登录时由页面自行提示（后端仍会返回 401）
        {
          path: 'claims/:claimId',
          name: 'claim-detail',
          component: () => import('@/views/claim/ClaimDetailView.vue'),
        },
        { path: 'login', name: 'login', component: () => import('@/views/account/LoginView.vue') },
      ],
    },
    {
      path: '/admin',
      component: () => import('@/layouts/AdminLayout.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
      children: [
        {
          path: '',
          name: 'admin-home',
          component: () => import('@/views/admin/AdminHomeView.vue'),
        },
        {
          path: 'items',
          name: 'admin-items',
          component: () => import('@/views/admin/AdminItemsView.vue'),
        },
        {
          path: 'reports',
          name: 'admin-reports',
          component: () => import('@/views/admin/AdminReportsView.vue'),
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
    },
  ],
})

// 路由守卫只改善体验，真正的权限检查在后端
router.beforeEach(async (to) => {
  const auth = useAuthStore()
  try {
    await auth.ensureLoaded()
  } catch {
    // 后端暂时连不上时按未登录处理，由页面自己展示错误
  }
  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin && !auth.isAdmin) {
    return { name: 'home' }
  }
})

export default router
