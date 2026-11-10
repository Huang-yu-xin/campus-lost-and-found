<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { describeError } from '@/api/feedback'
import { http } from '@/api/http'

interface ReceivedClaim {
  id: number
  itemId: number
  itemTitle: string
  itemStatus: string
  applicantNickname: string
  status: string
  reason: string | null
  createdAt: string
}

const router = useRouter()
const rows = ref<ReceivedClaim[]>([])
const loading = ref(true)
const errorText = ref('')

const statusText: Record<string, string> = {
  PENDING: '待确认',
  CONFIRMED: '已确认',
  REJECTED: '已驳回',
  COMPLETED: '已完成',
  CLOSED: '已关闭',
}

function tagType(status: string) {
  if (status === 'CONFIRMED') return 'success'
  if (status === 'COMPLETED') return 'info'
  if (status === 'REJECTED' || status === 'CLOSED') return 'danger'
  return 'warning'
}

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    rows.value = await http.get<ReceivedClaim[]>('/claims/received')
  } catch (error) {
    errorText.value = describeError(error)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="claim-list">
    <div class="head">
      <h1>我收到的认领申请</h1>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-alert v-if="errorText" :title="errorText" type="warning" :closable="false" show-icon>
      <p class="hint">请确认登录状态后重新加载。</p>
    </el-alert>

    <el-skeleton v-else-if="loading" :rows="5" animated />

    <el-empty v-else-if="rows.length === 0" description="还没有人申请认领你发布的信息" />

    <el-table v-else :data="rows" border style="width: 100%">
      <el-table-column prop="itemTitle" label="物品" min-width="180" />
      <el-table-column prop="applicantNickname" label="申请人" width="120" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="tagType(row.status)">{{ statusText[row.status] ?? row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="申请时间" width="200" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/claims/${row.id}`)"
            >查看 / 处理</el-button
          >
        </template>
      </el-table-column>
    </el-table>
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
.hint {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--app-text-secondary, #666);
}
</style>
