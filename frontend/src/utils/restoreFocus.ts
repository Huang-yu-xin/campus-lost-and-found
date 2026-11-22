import { nextTick } from 'vue'

export async function restoreFocus(previous: Element | null) {
  if (!(previous instanceof HTMLElement) || previous === document.body) return
  const dialog = previous.closest('[role="dialog"]')
  await nextTick()
  if (document.activeElement !== document.body) return
  previous.focus()
  if (document.activeElement === document.body) {
    previous.closest('.el-pagination')?.querySelector<HTMLElement>('[aria-current="true"]')?.focus()
  }
  if (
    document.activeElement === document.body &&
    dialog instanceof HTMLElement &&
    dialog.offsetHeight > 0
  ) {
    dialog.querySelector<HTMLElement>('button:not(:disabled)')?.focus()
  }
  if (document.activeElement === document.body) {
    document.getElementById('main-content')?.focus()
  }
}
