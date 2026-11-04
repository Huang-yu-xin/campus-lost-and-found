import type { CsrfData, EmptyData } from '@/types/api'
import type { Me } from '@/types/domain'

import { http } from './http'

// 认证与当前用户接口（SUP-01、FR-02）。登录、退出后都要重新获取 CSRF 令牌，统一在 stores/auth.ts 中处理。

export interface LoginRequest {
  /** 学号、校园卡号或管理员账号；邮箱不能登录。 */
  account: string
  password: string
}

export interface CodeSentData {
  expiresInSeconds: number
  /** 再次发送前需等待的秒数。 */
  retryAfterSeconds: number
}

export interface RegisterRequest {
  studentId: string
  realName: string
  campusCardNo: string
  email: string
  emailCode: string
  password: string
  /** 可选；不填时后端生成不暴露身份的默认昵称。 */
  nickname?: string
}

/** 发送注册验证码（开发环境为模拟发送）；60 秒内重复发送返回 429。 */
export function sendRegistrationCode(email: string): Promise<CodeSentData> {
  return http.post<CodeSentData>('/auth/email-codes', { email })
}

/** 注册成功返回 201 和新账号资料，但不会自动登录。 */
export function register(request: RegisterRequest): Promise<Me> {
  return http.post<Me>('/auth/register', request)
}

export function getCsrf(): Promise<CsrfData> {
  return http.get<CsrfData>('/auth/csrf')
}

/** 读取当前登录用户；未登录时抛出 401 的 ApiError，但不会触发跳转登录页。 */
export function getMe(): Promise<Me> {
  return http.get<Me>('/me', { skipAuthRedirect: true })
}

/** 账号或密码错误返回 401 BAD_CREDENTIALS；连续失败过多返回 429。 */
export function login(request: LoginRequest): Promise<Me> {
  return http.post<Me>('/auth/login', request, { skipAuthRedirect: true })
}

export function logout(): Promise<EmptyData> {
  return http.post<EmptyData>('/auth/logout', undefined, { skipAuthRedirect: true })
}
