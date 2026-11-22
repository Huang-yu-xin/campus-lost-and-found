<script setup lang="ts">
import { computed, ref, useId } from 'vue'
import type { FormItemInstance } from 'element-plus'

const props = defineProps<{ label: string }>()
const field = ref<FormItemInstance>()
const errorId = useId()
const control = computed(() => ({
  'aria-label': props.label,
  'aria-invalid': field.value?.validateState === 'error' || undefined,
  'aria-describedby': field.value?.validateState === 'error' ? errorId : undefined,
}))
</script>

<template>
  <el-form-item ref="field" :label="label">
    <slot :control="control" />
    <template #error="{ error }">
      <div :id="errorId" class="el-form-item__error" role="alert">{{ error }}</div>
    </template>
  </el-form-item>
</template>
