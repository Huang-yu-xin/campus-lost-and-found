// 与后端统一响应格式对齐，见 docs/接口文档.md “公共规范”与 openapi.yaml

/** 成功响应。客户端按 code 判断，不依赖 message 文案。 */
export interface ApiResponse<T> {
  code: 'OK'
  message: string
  data: T
  requestId: string
}

export interface FieldError {
  field: string
  message: string
}

/** 失败响应：data 固定为 null。 */
export interface ApiErrorBody {
  code: string
  message: string
  data: null
  requestId: string
  errors: FieldError[]
}

/** 列表数据：page 从 1 开始，超出末页时 items 为空数组。 */
export interface PageData<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
}

/** 操作成功但无额外数据时 data 为 {}。 */
export type EmptyData = Record<string, never>

/** GET /auth/csrf 的数据：写请求在 headerName 指定的请求头中携带 token。 */
export interface CsrfData {
  headerName: 'X-CSRF-TOKEN'
  token: string
}
