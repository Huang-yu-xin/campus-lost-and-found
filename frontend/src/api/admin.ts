import { http } from './http'
import type { ItemRecord } from './items'
import type { PageData } from '@/types/api'

// 管理端审核接口（FR-20 审核、FR-21 下架）。物品字段沿用 openapi.yaml 的 ItemRecord。

export type AdminItemStatus = 'PENDING_REVIEW' | 'RETURNED' | 'PUBLISHED'

export interface ReviewRecord {
  id: string
  itemId: string
  action: 'APPROVE' | 'RETURN' | 'TAKE_DOWN'
  reason: string | null
  operatorId: string
  createdAt: string
}

export function listAdminItems(
  status: AdminItemStatus,
  page = 1,
  pageSize = 10,
): Promise<PageData<ItemRecord>> {
  return http.get<PageData<ItemRecord>>('/admin/items', { params: { status, page, pageSize } })
}

export function getAdminItem(itemId: string): Promise<ItemRecord> {
  return http.get<ItemRecord>(`/admin/items/${itemId}`)
}

export function listReviewRecords(
  itemId: string,
  page = 1,
  pageSize = 10,
): Promise<PageData<ReviewRecord>> {
  return http.get<PageData<ReviewRecord>>(`/admin/items/${itemId}/review-records`, {
    params: { page, pageSize },
  })
}

export function approveItem(itemId: string, expectedVersion: number): Promise<ItemRecord> {
  return http.post<ItemRecord>(`/admin/items/${itemId}/approve`, { expectedVersion })
}

export function returnItem(
  itemId: string,
  expectedVersion: number,
  reason: string,
): Promise<ItemRecord> {
  return http.post<ItemRecord>(`/admin/items/${itemId}/return`, { expectedVersion, reason })
}

export function takeDownItem(
  itemId: string,
  expectedVersion: number,
  reason: string,
): Promise<ItemRecord> {
  return http.post<ItemRecord>(`/admin/items/${itemId}/take-down`, { expectedVersion, reason })
}
