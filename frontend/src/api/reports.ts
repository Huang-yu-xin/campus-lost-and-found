import { http } from './http'
import type { PageData } from '@/types/api'

// 举报接口（FR-22、FR-27，选做）。字段与 openapi.yaml 的 Report 对齐。

export type ReportStatus = 'PENDING' | 'RESOLVED'
export type ReportAction = 'KEEP' | 'TAKE_DOWN' | 'REJECT_PENDING_CLAIM'

export interface Report {
  id: number
  itemId: number
  claimId: number | null
  clueId: number | null
  reason: string
  status: ReportStatus
  action: ReportAction | null
  resolution: string | null
  version: number
  createdAt: string
  resolvedAt: string | null
}

export interface CreateReportBody {
  itemId: string | number
  claimId?: number | null
  clueId?: string | number | null
  reason: string
}

export interface ReportConversation {
  kind: 'CLAIM' | 'CLUE'
  contextId: number
  evidenceText: string
  items: Array<{
    id: number
    sender: { id: number; nickname: string }
    content: string
    createdAt: string
  }>
  page: number
  pageSize: number
  total: number
}

export function createReport(body: CreateReportBody): Promise<Report> {
  return http.post<Report>('/reports', body)
}

export function listMyReports(page = 1, pageSize = 10): Promise<PageData<Report>> {
  return http.get<PageData<Report>>('/me/reports', { params: { page, pageSize } })
}

export function listAdminReports(
  status: ReportStatus,
  page = 1,
  pageSize = 10,
): Promise<PageData<Report>> {
  return http.get<PageData<Report>>('/admin/reports', { params: { status, page, pageSize } })
}

export function getAdminReport(reportId: number): Promise<Report> {
  return http.get<Report>(`/admin/reports/${reportId}`)
}

export function getReportConversation(
  reportId: number,
  page = 1,
  pageSize = 50,
): Promise<ReportConversation> {
  return http.get<ReportConversation>(`/admin/reports/${reportId}/conversation`, {
    params: { page, pageSize },
  })
}

export function resolveReport(
  reportId: number,
  expectedVersion: number,
  action: ReportAction,
  reason: string,
): Promise<Report> {
  return http.post<Report>(`/admin/reports/${reportId}/resolve`, {
    expectedVersion,
    action,
    reason,
  })
}
