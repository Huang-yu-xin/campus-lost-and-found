import { nextTick } from 'vue'
import type { FormInstance } from 'element-plus'

export async function validateForm(form?: FormInstance) {
  if (!form) return false
  if (await form.validate().catch(() => false)) return true
  await nextTick()
  const field = form.fields.find((field) => field.validateState === 'error')
  const inputId = field?.inputIds[0]
  if (inputId) document.getElementById(inputId)?.focus()
  return false
}
