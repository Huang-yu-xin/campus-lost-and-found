import type { CsrfData } from '@/types/api'
import type { Me } from '@/types/domain'

import { http } from './http'

// 认证与当前用户接口（SUP-01、FR-02）。登录、注册、退出由账号模块负责人在此补充。

export function getCsrf(): Promise<CsrfData> {
  return http.get<CsrfData>('/auth/csrf')
}

/** 读取当前登录用户；未登录时抛出 401 的 ApiError，但不会触发跳转登录页。 */
export function getMe(): Promise<Me> {
  return http.get<Me>('/me', { skipAuthRedirect: true })
}
