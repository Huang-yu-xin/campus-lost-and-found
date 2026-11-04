import { describe, expect, it } from 'vitest'

import { textLength } from '@/utils/text'

describe('textLength', () => {
  it('与后端一致：去首尾空白后按字符计数', () => {
    expect(textLength('  校园卡  ')).toBe(3)
    expect(textLength('😀😀')).toBe(2)
    expect(textLength('   ')).toBe(0)
  })
})
