<script setup lang="ts">
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

import { register, sendRegistrationCode } from '@/api/auth'
import { describeError } from '@/api/feedback'
import AccessibleFormItem from '@/components/AccessibleFormItem.vue'
import PasswordInput from '@/components/PasswordInput.vue'
import { validateForm } from '@/utils/validateForm'
import { textLength } from '@/utils/text'

// FR-01 注册：学号、姓名、校园卡号须与虚拟名册一致；邮箱验证码为模拟发送。注册成功不自动登录

const router = useRouter()

const formRef = ref<FormInstance>()
const form = reactive({
  studentId: '',
  realName: '',
  campusCardNo: '',
  email: '',
  emailCode: '',
  password: '',
  confirmPassword: '',
  nickname: '',
})
const submitting = ref(false)
const sending = ref(false)
const countdown = ref(0)
const errorMessage = ref('')
let timer: ReturnType<typeof setInterval> | undefined

const rules: FormRules<typeof form> = {
  studentId: [
    { required: true, message: '请输入学号', trigger: 'blur' },
    { pattern: /^[0-9]{1,32}$/, message: '学号只能是数字', trigger: 'blur' },
  ],
  realName: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { pattern: /^[㐀-鿿]{2,10}$/, message: '姓名为 2～10 个汉字', trigger: 'blur' },
  ],
  campusCardNo: [{ required: true, message: '请输入校园卡号', trigger: 'blur' }],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  emailCode: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { pattern: /^[0-9]{6}$/, message: '验证码为 6 位数字', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    {
      pattern: /^(?=.*[A-Za-z])(?=.*[0-9]).{8,64}$/,
      message: '密码为 8～64 位，且同时包含字母和数字',
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) =>
        value === form.password ? callback() : callback(new Error('两次输入的密码不一致')),
      trigger: 'blur',
    },
  ],
  nickname: [
    {
      validator: (_rule, value: string, callback) =>
        textLength(value) <= 30 ? callback() : callback(new Error('昵称最多 30 个字符')),
      trigger: 'blur',
    },
  ],
}

function startCountdown(seconds: number) {
  countdown.value = seconds
  clearInterval(timer)
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      clearInterval(timer)
    }
  }, 1000)
}

async function sendCode() {
  if (sending.value || countdown.value > 0) {
    return
  }
  const emailValid = await formRef.value
    ?.validateField('email')
    .then(() => true)
    .catch(() => false)
  if (!emailValid) {
    return
  }
  sending.value = true
  errorMessage.value = ''
  try {
    const result = await sendRegistrationCode(form.email.trim())
    startCountdown(result.retryAfterSeconds)
    ElMessage.success(
      result.deliveryMode === 'DEV_FILE'
        ? '验证码已模拟发送，请查看开发收件箱'
        : '验证码已发送，请查看邮箱',
    )
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    sending.value = false
  }
}

async function submit() {
  if (submitting.value || !(await validateForm(formRef.value))) {
    return
  }
  submitting.value = true
  errorMessage.value = ''
  try {
    const nickname = form.nickname.trim()
    await register({
      studentId: form.studentId.trim(),
      realName: form.realName.trim(),
      campusCardNo: form.campusCardNo.trim(),
      email: form.email.trim(),
      emailCode: form.emailCode.trim(),
      password: form.password,
      nickname: nickname || undefined,
    })
    ElMessage.success('注册成功，请登录')
    await router.push({ name: 'login', query: { account: form.studentId.trim() } })
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    submitting.value = false
  }
}

onBeforeUnmount(() => clearInterval(timer))
</script>

<template>
  <section class="register-page">
    <el-card shadow="never" class="register-card">
      <h1>注册</h1>
      <el-alert
        title="身份信息需与学校名册一致。获取验证码后按提示查看邮箱；开发环境使用模拟发送时，请查看后端开发收件箱。"
        type="info"
        :closable="false"
        class="notice"
      />
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
        <AccessibleFormItem label="学号" prop="studentId" v-slot="{ control }">
          <el-input
            v-bind="control"
            aria-required="true"
            v-model="form.studentId"
            maxlength="32"
            inputmode="numeric"
          />
        </AccessibleFormItem>
        <AccessibleFormItem label="姓名" prop="realName" v-slot="{ control }">
          <el-input v-bind="control" aria-required="true" v-model="form.realName" maxlength="10" />
        </AccessibleFormItem>
        <AccessibleFormItem label="校园卡号" prop="campusCardNo" v-slot="{ control }">
          <el-input
            v-bind="control"
            aria-required="true"
            v-model="form.campusCardNo"
            maxlength="32"
          />
        </AccessibleFormItem>
        <AccessibleFormItem label="邮箱" prop="email" v-slot="{ control }">
          <el-input
            v-bind="control"
            aria-required="true"
            v-model="form.email"
            maxlength="254"
            autocomplete="email"
          />
        </AccessibleFormItem>
        <AccessibleFormItem label="验证码" prop="emailCode" v-slot="{ control }">
          <div class="code-row">
            <el-input
              v-bind="control"
              aria-required="true"
              v-model="form.emailCode"
              maxlength="6"
              inputmode="numeric"
            />
            <el-button :loading="sending" :disabled="countdown > 0" @click="sendCode">
              {{ countdown > 0 ? `${countdown} 秒后重发` : '获取验证码' }}
            </el-button>
          </div>
        </AccessibleFormItem>
        <AccessibleFormItem label="密码" prop="password" v-slot="{ control }">
          <PasswordInput
            v-bind="control"
            aria-required="true"
            v-model="form.password"
            maxlength="64"
            autocomplete="new-password"
          />
        </AccessibleFormItem>
        <AccessibleFormItem label="确认密码" prop="confirmPassword" v-slot="{ control }">
          <PasswordInput
            v-bind="control"
            aria-required="true"
            toggle-label="显示确认密码"
            v-model="form.confirmPassword"
            maxlength="64"
            autocomplete="new-password"
          />
        </AccessibleFormItem>
        <AccessibleFormItem label="昵称（选填，公开显示）" prop="nickname" v-slot="{ control }">
          <el-input v-bind="control" v-model="form.nickname" placeholder="不填则自动生成" />
        </AccessibleFormItem>
        <el-button type="primary" native-type="submit" :loading="submitting" class="submit">
          注册
        </el-button>
      </el-form>
      <p class="switch">已有账号？<RouterLink :to="{ name: 'login' }">去登录</RouterLink></p>
    </el-card>
  </section>
</template>

<style scoped>
.register-page {
  display: flex;
  justify-content: center;
  padding-top: 24px;
}

.register-card {
  width: 100%;
  max-width: 440px;
}

h1 {
  margin: 0 0 16px;
  font-size: 22px;
}

.notice,
.error {
  margin-bottom: 16px;
}

.code-row {
  display: flex;
  gap: 8px;
  width: 100%;
  min-width: 0;
}

.code-row .el-input {
  flex: 1;
  min-width: 0;
}

.code-row .el-button {
  flex-shrink: 0;
  width: 112px;
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
  .register-page {
    padding-top: 8px;
  }
}

@media (max-width: 400px) {
  .code-row {
    flex-wrap: wrap;
  }

  .code-row .el-input,
  .code-row .el-button {
    flex-basis: 100%;
    width: 100%;
  }
}
</style>
