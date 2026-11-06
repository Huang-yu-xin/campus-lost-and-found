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
}

export interface PublicItem extends CreateItemRequest {
  id: string
  publisher: { id: string; nickname: string }
  images: { id: string; url: string; mediaType: string; sizeBytes: number }[]
  publishedAt: string | null
  createdAt: string
  hasConfirmedClaim: boolean
}

interface ItemRecord extends PublicItem {
  status: ItemStatus
  version: number
  reviewReason: string | null
  closureReason: string | null
  updatedAt: string
}

export interface ItemFilters {
  type?: ItemType | ''
  category?: Category | ''
  keyword?: string
  location?: string
  dateFrom?: string
  dateTo?: string
}

export function createItem(request: CreateItemRequest): Promise<ItemRecord> {
  return http.post<ItemRecord>('/items', request)
}

export function listItems(page: number, filters: ItemFilters): Promise<PageData<PublicItem>> {
  const params = Object.fromEntries(Object.entries(filters).filter(([, value]) => value?.trim()))
  return http.get<PageData<PublicItem>>('/items', { params: { ...params, page } })
}

export function getItem(id: string): Promise<PublicItem> {
  return http.get<PublicItem>(`/items/${encodeURIComponent(id)}`)
}
