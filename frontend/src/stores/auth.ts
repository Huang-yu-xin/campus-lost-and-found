import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { getMe, login as loginApi, logout as logoutApi, type LoginRequest } from '@/api/auth'
import { ApiError, http } from '@/api/http'
import type { Me } from '@/types/domain'

/** 当前登录用户。Pinia 只存这类少量共享状态，列表和表单数据放在各自页面里。 */
export const useAuthStore = defineStore('auth', () => {
  const me = ref<Me | null>(null)
  const loaded = ref(false)
  let loading: Promise<void> | null = null

  const isLoggedIn = computed(() => me.value !== null)
  const isAdmin = computed(() => me.value?.role === 'ADMIN')

  /** 向后端确认登录状态；未登录（401）是正常结果，不算错误。 */
  function reload(): Promise<void> {
    loading ??= getMe()
      .then((user) => {
        me.value = user
      })
      .catch((error: unknown) => {
        me.value = null
        if (!(error instanceof ApiError && error.status === 401)) {
          throw error
        }
      })
      .finally(() => {
        loaded.value = true
        loading = null
      })
    return loading
  }

  /** 首次进入页面时加载一次；已加载过则直接返回。 */
  async function ensureLoaded(): Promise<void> {
    if (!loaded.value) {
      await reload()
    }
  }

  /** 登录成功后旧 CSRF 令牌作废，先取到新令牌再允许后续写请求。 */
  async function login(request: LoginRequest): Promise<void> {
    const user = await loginApi(request)
    await http.refreshCsrfToken()
    me.value = user
    loaded.value = true
  }

  /** 无论退出请求是否成功，本地都清空登录状态；随后换用新的匿名会话令牌。 */
  async function logout(): Promise<void> {
    try {
      await logoutApi()
    } finally {
      clear()
      await http.refreshCsrfToken().catch(() => undefined)
    }
  }

  /** 资料修改成功后用接口返回的最新资料（含新 version）替换。 */
  function setMe(user: Me): void {
    me.value = user
  }

  /** 退出或会话失效时清空，避免页面继续显示私有数据。 */
  function clear(): void {
    me.value = null
  }

  return { me, loaded, isLoggedIn, isAdmin, reload, ensureLoaded, login, logout, setMe, clear }
})
