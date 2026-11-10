import { http } from './http'
import type { ItemRecord } from './items'

// 认领模块接口（FR-15 ~ FR-18）。字段与 openapi.yaml 的 Claim / ClaimDetail 对齐。
// 编号按合同以字符串传输，不转成 Number；时间为带时区的 ISO 8601 字符串。

export interface UserBrief {
  id: string
  nickname: string
}

export type ClaimStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'COMPLETED' | 'CLOSED'

export interface Claim {
  id: string
  itemId: string
  applicant: UserBrief
  publisher: UserBrief
  description: string
  status: ClaimStatus
  reason: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export interface ClaimDetail {
  claim: Claim
  item: ItemRecord
}

export function createClaim(itemId: string, description: string): Promise<Claim> {
  return http.post<Claim>(`/items/${itemId}/claims`, { description })
}

export function getClaim(claimId: string): Promise<ClaimDetail> {
  return http.get<ClaimDetail>(`/claims/${claimId}`)
}

export function confirmClaim(claimId: string, expectedVersion: number): Promise<Claim> {
  return http.post<Claim>(`/claims/${claimId}/confirm`, { expectedVersion })
}

export function rejectClaim(
  claimId: string,
  expectedVersion: number,
  reason: string,
): Promise<Claim> {
  return http.post<Claim>(`/claims/${claimId}/reject`, { expectedVersion, reason })
}

export function completeClaim(claimId: string, expectedVersion: number): Promise<Claim> {
  return http.post<Claim>(`/claims/${claimId}/complete`, { expectedVersion })
}

// ---------------- FR-19 留言 ----------------

export interface Message {
  id: string
  sender: UserBrief
  content: string
  createdAt: string
}

export interface MessagePage {
  items: Message[]
  page: number
  pageSize: number
  total: number
}

export function listMessages(claimId: string, page = 1, pageSize = 50): Promise<MessagePage> {
  return http.get<MessagePage>(`/claims/${claimId}/messages`, { params: { page, pageSize } })
}

export function sendMessage(claimId: string, content: string): Promise<Message> {
  return http.post<Message>(`/claims/${claimId}/messages`, { content })
}

// ---------------- FR-06 联系方式授权 ----------------

export interface ContactPermission {
  claimId: string
  phoneVisible: boolean
  emailVisible: boolean
  effective: boolean
  version: number
  updatedAt: string | null
}

export interface CounterpartyContact {
  claimId: string
  phone?: string
  email?: string
}

export function getContactPermission(claimId: string): Promise<ContactPermission> {
  return http.get<ContactPermission>(`/claims/${claimId}/contact-permission`)
}

export function setContactPermission(
  claimId: string,
  expectedVersion: number,
  phoneVisible: boolean,
  emailVisible: boolean,
): Promise<ContactPermission> {
  return http.put<ContactPermission>(`/claims/${claimId}/contact-permission`, {
    expectedVersion,
    phoneVisible,
    emailVisible,
  })
}

export function getCounterpartyContact(claimId: string): Promise<CounterpartyContact> {
  return http.get<CounterpartyContact>(`/claims/${claimId}/counterparty-contact`)
}
