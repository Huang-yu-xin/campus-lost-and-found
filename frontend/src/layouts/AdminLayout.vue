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
  <div class="admin-layout">
    <aside class="sidebar">
      <RouterLink to="/admin" class="brand">管理后台</RouterLink>
      <nav class="menu" aria-label="管理端导航">
        <RouterLink to="/admin">概览</RouterLink>
        <RouterLink to="/admin/items">物品审核</RouterLink>
        <RouterLink to="/admin/reports">举报处理</RouterLink>
      </nav>
      <div class="sidebar-footer">
        <span>{{ auth.me?.nickname }}</span>
        <RouterLink to="/">返回学生端</RouterLink>
        <el-button link @click="logout">退出</el-button>
      </div>
    </aside>
    <main class="content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
}

.sidebar {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 200px;
  flex-shrink: 0;
  padding: 20px 16px;
  background: var(--app-surface);
  border-right: 1px solid var(--app-border);
}

.brand {
  font-size: 18px;
  font-weight: 600;
  color: var(--app-text);
}

.menu {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}

.menu a {
  padding: 8px 12px;
  border-radius: 6px;
}

.menu a.router-link-exact-active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.sidebar-footer {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: var(--app-text-secondary);
  min-width: 0;
  overflow-wrap: anywhere;
}

/* 管理端以表格为主，内容区留出横向空间；表格过宽时由表格自身横向滚动 */
.content {
  flex: 1;
  min-width: 0;
  padding: 24px;
}

/* 手机窄屏：侧边栏改为顶部栏 */
@media (max-width: 768px) {
  .admin-layout {
    flex-direction: column;
  }

  .sidebar {
    width: auto;
    flex-direction: row;
    flex-wrap: wrap;
    align-items: center;
    border-right: none;
    border-bottom: 1px solid var(--app-border);
    padding: 12px 16px;
  }

  .menu {
    flex-direction: row;
    flex-basis: 100%;
    order: 3;
    flex-wrap: wrap;
  }

  .sidebar-footer {
    flex-direction: row;
    gap: 12px;
    margin-left: auto;
    flex-wrap: wrap;
    flex: 1 1 100%;
  }

  .sidebar-footer > span {
    flex: 1 1 100%;
  }

  .menu a,
  .sidebar-footer a {
    display: inline-flex;
    align-items: center;
    min-height: 44px;
  }

  .content {
    padding: 16px;
  }
}
</style>
