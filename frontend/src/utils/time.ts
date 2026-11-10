/** 把后端返回的 UTC ISO 8601 时间按浏览器本地时区（北京时间）展示；空值显示“—”。 */
export function formatTime(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}
