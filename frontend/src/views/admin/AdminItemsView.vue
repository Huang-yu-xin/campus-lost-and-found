<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { describeError } from '@/api/feedback'
import { http } from '@/api/http'

interface AdminItem {
  id: number
  title: string
  type: string
  category: string
  location: string
  status: string
  version: number
  reviewReason: string | null
  publisherNickname: string
  createdAt: string
}

interface AdminItemDetail {
  id: number
  title: string
  description: string
  location: string
  status: string
  images: { id: string; url: string }[]
  reviewRecords: { id: number; action: string; reason: string | null; operator_nickname: string }[]
}

const rows = ref<AdminItem[]>([])
const loading = ref(true)
const actingId = ref<number | null>(null)
const errorText = ref('')
const status = ref<'PENDING_REVIEW' | 'RETURNED' | 'PUBLISHED'>('PENDING_REVIEW')
const detailVisible = ref(false)
const detailLoading = ref(false)
const selectedItem = ref<AdminItemDetail | null>(null)
const detailError = ref('')

const typeText: Record<string, string> = { LOST: '寻物', FOUND: '招领' }

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    rows.value = await http.get<AdminItem[]>(`/admin/items?status=${status.value}`)
  } catch (error) {
    errorText.value = describeError(error)
  } finally {
    loading.value = false
  }
}

async function approve(rawRow: unknown) {
  const row = rawRow as AdminItem
  actingId.value = row.id
  try {
    await http.post(`/admin/items/${row.id}/approve`, { expectedVersion: row.version })
    ElMessage.success('已通过，信息进入公共列表')
    await load()
  } catch (error) {
    ElMessage.error(describeError(error))
  } finally {
    actingId.value = null
  }
}

async function returnItem(rawRow: unknown) {
  const row = rawRow as AdminItem
  actingId.value = row.id
  try {
    const { value } = await ElMessageBox.prompt('请填写退回原因（发布者可见，必填）', '退回修改', {
      inputValidator: (v: string) => (v && v.trim().length > 0 ? true : '退回原因必填'),
    })
    await http.post(`/admin/items/${row.id}/return`, {
      expectedVersion: row.version,
      reason: value.trim(),
    })
    ElMessage.success('已退回，发布者可修改后重新提交')
    await load()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(describeError(error))
  } finally {
    actingId.value = null
  }
}

async function showDetail(rawRow: unknown) {
  const row = rawRow as AdminItem
  detailVisible.value = true
  detailLoading.value = true
  selectedItem.value = null
  detailError.value = ''
  try {
    selectedItem.value = await http.get<AdminItemDetail>(`/admin/items/${row.id}`)
  } catch (error) {
    detailError.value = describeError(error)
  } finally {
    detailLoading.value = false
  }
}

onMounted(load)
async function takeDown(rawRow: unknown) {
  const row = rawRow as AdminItem
  if (actingId.value) return
  actingId.value = row.id
  try {
    const { value } = await ElMessageBox.prompt(
      '请填写下架原因（1～500 字，发布者可见）',
      '下架信息',
      {
        inputValidator: (v: string) =>
          (!!v?.trim() && v.trim().length <= 500) || '请填写 1～500 字的原因',
      },
    )
    await http.post(`/admin/items/${row.id}/take-down`, {
      expectedVersion: row.version,
      reason: value.trim(),
    })
    ElMessage.success('已下架，相关会话停止新增留言')
    await load()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(describeError(e))
  } finally {
    actingId.value = null
  }
}
</script>

<template>
  <section class="admin-items">
    <div class="head">
      <h1>信息管理</h1>
      <el-radio-group v-model="status" @change="load">
        <el-radio-button label="PENDING_REVIEW">待审核</el-radio-button>
        <el-radio-button label="RETURNED">已退回</el-radio-button>
        <el-radio-button value="PUBLISHED">已发布</el-radio-button>
      </el-radio-group>
    </div>

    <el-alert v-if="errorText" :title="errorText" type="warning" :closable="false" show-icon>
      <p class="hint">请使用管理员账号登录后再访问。</p>
    </el-alert>

    <el-skeleton v-else-if="loading" :rows="5" animated />

    <el-empty v-else-if="rows.length === 0" description="这个状态下没有待处理的信息" />

    <el-table v-else :data="rows" border>
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">{{ typeText[row.type] ?? row.type }}</template>
      </el-table-column>
      <el-table-column prop="location" label="地点" width="140" />
      <el-table-column prop="publisherNickname" label="发布者" width="110" />
      <el-table-column prop="createdAt" label="提交时间" width="200" />
      <el-table-column
        v-if="status === 'RETURNED'"
        prop="reviewReason"
        label="退回原因"
        min-width="200"
      />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDetail(row)">查看详情</el-button>
          <template v-if="status === 'PENDING_REVIEW'">
            <el-button link type="success" :loading="actingId === row.id" @click="approve(row)"
              >通过</el-button
            >
            <el-button link type="warning" :loading="actingId === row.id" @click="returnItem(row)"
              >退回</el-button
            >
          </template>
          <el-button
            v-else-if="status === 'PUBLISHED'"
            link
            type="danger"
            :disabled="!!actingId"
            @click="takeDown(row)"
            >下架</el-button
          >
          <span v-else class="muted">已退回，等待发布者修改</span>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="detailVisible" title="物品审核详情" width="min(720px, 92vw)">
      <p v-if="detailLoading" role="status">正在加载…</p>
      <el-alert v-else-if="detailError" :title="detailError" type="error" :closable="false" />
      <article v-else-if="selectedItem">
        <h2>{{ selectedItem.title }}</h2>
        <p>发生地点：{{ selectedItem.location }}</p>
        <p class="description">{{ selectedItem.description }}</p>
        <div class="images">
          <img
            v-for="image in selectedItem.images"
            :key="image.id"
            :src="image.url"
            alt="待审核物品图片"
          />
        </div>
        <h3>审核记录</h3>
        <p v-if="!selectedItem.reviewRecords.length">暂无审核记录。</p>
        <p v-for="record in selectedItem.reviewRecords" :key="record.id">
          {{ record.operator_nickname }} · {{ record.action
          }}<span v-if="record.reason">：{{ record.reason }}</span>
        </p>
      </article>
    </el-dialog>
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
.muted {
  color: var(--app-text-secondary, #888);
  font-size: 13px;
}
.description {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.images {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.images img {
  width: 180px;
  max-width: 100%;
  height: 140px;
  object-fit: contain;
}
</style>
