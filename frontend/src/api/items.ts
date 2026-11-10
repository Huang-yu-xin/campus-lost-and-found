import type { PageData } from '@/types/api'
import type { Category, ItemStatus, ItemType } from '@/types/domain'

import { http } from './http'

export interface CreateItemRequest {
  type: ItemType
  category: Category
  title: string
  location: string
  occurredDate: string
  description: string
  imageIds?: string[]
}

export interface PublicItem extends CreateItemRequest {
  id: string
  publisher: { id: string; nickname: string }
  images: { id: string; url: string; mediaType: string; sizeBytes: number }[]
  publishedAt: string | null
  createdAt: string
  hasConfirmedClaim: boolean
}

export interface ItemRecord extends PublicItem {
  status: ItemStatus
  version: number
  reviewReason: string | null
  closureReason: string | null
  updatedAt: string
}

export interface ItemImage {
  id: string
  url: string
  mediaType: string
  sizeBytes: number
}

export function uploadImage(file: File): Promise<ItemImage> {
  const body = new FormData()
  body.append('file', file)
  return http.post<ItemImage>('/uploads/images', body)
}

export function listOwnItems(page: number): Promise<PageData<ItemRecord>> {
  return http.get<PageData<ItemRecord>>('/me/items', { params: { page } })
}

export function getOwnItem(id: string): Promise<ItemRecord> {
  return http.get<ItemRecord>(`/me/items/${encodeURIComponent(id)}`)
}

export function editItem(
  id: string,
  request: Omit<CreateItemRequest, 'type'> & { expectedVersion: number },
): Promise<ItemRecord> {
  return http.patch<ItemRecord>(`/items/${encodeURIComponent(id)}`, request)
}

export function withdrawItem(id: string, expectedVersion: number): Promise<ItemRecord> {
  return http.post<ItemRecord>(`/items/${encodeURIComponent(id)}/withdraw`, { expectedVersion })
}

export interface ItemFilters {
  type?: ItemType | ''
  category?: Category | ''
  keyword?: string
  location?: string
  dateFrom?: string
  dateTo?: string
}

export function createItem(request: CreateItemRequest, submissionKey: string): Promise<ItemRecord> {
  return http.post<ItemRecord>('/items', request, { headers: { 'Idempotency-Key': submissionKey } })
}

export function listItems(page: number, filters: ItemFilters): Promise<PageData<PublicItem>> {
  const params = Object.fromEntries(Object.entries(filters).filter(([, value]) => value?.trim()))
  return http.get<PageData<PublicItem>>('/items', { params: { ...params, page } })
}

export function getItem(id: string): Promise<PublicItem> {
  return http.get<PublicItem>(`/items/${encodeURIComponent(id)}`)
}
