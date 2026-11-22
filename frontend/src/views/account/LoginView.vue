<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'

import { describeError } from '@/api/feedback'
import AccessibleFormItem from '@/components/AccessibleFormItem.vue'
import PasswordInput from '@/components/PasswordInput.vue'
import { validateForm } from '@/utils/validateForm'
import { useAuthStore } from '@/stores/auth'
import { safeRedirect } from '@/utils/redirect'

// FR-02 登录：学号、校园卡号或管理员账号均可登录，邮箱不能作为账号

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const formRef = ref<FormInstance>()
// 从注册页跳过来时带上刚注册的学号
const form = reactive({
  account: typeof route.query.account === 'string' ? route.query.account : '',
  password: '',
})
const submitting = ref(false)
const errorMessage = ref('')

const rules: FormRules<typeof form> = {
  account: [{ required: true, message: '请输入学号、校园卡号或管理员账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  if (submitting.value || !(await validateForm(formRef.value))) {
    return
  }
  submitting.value = true
  errorMessage.value = ''
  try {
    await auth.login({ account: form.account.trim(), password: form.password })
    await router.replace(safeRedirect(route.query.redirect))
  } catch (error) {
    errorMessage.value = describeError(error)
    form.password = ''
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="login-page">
    <el-card shadow="never" class="login-card">
      <h1>登录</h1>
      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        show-icon
        :closable="false"
        class="error"
      />
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="submit"
      >
        <AccessibleFormItem label="账号" prop="account" v-slot="{ control }">
          <el-input
            v-bind="control"
            aria-required="true"
            v-model="form.account"
            placeholder="学号 / 校园卡号"
            autocomplete="username"
            maxlength="32"
          />
        </AccessibleFormItem>
        <AccessibleFormItem label="密码" prop="password" v-slot="{ control }">
          <PasswordInput
            v-bind="control"
            aria-required="true"
            v-model="form.password"
            autocomplete="current-password"
            maxlength="64"
          />
        </AccessibleFormItem>
        <el-button type="primary" native-type="submit" :loading="submitting" class="submit">
          登录
        </el-button>
      </el-form>
      <p class="switch">还没有账号？<RouterLink :to="{ name: 'register' }">去注册</RouterLink></p>
    </el-card>
  </section>
</template>

<style scoped>
.login-page {
  display: flex;
  justify-content: center;
  padding-top: 40px;
}

.login-card {
  width: 100%;
  max-width: 400px;
}

h1 {
  margin: 0 0 20px;
  font-size: 22px;
}

.error {
  margin-bottom: 16px;
}

.submit {
  width: 100%;
  height: 40px;
}

.switch {
  margin: 16px 0 0;
  text-align: center;
  color: var(--app-text-secondary);
}

.switch a {
  color: var(--el-color-primary);
}

@media (max-width: 640px) {
  .login-page {
    padding-top: 8px;
  }

  .switch {
    margin-bottom: 0;
  }
}
</style>
