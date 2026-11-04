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
      // 登录时的“账号或密码错误”直接用后端文案；其余 401 都是会话失效
      return error.code === 'AUTH_REQUIRED' ? '登录已失效，请重新登录' : error.message
    case 404:
      return '内容不存在或无权查看'
    case 409:
      return `${error.message}，请刷新后查看最新状态`
    default:
      // 429 等由后端给出具体说明，例如“请 15 分钟后再试”
      return error.message
  }
}

/** 弹出错误提示。服务端错误附带请求编号，方便对照后端日志排查。 */
export function showError(error: unknown): void {
  const message = describeError(error)
  const requestId = error instanceof ApiError && error.status >= 500 ? error.requestId : null
  ElMessage.error(requestId ? `${message}（请求编号 ${requestId}）` : message)
}
