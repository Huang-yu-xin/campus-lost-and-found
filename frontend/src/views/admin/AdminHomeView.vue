<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { listAdminItems } from '@/api/admin'
import { describeError } from '@/api/feedback'
import type { ItemRecord } from '@/api/items'
import { listAdminReports } from '@/api/reports'
import { formatTime } from '@/utils/time'

interface Stat {
  label: string
  count: number
  to: { name: string; query?: Record<string, string> }
}

const stats = ref<Stat[]>([])
const oldestPending = ref<ItemRecord[]>([])
const loading = ref(true)
const errorText = ref('')

// 只读取各列表的 total；待审核列表按提交时间升序，前几条就是等待最久的信息
async function load() {
  loading.value = true
  errorText.value = ''
  try {
    const [pending, returned, published, reports] = await Promise.all([
      listAdminItems('PENDING_REVIEW', 1, 5),
      listAdminItems('RETURNED', 1, 1),
      listAdminItems('PUBLISHED', 1, 1),
      listAdminReports('PENDING', 1, 1),
    ])
    stats.value = [
      { label: '待审核', count: pending.total, to: { name: 'admin-items' } },
      {
        label: '已退回待修改',
        count: returned.total,
        to: { name: 'admin-items', query: { status: 'RETURNED' } },
      },
      {
        label: '已发布',
        count: published.total,
        to: { name: 'admin-items', query: { status: 'PUBLISHED' } },
      },
      { label: '待处理举报', count: reports.total, to: { name: 'admin-reports' } },
    ]
    oldestPending.value = pending.items
  } catch (error) {
    errorText.value = describeError(error)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="admin-home">
    <div class="head">
      <h1>概览</h1>
      <el-button :disabled="loading" @click="load">刷新</el-button>
    </div>

    <el-alert v-if="errorText" :title="errorText" type="warning" :closable="false" show-icon />

    <el-skeleton v-else-if="loading" :rows="4" animated />

    <template v-else>
      <div class="stats">
        <RouterLink v-for="stat in stats" :key="stat.label" :to="stat.to" class="stat">
          <span class="stat-count">{{ stat.count }}</span>
          <span class="stat-label">{{ stat.label }}</span>
        </RouterLink>
      </div>

      <h2>等待最久的待审核信息</h2>
      <p v-if="!oldestPending.length" class="muted">目前没有待审核的信息。</p>
      <ul v-else class="pending">
        <li v-for="item in oldestPending" :key="item.id">
          <span class="title">{{ item.title }}</span>
          <span class="muted">{{ item.publisher.nickname }} · {{ formatTime(item.createdAt) }}</span>
        </li>
      </ul>
      <RouterLink v-if="oldestPending.length" :to="{ name: 'admin-items' }">前往审核</RouterLink>
    </template>
  </section>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.head h1 {
  margin: 0;
  font-size: 22px;
}
h2 {
  margin: 24px 0 12px;
  font-size: 18px;
}
.stats {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
}
.stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 16px;
  border: 1px solid var(--app-border, #ebeef5);
  border-radius: 8px;
  background: var(--app-surface, #fff);
  color: var(--app-text, #303133);
}
.stat:hover {
  border-color: var(--el-color-primary);
}
.stat-count {
  font-size: 28px;
  font-weight: 600;
}
.stat-label,
.muted {
  color: var(--app-text-secondary, #666);
  font-size: 13px;
}
.pending {
  list-style: none;
  margin: 0 0 12px;
  padding: 0;
}
.pending li {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid var(--app-border, #ebeef5);
}
.title {
  overflow-wrap: anywhere;
}
</style>
