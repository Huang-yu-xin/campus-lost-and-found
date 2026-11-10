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
        { path: 'items', name: 'items', component: () => import('@/views/item/ItemListView.vue') },
        {
          path: 'items/new',
          name: 'item-publish',
          component: () => import('@/views/item/ItemPublishView.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'me/items',
          name: 'my-items',
          component: () => import('@/views/item/MyItemsView.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'me/items/:itemId/edit',
          name: 'item-edit',
          component: () => import('@/views/item/ItemPublishView.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'items/:itemId',
          name: 'item-detail',
          component: () => import('@/views/item/ItemDetailView.vue'),
        },
        {
          path: 'me/claims',
          name: 'my-claims',
          component: () => import('@/views/claim/ClaimListView.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'claims',
          alias: 'me/received-claims',
          name: 'claim-received',
          component: () => import('@/views/claim/ClaimListView.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'reports',
          name: 'my-reports',
          component: () => import('@/views/report/MyReportsView.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'me/clues',
          name: 'my-clues',
          component: () => import('@/views/clue/ClueListView.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'clues/:clueId',
          name: 'clue-detail',
          component: () => import('@/views/clue/ClueDetailView.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'claims/:claimId',
          name: 'claim-detail',
          component: () => import('@/views/claim/ClaimDetailView.vue'),
          meta: { requiresAuth: true },
        },
        { path: 'login', name: 'login', component: () => import('@/views/account/LoginView.vue') },
        {
          path: 'register',
          name: 'register',
          component: () => import('@/views/account/RegisterView.vue'),
        },
        {
          path: 'me/profile',
          name: 'profile',
          component: () => import('@/views/personal/ProfileView.vue'),
          meta: { requiresAuth: true },
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
  if ((to.name === 'login' || to.name === 'register') && auth.isLoggedIn) {
    return { name: 'home' }
  }
  if (to.meta.requiresAdmin && !auth.isAdmin) {
    return { name: 'home' }
  }
})

export default router
