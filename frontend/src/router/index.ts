import { createRouter, createWebHistory } from 'vue-router'

import StudentLayout from '@/layouts/StudentLayout.vue'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
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
        {
          path: '',
          name: 'home',
          component: () => import('@/views/HomeView.vue'),
          meta: { title: '校园失物招领系统' },
        },
        {
          path: 'items',
          name: 'items',
          component: () => import('@/views/item/ItemListView.vue'),
          meta: { title: '浏览物品' },
        },
        {
          path: 'items/new',
          name: 'item-publish',
          component: () => import('@/views/item/ItemPublishView.vue'),
          meta: { title: '发布物品信息', requiresAuth: true },
        },
        {
          path: 'me/items',
          name: 'my-items',
          component: () => import('@/views/item/MyItemsView.vue'),
          meta: { title: '我的发布', requiresAuth: true },
        },
        {
          path: 'me/items/:itemId/edit',
          name: 'item-edit',
          component: () => import('@/views/item/ItemPublishView.vue'),
          meta: { title: '修改物品信息', requiresAuth: true },
        },
        {
          path: 'items/:itemId',
          name: 'item-detail',
          component: () => import('@/views/item/ItemDetailView.vue'),
          meta: { title: '物品详情' },
        },
        {
          path: 'me/claims',
          name: 'my-claims',
          component: () => import('@/views/claim/ClaimListView.vue'),
          meta: { title: '我申请的认领', requiresAuth: true },
        },
        {
          path: 'claims',
          alias: 'me/received-claims',
          name: 'claim-received',
          component: () => import('@/views/claim/ClaimListView.vue'),
          meta: { title: '我收到的认领申请', requiresAuth: true },
        },
        {
          path: 'reports',
          name: 'my-reports',
          component: () => import('@/views/report/MyReportsView.vue'),
          meta: { title: '我的举报', requiresAuth: true },
        },
        {
          path: 'me/clues',
          name: 'my-clues',
          component: () => import('@/views/clue/ClueListView.vue'),
          meta: { title: '我的线索', requiresAuth: true },
        },
        {
          path: 'clues/:clueId',
          name: 'clue-detail',
          component: () => import('@/views/clue/ClueDetailView.vue'),
          meta: { title: '线索详情', requiresAuth: true },
        },
        {
          path: 'claims/:claimId',
          name: 'claim-detail',
          component: () => import('@/views/claim/ClaimDetailView.vue'),
          meta: { title: '认领详情', requiresAuth: true },
        },
        {
          path: 'login',
          name: 'login',
          component: () => import('@/views/account/LoginView.vue'),
          meta: { title: '登录' },
        },
        {
          path: 'register',
          name: 'register',
          component: () => import('@/views/account/RegisterView.vue'),
          meta: { title: '注册' },
        },
        {
          path: 'me/profile',
          name: 'profile',
          component: () => import('@/views/personal/ProfileView.vue'),
          meta: { title: '个人资料', requiresAuth: true },
        },
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
          meta: { title: '管理端概览' },
        },
        {
          path: 'items',
          name: 'admin-items',
          component: () => import('@/views/admin/AdminItemsView.vue'),
          meta: { title: '信息管理' },
        },
        {
          path: 'reports',
          name: 'admin-reports',
          component: () => import('@/views/admin/AdminReportsView.vue'),
          meta: { title: '举报处理' },
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { title: '页面不存在' },
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
  if ((to.name === 'login' || to.name === 'register') && auth.isLoggedIn) {
    return { name: 'home' }
  }
  if (to.meta.requiresAdmin && !auth.isAdmin) {
    return { name: 'home' }
  }
})

export default router
