<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { showError } from '@/api/feedback'
import { useAuthStore } from '@/stores/auth'
import { useMediaQuery } from '@/composables/useMediaQuery'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const compact = useMediaQuery('(max-width: 640px)')
const menuOpen = ref(false)
const menuButton = ref<HTMLButtonElement>()

function closeMenu(restoreFocus = false) {
  menuOpen.value = false
  if (restoreFocus) menuButton.value?.focus()
}

watch(
  () => route.fullPath,
  () => closeMenu(),
)
watch(compact, () => closeMenu())

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
      <div class="container header-inner" @keydown.esc.stop="closeMenu(true)">
        <RouterLink to="/" class="brand">校园失物招领</RouterLink>
        <button
          ref="menuButton"
          class="menu-toggle"
          type="button"
          aria-controls="student-navigation"
          :aria-expanded="menuOpen"
          @click="menuOpen = !menuOpen"
        >
          {{ menuOpen ? '收起菜单' : '展开菜单' }}
        </button>
        <nav
          id="student-navigation"
          class="nav"
          :class="{ 'is-open': menuOpen }"
          aria-label="学生端导航"
          @click="closeMenu()"
        >
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
            <RouterLink :to="{ name: 'my-clues' }">我的线索</RouterLink>
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

.menu-toggle {
  display: none;
  border: 1px solid var(--app-border);
  border-radius: 6px;
  background: var(--app-surface);
  color: var(--app-text);
  padding: 8px 12px;
  font: inherit;
  cursor: pointer;
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
  min-width: 0;
  max-width: 280px;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.nickname {
  color: var(--app-text-secondary);
  overflow-wrap: anywhere;
  min-width: 0;
  max-width: 14em;
}

.page {
  padding-top: 24px;
  padding-bottom: 48px;
}

/* 手机使用可键盘操作的展开导航，保留所有原入口。 */
@media (max-width: 640px) {
  .header-inner {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 8px 16px;
    padding-top: 8px;
    padding-bottom: 8px;
  }

  .nav {
    display: none;
    grid-column: 1 / -1;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 4px 12px;
  }

  .nav.is-open {
    display: grid;
  }

  .nav a {
    display: flex;
    align-items: center;
    min-height: 44px;
    padding: 8px;
    border-radius: 4px;
  }

  .menu-toggle {
    display: block;
    min-height: 44px;
    grid-column: 2;
    grid-row: 1;
  }

  .user-area {
    grid-row: 2;
    grid-column: 1 / -1;
    max-width: none;
  }

  .nickname {
    max-width: none;
    flex: 1;
  }

  .user-area > a {
    display: inline-flex;
    align-items: center;
    min-height: 44px;
  }
}
</style>
