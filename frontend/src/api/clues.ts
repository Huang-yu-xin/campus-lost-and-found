import { http } from './http'
import type { PageData } from '@/types/api'
import type { ItemRecord } from './items'

export interface Clue {
  id: string
  lostItemId: string
  provider: { id: string; nickname: string }
  publisher: { id: string; nickname: string }
  content: string
  createdAt: string
}
export interface ClueDetail {
  clue: Clue
  item: ItemRecord
}
export interface ClueMessage {
  id: string
  sender: { id: string; nickname: string }
  content: string
  createdAt: string
}
export function createClue(itemId: string, content: string): Promise<Clue> {
  return http.post(`/items/${itemId}/clues`, { content })
}
export function getClue(id: string): Promise<ClueDetail> {
  return http.get(`/clues/${id}`)
}
export function listClues(received: boolean, page: number): Promise<PageData<Clue>> {
  return http.get(received ? '/me/received-clues' : '/me/clues', { params: { page, pageSize: 10 } })
}
export function clueMessages(id: string, page: number): Promise<PageData<ClueMessage>> {
  return http.get(`/clues/${id}/messages`, { params: { page, pageSize: 10 } })
}
export function sendClueMessage(id: string, content: string): Promise<ClueMessage> {
  return http.post(`/clues/${id}/messages`, { content })
}
export function resolveLostItem(id: string, expectedVersion: number): Promise<ItemRecord> {
  return http.post(`/items/${id}/resolve`, { expectedVersion })
}
