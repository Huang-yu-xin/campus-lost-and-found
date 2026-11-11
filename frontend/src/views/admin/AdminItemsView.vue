<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  approveItem,
  getAdminItem,
  listAdminItems,
  listReviewRecords,
  returnItem as returnAdminItem,
  takeDownItem,
  type AdminItemStatus,
  type ReviewRecord,
} from '@/api/admin'
import { describeError } from '@/api/feedback'
import type { ItemRecord } from '@/api/items'
import { formatTime } from '@/utils/time'
import { useMediaQuery } from '@/composables/useMediaQuery'

const STATUSES: AdminItemStatus[] = ['PENDING_REVIEW', 'RETURNED', 'PUBLISHED']
const PAGE_SIZE = 10

const route = useRoute()
const narrow = useMediaQuery('(max-width: 768px)')
const initialStatus = STATUSES.find((value) => value === route.query.status) ?? 'PENDING_REVIEW'

const rows = ref<ItemRecord[]>([])
const page = ref(1)
const total = ref(0)
const loading = ref(true)
const actingId = ref<string | null>(null)
const errorText = ref('')
const status = ref<AdminItemStatus>(initialStatus)
const detailVisible = ref(false)
const detailLoading = ref(false)
const selectedItem = ref<ItemRecord | null>(null)
const records = ref<ReviewRecord[]>([])
const recordPage = ref(1)
const recordTotal = ref(0)
const detailError = ref('')

const typeText: Record<string, string> = { LOST: '寻物', FOUND: '招领' }
const actionText: Record<string, string> = { APPROVE: '通过', RETURN: '退回', TAKE_DOWN: '下架' }

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    const result = await listAdminItems(status.value, page.value, PAGE_SIZE)
    // 处理完末页最后一条后退回上一页，避免停在空页
    if (result.items.length === 0 && result.total > 0 && page.value > 1) {
      page.value = Math.ceil(result.total / PAGE_SIZE)
      return await load()
    }
    rows.value = result.items
    total.value = result.total
  } catch (error) {
    errorText.value = describeError(error)
  } finally {
    loading.value = false
  }
}

function switchStatus() {
  page.value = 1
  void load()
}

async function approve(rawRow: unknown) {
  const row = rawRow as ItemRecord
  actingId.value = row.id
  try {
    await approveItem(row.id, row.version)
    ElMessage.success('已通过，信息进入公共列表')
    await load()
  } catch (error) {
    ElMessage.error(describeError(error))
  } finally {
    actingId.value = null
  }
}

async function returnItem(rawRow: unknown) {
  const row = rawRow as ItemRecord
  actingId.value = row.id
  try {
    const { value } = await ElMessageBox.prompt('请填写退回原因（发布者可见，必填）', '退回修改', {
      inputValidator: (v: string) => (v && v.trim().length > 0 ? true : '退回原因必填'),
    })
    await returnAdminItem(row.id, row.version, value.trim())
    ElMessage.success('已退回，发布者可修改后重新提交')
    await load()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(describeError(error))
  } finally {
    actingId.value = null
  }
}

async function showDetail(rawRow: unknown) {
  const row = rawRow as ItemRecord
  detailVisible.value = true
  detailLoading.value = true
  selectedItem.value = null
  records.value = []
  recordPage.value = 1
  detailError.value = ''
  try {
    const [item] = await Promise.all([getAdminItem(row.id), loadRecords(row.id)])
    selectedItem.value = item
  } catch (error) {
    detailError.value = describeError(error)
  } finally {
    detailLoading.value = false
  }
}

async function loadRecords(itemId = selectedItem.value?.id) {
  if (!itemId) return
  const result = await listReviewRecords(itemId, recordPage.value, PAGE_SIZE)
  records.value = result.items
  recordTotal.value = result.total
}

async function changeRecordPage() {
  try {
    await loadRecords()
  } catch (error) {
    detailError.value = describeError(error)
  }
}

onMounted(load)
async function takeDown(rawRow: unknown) {
  const row = rawRow as ItemRecord
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
    await takeDownItem(row.id, row.version, value.trim())
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
      <el-radio-group v-model="status" @change="switchStatus">
        <el-radio-button value="PENDING_REVIEW">待审核</el-radio-button>
        <el-radio-button value="RETURNED">已退回</el-radio-button>
        <el-radio-button value="PUBLISHED">已发布</el-radio-button>
      </el-radio-group>
    </div>

    <el-alert v-if="errorText" :title="errorText" type="warning" :closable="false" show-icon>
      <p class="hint">请使用管理员账号登录后再访问。</p>
    </el-alert>

    <el-skeleton v-else-if="loading" :rows="5" animated />

    <el-empty v-else-if="rows.length === 0" description="这个状态下没有待处理的信息" />

    <template v-else>
      <p v-if="narrow" class="hint">表格可左右滑动，查看完整信息与操作。</p>
      <el-table :data="rows" border>
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ typeText[row.type] ?? row.type }}</template>
        </el-table-column>
        <el-table-column prop="location" label="地点" width="140" />
        <el-table-column prop="publisher.nickname" label="发布者" width="110" />
        <el-table-column label="提交时间" width="200">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column
          v-if="status === 'RETURNED'"
          prop="reviewReason"
          label="退回原因"
          min-width="200"
        />
        <el-table-column label="操作" width="220" :fixed="narrow ? false : 'right'">
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
    </template>
    <el-pagination
      v-if="total > PAGE_SIZE"
      v-model:current-page="page"
      class="pager"
      :page-size="PAGE_SIZE"
      :total="total"
      :pager-count="5"
      layout="prev, pager, next, total"
      :disabled="loading || !!actingId"
      @current-change="load"
    />
    <el-dialog v-model="detailVisible" title="物品审核详情" width="min(720px, 92vw)">
      <p v-if="detailLoading" role="status">正在加载…</p>
      <el-alert v-else-if="detailError" :title="detailError" type="error" :closable="false" />
      <article v-else-if="selectedItem">
        <h2>{{ selectedItem.title }}</h2>
        <p>发布者：{{ selectedItem.publisher.nickname }}</p>
        <p>发生地点：{{ selectedItem.location }} · 发生日期：{{ selectedItem.occurredDate }}</p>
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
        <p v-if="!records.length">暂无审核记录。</p>
        <p v-for="record in records" :key="record.id">
          {{ formatTime(record.createdAt) }} · {{ actionText[record.action] ?? record.action
          }}<span v-if="record.reason">：{{ record.reason }}</span>
        </p>
        <el-pagination
          v-if="recordTotal > PAGE_SIZE"
          v-model:current-page="recordPage"
          small
          :page-size="PAGE_SIZE"
          :total="recordTotal"
          :pager-count="5"
          layout="prev, pager, next"
          @current-change="changeRecordPage"
        />
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
  flex-wrap: wrap;
  gap: 12px;
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
.pager {
  margin-top: 16px;
  justify-content: flex-end;
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
