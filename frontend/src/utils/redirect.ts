/**
 * 登录后跳回的地址只接受站内路径，防止 ?redirect=https://恶意网站 这类开放跳转。
 * 不合法时回到首页。
 */
export function safeRedirect(value: unknown): string {
  if (
    typeof value !== 'string' ||
    !value.startsWith('/') ||
    value.startsWith('//') ||
    value.includes('\\')
  ) {
    return '/'
  }
  return value
}
