<script setup lang="ts">
import { nextTick, ref, useId, watch } from 'vue'
import { useRoute } from 'vue-router'
import type { InputInstance } from 'element-plus'
import { textPrompt } from '@/composables/useTextPrompt'
import { restoreFocus } from '@/utils/restoreFocus'

const route = useRoute()
const id = useId()
const input = ref<InputInstance>()
const open = ref(false)
const value = ref('')
const error = ref('')
let result: string | undefined
let opener: Element | null = null

watch(textPrompt, (request) => {
  if (!request) return
  value.value = ''
  error.value = ''
  result = undefined
  opener = document.activeElement
  open.value = true
})

watch(
  () => route.path,
  () => {
    if (open.value) open.value = false
  },
)

async function submit() {
  const valid = textPrompt.value?.inputValidator(value.value)
  if (valid === true) {
    result = value.value
    open.value = false
  } else {
    error.value = valid ?? ''
    await nextTick()
    input.value?.focus()
  }
}

async function finish() {
  const request = textPrompt.value
  const submitted = result
  const previous = opener
  textPrompt.value = undefined
  await restoreFocus(previous)
  if (submitted === undefined) request?.cancel('cancel')
  else request?.resolve({ value: submitted })
}
</script>

<template>
  <el-dialog
    v-model="open"
    :title="textPrompt?.title"
    width="min(420px, calc(100vw - 32px))"
    append-to-body
    destroy-on-close
    @opened="input?.focus()"
    @closed="finish"
  >
    <form :id="`${id}-form`" class="text-prompt" @submit.prevent="submit">
      <label :for="id">{{ textPrompt?.message }}</label>
      <el-input
        :id="id"
        ref="input"
        v-model="value"
        aria-required="true"
        :aria-invalid="error ? true : undefined"
        :aria-describedby="error ? `${id}-error` : undefined"
        @input="error = ''"
      />
      <p v-if="error" :id="`${id}-error`" class="text-prompt-error" role="alert">{{ error }}</p>
    </form>
    <template #footer>
      <el-button @click="open = false">取消</el-button>
      <el-button type="primary" native-type="submit" :form="`${id}-form`">确定</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.text-prompt {
  display: grid;
  gap: 8px;
}

.text-prompt-error {
  margin: 0;
  color: var(--el-color-error);
}
</style>
