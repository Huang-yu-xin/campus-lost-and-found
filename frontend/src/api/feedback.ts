import { ElMessage } from 'element-plus'

import { ApiError } from './http'

/**
 * 把接口错误转成给用户看的提示文字。
 * 400 优先展示第一条字段错误；其余按状态码给出统一说明，并保留后端的具体文案。
 */
export function describeError(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return '操作失败，请稍后重试'
  }
  switch (error.status) {
    case 400:
      return error.errors[0]?.message ?? error.message
    case 401:
      return '登录已失效，请重新登录'
    case 404:
      return '内容不存在或无权查看'
    case 409:
      return `${error.message}，请刷新后查看最新状态`
    case 429:
      return '操作过于频繁，请稍后再试'
    default:
      return error.message
  }
}

/** 弹出错误提示。服务端错误附带请求编号，方便对照后端日志排查。 */
export function showError(error: unknown): void {
  const message = describeError(error)
  const requestId = error instanceof ApiError && error.status >= 500 ? error.requestId : null
  ElMessage.error(requestId ? `${message}（请求编号 ${requestId}）` : message)
}
