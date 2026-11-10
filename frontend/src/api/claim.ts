import { http } from './http'

// 认领模块接口（FR-15 ~ FR-18）。字段与 openapi.yaml 的 Claim / ClaimDetail 对齐。

export interface UserBrief {
  id: number
  nickname: string
}

export type ClaimStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'COMPLETED' | 'CLOSED'

export interface Claim {
  id: number
  itemId: number
  applicant: UserBrief
  publisher: UserBrief
  description: string
  status: ClaimStatus
  reason: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export interface ItemBrief {
  id: number
  type: string
  category: string
  location: string
  title: string
  description: string
  status: string
  occurredDate: string | null
  publishedAt: string | null
  createdAt: string
}

export interface ClaimDetail {
  claim: Claim
  item: ItemBrief
}

export function createClaim(itemId: string | number, description: string): Promise<Claim> {
  return http.post<Claim>(`/items/${itemId}/claims`, { description })
}

export function getClaim(claimId: number): Promise<ClaimDetail> {
  return http.get<ClaimDetail>(`/claims/${claimId}`)
}

export function confirmClaim(claimId: number, expectedVersion: number): Promise<Claim> {
  return http.post<Claim>(`/claims/${claimId}/confirm`, { expectedVersion })
}

export function rejectClaim(
  claimId: number,
  expectedVersion: number,
  reason: string,
): Promise<Claim> {
  return http.post<Claim>(`/claims/${claimId}/reject`, { expectedVersion, reason })
}

export function completeClaim(claimId: number, expectedVersion: number): Promise<Claim> {
  return http.post<Claim>(`/claims/${claimId}/complete`, { expectedVersion })
}

// ---------------- FR-19 留言 ----------------

export interface Message {
  id: number
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

export function listMessages(claimId: number, page = 1, pageSize = 50): Promise<MessagePage> {
  return http.get<MessagePage>(`/claims/${claimId}/messages`, { params: { page, pageSize } })
}

export function sendMessage(claimId: number, content: string): Promise<Message> {
  return http.post<Message>(`/claims/${claimId}/messages`, { content })
}

// ---------------- FR-06 联系方式授权 ----------------

export interface ContactPermission {
  claimId: number
  phoneVisible: boolean
  emailVisible: boolean
  effective: boolean
  version: number
  updatedAt: string | null
}

export interface CounterpartyContact {
  claimId: number
  phone?: string
  email?: string
}

export function getContactPermission(claimId: number): Promise<ContactPermission> {
  return http.get<ContactPermission>(`/claims/${claimId}/contact-permission`)
}

export function setContactPermission(
  claimId: number,
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

export function getCounterpartyContact(claimId: number): Promise<CounterpartyContact> {
  return http.get<CounterpartyContact>(`/claims/${claimId}/counterparty-contact`)
}
