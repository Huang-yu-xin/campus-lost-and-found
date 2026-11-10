<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { Claim } from '@/api/claim'

import { describeError } from '@/api/feedback'
import { http } from '@/api/http'

const router = useRouter()
const route = useRoute()
const mine = computed(() => route.name === 'my-claims')
const rows = ref<Claim[]>([])
const page = ref(1)
const total = ref(0)
const filter = ref('')
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
    const data = await http.get<{ items: Claim[]; total: number }>(
      mine.value ? '/me/claims' : '/me/received-claims',
      { params: { page: page.value, pageSize: 10, status: filter.value || undefined } },
    )
    rows.value = data.items
    total.value = data.total
  } catch (error) {
    errorText.value = describeError(error)
  } finally {
    loading.value = false
  }
}

watch(
  [mine, filter],
  () => {
    page.value = 1
    void load()
  },
  { immediate: true },
)
</script>

<template>
  <section class="claim-list">
    <div class="head">
      <h1>{{ mine ? '我申请的认领' : '我收到的认领申请' }}</h1>
      <el-button @click="load">刷新</el-button>
    </div>
    <el-select
      v-model="filter"
      placeholder="全部状态"
      clearable
      style="width: 180px; margin-bottom: 16px"
    >
      <el-option v-for="(text, value) in statusText" :key="value" :value="value" :label="text" />
    </el-select>

    <el-alert v-if="errorText" :title="errorText" type="warning" :closable="false" show-icon>
      <p class="hint">请确认登录状态后重新加载。</p>
    </el-alert>

    <el-skeleton v-else-if="loading" :rows="5" animated />

    <el-empty v-else-if="rows.length === 0" description="暂无符合条件的认领申请" />

    <el-table v-else :data="rows" border style="width: 100%">
      <el-table-column prop="itemId" label="物品编号" min-width="120" />
      <el-table-column
        :prop="mine ? 'publisher.nickname' : 'applicant.nickname'"
        :label="mine ? '发布人' : '申请人'"
        width="120"
      />
      <el-table-column prop="description" label="特征说明" min-width="180" show-overflow-tooltip />
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
    <el-pagination
      v-if="total > 0"
      v-model:current-page="page"
      :page-size="10"
      :total="total"
      layout="prev, pager, next, total"
      @current-change="load"
    />
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
