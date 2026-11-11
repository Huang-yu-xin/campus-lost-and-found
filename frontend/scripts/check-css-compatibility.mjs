import { readdir, readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

// 检查最终产物，防止源文件写 max-width、压缩后却变为旧内核不认识的范围语法。
const assets = fileURLToPath(new URL('../dist/assets/', import.meta.url))
const files = (await readdir(assets)).filter((name) => name.endsWith('.css'))
if (!files.length) throw new Error('没有找到已构建的 CSS，请先运行 vite build')
const queries = []
for (const file of files) {
  const css = await readFile(path.join(assets, file), 'utf8')
  for (const match of css.matchAll(/@media\s+([^{}]+)\{/g)) {
    const query = match[1]
    if (/[<>]/.test(query)) {
      throw new Error(`${file} 使用了旧内核不支持的媒体查询范围语法：${query}`)
    }
    queries.push(query)
  }
}
for (const width of [400, 640, 768]) {
  if (!queries.some((query) => new RegExp(`max-width\\s*:\\s*${width}px`).test(query))) {
    throw new Error(`构建产物缺少兼容写法的 ${width}px 断点`)
  }
}
console.log(`CSS 兼容检查通过：${files.length} 个样式文件，手机断点保留 min/max-width 写法`)
