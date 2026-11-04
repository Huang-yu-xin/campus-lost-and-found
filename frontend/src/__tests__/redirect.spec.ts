import { describe, expect, it } from 'vitest'

import { safeRedirect } from '@/utils/redirect'

describe('safeRedirect', () => {
  it('保留站内路径和查询参数', () => {
    expect(safeRedirect('/me/items?page=2')).toBe('/me/items?page=2')
  })

  const unsafe: [unknown, string][] = [
    ['https://evil.example.com', '外部网址'],
    ['//evil.example.com', '协议相对地址'],
    ['/\\evil.example.com', '反斜杠绕过'],
    ['me/items', '相对路径'],
    [undefined, '缺省'],
    [['/a', '/b'], '重复参数'],
  ]

  it.each(unsafe)('%s（%s）回到首页', (value) => {
    expect(safeRedirect(value)).toBe('/')
  })
})
