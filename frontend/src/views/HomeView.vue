<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { getCsrf } from '@/api/auth'
import { describeError } from '@/api/feedback'
import { useAuthStore } from '@/stores/auth'

// 基础框架阶段的首页：检查前端 → 代理 → 后端这条链路。物品列表做好后由负责人替换本页内容

type CheckState = 'checking' | 'ok' | 'failed'

const auth = useAuthStore()
const backendState = ref<CheckState>('checking')
const backendMessage = ref('')

async function checkBackend() {
  backendState.value = 'checking'
  try {
    await getCsrf()
    backendState.value = 'ok'
    backendMessage.value = '前端已通过代理连上后端，会话与 CSRF 令牌正常'
  } catch (error) {
    backendState.value = 'failed'
    backendMessage.value = describeError(error)
  }
}

onMounted(checkBackend)
</script>

<template>
  <section class="home">
    <h1>校园失物招领系统</h1>
    <p class="subtitle">发布与查找物品信息，跟进认领和处理进度。</p>

    <el-card shadow="never" class="status-card">
      <template #header>常用功能</template>
      <div class="entries">
        <el-button type="primary" @click="$router.push({ name: 'items' })">浏览物品</el-button>
        <template v-if="!auth.isAdmin">
          <el-button @click="$router.push({ name: 'item-publish' })">发布信息</el-button>
          <el-button v-if="auth.isLoggedIn" @click="$router.push({ name: 'my-items' })"
            >我的发布</el-button
          >
          <el-button v-if="auth.isLoggedIn" @click="$router.push({ name: 'claim-received' })"
            >我收到的认领</el-button
          >
        </template>
        <template v-else>
          <el-button @click="$router.push({ name: 'admin-items' })">信息审核</el-button>
          <el-button @click="$router.push({ name: 'admin-reports' })">举报处理</el-button>
        </template>
      </div>
      <p v-if="!auth.isLoggedIn" class="login-state">登录后可以发布信息和管理本人记录。</p>
    </el-card>

    <el-card shadow="never" class="status-card">
      <template #header>系统连通性</template>
      <el-result
        v-if="backendState !== 'checking'"
        :icon="backendState === 'ok' ? 'success' : 'error'"
        :title="backendState === 'ok' ? '后端连接正常' : '后端连接失败'"
        :sub-title="backendMessage"
      >
        <template #extra>
          <el-button @click="checkBackend">重新检查</el-button>
        </template>
      </el-result>
      <p v-else class="checking">正在检查后端连接…</p>
      <p class="login-state">
        当前状态：{{ auth.isLoggedIn ? `已登录（${auth.me?.nickname}）` : '未登录' }}
      </p>
    </el-card>
  </section>
</template>

<style scoped>
.home {
  width: 100%;
  max-width: 720px;
  margin-inline: auto;
}

.home h1 {
  margin: 0 0 8px;
  font-size: 24px;
}

.subtitle {
  margin: 0 0 24px;
  color: var(--app-text-secondary);
}

.status-card {
  margin-bottom: 20px;
}

.checking,
.login-state {
  text-align: center;
  color: var(--app-text-secondary);
}

.entries {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 10px;
}
</style>
