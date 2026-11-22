<script setup lang="ts">
import { ref } from 'vue'

defineOptions({ inheritAttrs: false })
defineProps<{ toggleLabel?: string }>()
const password = defineModel<string>({ required: true })
const visible = ref(false)
</script>

<template>
  <el-input v-bind="$attrs" v-model="password" :type="visible ? 'text' : 'password'">
    <template #suffix>
      <button
        type="button"
        class="password-toggle"
        :aria-label="toggleLabel || '显示密码'"
        :aria-pressed="visible"
        @click="visible = !visible"
      >
        显示
      </button>
    </template>
  </el-input>
</template>

<style scoped>
.password-toggle {
  min-height: 28px;
  padding: 0 4px;
  border: 0;
  background: transparent;
  color: var(--el-color-primary);
  font: inherit;
  cursor: pointer;
}

.password-toggle[aria-pressed='true'] {
  text-decoration: underline;
}

@media (forced-colors: active) {
  .password-toggle {
    border: 1px solid ButtonText;
  }
}
</style>
