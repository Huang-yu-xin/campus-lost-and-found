import type { EmptyData } from '@/types/api'
import type { Me } from '@/types/domain'

import type { CodeSentData } from './auth'
import { http } from './http'

// 本人资料接口（FR-03、FR-06）。更新都要带 expectedVersion（最后读到的 me.version），版本过期返回 409

export interface ProfilePatch {
  expectedVersion: number
  nickname?: string
  /** null 表示清空手机号；不传表示不修改。 */
  phone?: string | null
}

export interface ChangeEmailRequest {
  expectedVersion: number
  email: string
  emailCode: string
}

export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

/** 修改昵称和手机号；改了手机号会撤回已给出的手机号授权。 */
export function updateProfile(patch: ProfilePatch): Promise<Me> {
  return http.patch<Me>('/me', patch)
}

/** 给新邮箱发送修改邮箱验证码（开发环境为模拟发送）。 */
export function sendChangeEmailCode(email: string): Promise<CodeSentData> {
  return http.post<CodeSentData>('/me/email-codes', { email })
}

/** 确认修改邮箱；成功后撤回已给出的邮箱授权。 */
export function changeEmail(request: ChangeEmailRequest): Promise<Me> {
  return http.put<Me>('/me/email', request)
}

/** 修改密码。成功后所有会话失效，当前会话也已退出，需要重新登录。 */
export function changePassword(request: ChangePasswordRequest): Promise<EmptyData> {
  return http.put<EmptyData>('/me/password', request)
}
