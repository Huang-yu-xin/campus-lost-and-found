// 业务枚举与共享模型，取值与 openapi.yaml 一致；ID 一律用 string，不转成 number

export type ItemStatus = 'PENDING_REVIEW' | 'RETURNED' | 'PUBLISHED' | 'OFF_SHELF' | 'ARCHIVED'

export type ClaimStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'COMPLETED' | 'CLOSED'

/** 发布后不允许修改类型。 */
export type ItemType = 'LOST' | 'FOUND'

export type Category = 'DOCUMENT' | 'ELECTRONICS' | 'DAILY' | 'BOOK' | 'CLOTHING' | 'OTHER'

export type Role = 'STUDENT' | 'ADMIN'

/** 当前登录用户（GET /me）。管理员的 studentId、realName、campusCardNo 为 null。 */
export interface Me {
  id: string
  studentId: string | null
  realName: string | null
  campusCardNo: string | null
  nickname: string
  email: string
  phone: string | null
  role: Role
  version: number
}

// 以下中文显示名为前端暂定文案，需求文档未规定；团队确认后统一在这里修改

export const ITEM_STATUS_LABELS: Record<ItemStatus, string> = {
  PENDING_REVIEW: '待审核',
  RETURNED: '已退回',
  PUBLISHED: '已发布',
  OFF_SHELF: '已下架',
  ARCHIVED: '已归档',
}

export const CLAIM_STATUS_LABELS: Record<ClaimStatus, string> = {
  PENDING: '待确认',
  CONFIRMED: '已确认',
  REJECTED: '已驳回',
  COMPLETED: '已完成交接',
  CLOSED: '已关闭',
}

export const ITEM_TYPE_LABELS: Record<ItemType, string> = {
  LOST: '寻物',
  FOUND: '招领',
}

export const CATEGORY_LABELS: Record<Category, string> = {
  DOCUMENT: '证件',
  ELECTRONICS: '电子产品',
  DAILY: '生活用品',
  BOOK: '书籍',
  CLOTHING: '衣物',
  OTHER: '其他',
}
