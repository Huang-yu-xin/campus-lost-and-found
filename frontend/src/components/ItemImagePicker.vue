<script setup lang="ts">
import { nextTick, ref, useId } from 'vue'
import { describeError } from '@/api/feedback'
import { uploadImage, type ItemImage } from '@/api/items'
import { restoreFocus } from '@/utils/restoreFocus'

const images = defineModel<ItemImage[]>({ required: true })
const props = defineProps<{ disabled?: boolean }>()
const emit = defineEmits<{ busy: [value: boolean] }>()
const uploading = ref(false)
const errorMessage = ref('')
const uploadStatus = ref('')
const inputId = useId()
const wrapper = ref<HTMLElement>()

async function choose(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  if (props.disabled || uploading.value || !files.length) return
  errorMessage.value = ''
  uploadStatus.value = ''
  if (images.value.length + files.length > 3) {
    errorMessage.value = '每条信息最多三张图片，请减少选择的数量'
    return
  }
  if (files.some((file) => file.size > 2097152 || file.size === 0)) {
    errorMessage.value = '图片不能为空，且每张不能超过 2 MB'
    return
  }
  uploading.value = true
  const previous = document.activeElement
  emit('busy', true)
  try {
    // 逐张上传，成功的图片保留；失败时可以重选，不清空表单。
    for (const file of files) images.value = [...images.value, await uploadImage(file)]
    uploadStatus.value = `已上传 ${files.length} 张图片，当前共 ${images.value.length} 张`
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    uploading.value = false
    emit('busy', false)
    await nextTick()
    const lastRemove = wrapper.value?.querySelector<HTMLButtonElement>(
      '.previews > div:last-child button',
    )
    await restoreFocus(
      previous === input && input.matches(':disabled') ? (lastRemove ?? input) : previous,
    )
  }
}

async function removeImage(id: string, index: number) {
  const keyboardFocus = wrapper.value?.contains(document.activeElement)
  images.value = images.value.filter((image) => image.id !== id)
  uploadStatus.value = `已移除第 ${index + 1} 张图片，当前共 ${images.value.length} 张`
  if (!keyboardFocus) return
  await nextTick()
  const buttons = wrapper.value?.querySelectorAll<HTMLButtonElement>('.previews button')
  const next = buttons?.[index] ?? buttons?.[index - 1] ?? document.getElementById(inputId)
  await restoreFocus(next ?? null)
}
</script>

<template>
  <div ref="wrapper" class="image-picker">
    <p><label :for="inputId">选择物品图片</label></p>
    <p :id="`${inputId}-hint`" class="hint">
      可选，最多三张 JPG/PNG，每张不超过 2 MB。请遮挡姓名、卡号等隐私信息，图片与文字一起等待审核。
    </p>
    <div class="previews">
      <div v-for="(image, index) in images" :key="image.id">
        <img :src="image.url" :alt="`物品图片 ${index + 1}`" />
        <el-button
          :aria-label="`移除第 ${index + 1} 张图片`"
          :disabled="disabled || uploading"
          @click="removeImage(image.id, index)"
          >移除</el-button
        >
      </div>
    </div>
    <input
      :id="inputId"
      type="file"
      accept="image/jpeg,image/png"
      multiple
      :aria-describedby="`${inputId}-hint${errorMessage ? ' ' + inputId + '-error' : ''}`"
      :disabled="disabled || uploading || images.length >= 3"
      @change="choose"
    />
    <p role="status" :class="{ 'visually-hidden': !uploading }">
      {{ uploading ? '正在上传图片，请稍候…' : uploadStatus }}
    </p>
    <el-alert
      v-if="errorMessage"
      :id="`${inputId}-error`"
      :title="errorMessage"
      type="error"
      :closable="false"
    />
  </div>
</template>

<style scoped>
.previews {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.previews > div {
  display: grid;
  gap: 6px;
}
img {
  width: 140px;
  height: 110px;
  object-fit: contain;
  border: 1px solid var(--app-border);
}
.hint {
  color: var(--app-text-secondary);
  font-size: 14px;
}

input[type='file'] {
  width: 100%;
  min-width: 0;
  min-height: 44px;
}
</style>
