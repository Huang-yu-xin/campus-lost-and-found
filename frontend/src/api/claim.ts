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

export function createClaim(itemId: number, description: string): Promise<Claim> {
  return http.post<Claim>(`/items/${itemId}/claims`, { description })
}

export function getClaim(claimId: number): Promise<ClaimDetail> {
  return http.get<ClaimDetail>(`/claims/${claimId}`)
}

export function confirmClaim(claimId: number): Promise<Claim> {
  return http.post<Claim>(`/claims/${claimId}/confirm`)
}

export function rejectClaim(claimId: number, reason: string): Promise<Claim> {
  return http.post<Claim>(`/claims/${claimId}/reject`, { reason })
}

export function completeClaim(claimId: number): Promise<Claim> {
  return http.post<Claim>(`/claims/${claimId}/complete`)
}
