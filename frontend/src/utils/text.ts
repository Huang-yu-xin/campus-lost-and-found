/**
 * 与后端 @CodePointLength 一致的长度计算：去首尾空白后按字符（码点）计数，一个 emoji 算 1 个字。
 * 不要直接用 string.length，它会把 emoji 算成 2。
 */
export function textLength(value: string): number {
  return [...value.trim()].length
}
