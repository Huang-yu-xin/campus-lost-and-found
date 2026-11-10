<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { describeError } from '@/api/feedback'
import { listMyReports } from '@/api/reports'
import type { Report } from '@/api/reports'

const rows = ref<Report[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(true)
const errorText = ref('')

const statusText: Record<string, string> = {
  PENDING: '待处理',
  RESOLVED: '已处理',
}

const actionText: Record<string, string> = {
  KEEP: '维持现状',
  TAKE_DOWN: '违规下架',
  REJECT_PENDING_CLAIM: '驳回关联申请',
}

function formatTime(value: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    const result = await listMyReports(page.value, pageSize)
    rows.value = result.items
    total.value = result.total
  } catch (error) {
    errorText.value = describeError(error)
  } finally {
    loading.value = false
  }
}

function changePage(next: number) {
  page.value = next
  void load()
}

onMounted(load)
</script>

<template>
  <section class="my-reports">
    <div class="head">
      <h1>我的举报</h1>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-alert v-if="errorText" :title="errorText" type="warning" :closable="false" show-icon />

    <el-skeleton v-else-if="loading" :rows="5" animated />

    <el-empty v-else-if="rows.length === 0" description="还没有提交过举报" />

    <template v-else>
      <el-table :data="rows" border style="width: 100%">
        <el-table-column prop="id" label="举报编号" width="100" />
        <el-table-column prop="itemId" label="物品" width="90" />
        <el-table-column label="关联会话" width="120">
          <template #default="{ row }">
            <span v-if="row.claimId">认领 #{{ row.claimId }}</span>
            <span v-else-if="row.clueId">线索 #{{ row.clueId }}</span>
            <span v-else>公开物品</span>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="举报理由" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'RESOLVED' ? 'success' : 'warning'">
              {{ statusText[row.status] ?? row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理结果" min-width="220">
          <template #default="{ row }">
            <span v-if="row.status === 'PENDING'" class="muted">等待管理员处理</span>
            <span v-else>
              {{ actionText[row.action ?? ''] ?? row.action }}：{{ row.resolution }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        layout="prev, pager, next, total"
        :current-page="page"
        :page-size="pageSize"
        :total="total"
        @current-change="changePage"
      />
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
.muted {
  color: var(--app-text-secondary, #666);
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
