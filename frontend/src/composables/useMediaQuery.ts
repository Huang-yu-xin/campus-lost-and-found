import { onBeforeUnmount, ref } from 'vue'

/** 只更新展示方式，不重建页面或重新请求数据；卸载时移除监听。 */
export function useMediaQuery(query: string) {
  const media = typeof window.matchMedia === 'function' ? window.matchMedia(query) : null
  const matches = ref(media?.matches ?? false)
  const update = (event: MediaQueryListEvent) => (matches.value = event.matches)
  media?.addEventListener('change', update)
  onBeforeUnmount(() => media?.removeEventListener('change', update))
  return matches
}
