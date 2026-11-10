<script setup lang="ts">
import { useRouter } from 'vue-router'

import { showError } from '@/api/feedback'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

async function logout() {
  try {
    await auth.logout()
  } catch (error) {
    showError(error)
  }
  await router.push({ name: 'home' })
}
</script>

<template>
  <div class="student-layout">
    <header class="site-header">
      <div class="container header-inner">
        <RouterLink to="/" class="brand">校园失物招领</RouterLink>
        <nav class="nav">
          <RouterLink to="/">首页</RouterLink>
          <RouterLink :to="{ name: 'items' }">浏览物品</RouterLink>
          <RouterLink v-if="!auth.isAdmin" :to="{ name: 'item-publish' }">发布信息</RouterLink>
          <RouterLink v-if="auth.isLoggedIn && !auth.isAdmin" :to="{ name: 'my-items' }"
            >我的发布</RouterLink
          >
          <template v-if="auth.isLoggedIn && !auth.isAdmin">
            <RouterLink :to="{ name: 'my-claims' }">我申请的认领</RouterLink>
            <RouterLink :to="{ name: 'claim-received' }">我收到的认领</RouterLink>
            <RouterLink :to="{ name: 'my-reports' }">我的举报</RouterLink>
          </template>
        </nav>
        <div class="user-area">
          <template v-if="auth.isLoggedIn">
            <RouterLink :to="{ name: 'profile' }" class="nickname">{{
              auth.me?.nickname
            }}</RouterLink>
            <RouterLink v-if="auth.isAdmin" to="/admin">管理后台</RouterLink>
            <el-button link @click="logout">退出</el-button>
          </template>
          <RouterLink v-else :to="{ name: 'login' }">
            <el-button type="primary" size="small">登录</el-button>
          </RouterLink>
        </div>
      </div>
    </header>
    <main class="container page">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.site-header {
  background: var(--app-surface);
  border-bottom: 1px solid var(--app-border);
}

.header-inner {
  display: flex;
  align-items: center;
  gap: 24px;
  min-height: 56px;
}

.brand {
  white-space: nowrap;
  font-size: 18px;
  font-weight: 600;
  color: var(--app-text);
}

.nav {
  display: flex;
  flex-wrap: wrap;
  min-width: 0;
  gap: 16px;
  flex: 1;
}

.nav a.router-link-exact-active {
  color: var(--el-color-primary);
  font-weight: 600;
}

.user-area {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 12px;
}

.nickname {
  color: var(--app-text-secondary);
}

.page {
  padding-top: 24px;
  padding-bottom: 48px;
}

/* 手机窄屏：导航换到第二行，保证可点击区域足够大 */
@media (max-width: 640px) {
  .header-inner {
    flex-wrap: wrap;
    gap: 8px 16px;
    padding-top: 8px;
    padding-bottom: 8px;
  }

  .nav {
    order: 3;
    flex-basis: 100%;
  }

  .nav a {
    padding: 8px 0;
  }

  .user-area {
    margin-left: auto;
  }
}
</style>
