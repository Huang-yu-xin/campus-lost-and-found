<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { describeError } from '@/api/feedback'
import {
  getAdminReport,
  getReportConversation,
  listAdminReports,
  resolveReport,
} from '@/api/reports'
import type { Report, ReportAction, ReportConversation, ReportStatus } from '@/api/reports'

const status = ref<ReportStatus>('PENDING')
const rows = ref<Report[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(true)
const errorText = ref('')

const drawerVisible = ref(false)
const selected = ref<Report | null>(null)
const selectedLoading = ref(false)

const conversationVisible = ref(false)
const conversation = ref<ReportConversation | null>(null)
const conversationLoading = ref(false)
const conversationError = ref('')

const resolveVisible = ref(false)
const resolving = ref(false)
const resolveForm = reactive<{ action: ReportAction; reason: string }>({
  action: 'KEEP',
  reason: '',
})

const statusText: Record<string, string> = { PENDING: '待处理', RESOLVED: '已处理' }
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
    const result = await listAdminReports(status.value, page.value, pageSize)
    rows.value = result.items
    total.value = result.total
  } catch (error) {
    errorText.value = describeError(error)
  } finally {
    loading.value = false
  }
}

function switchStatus(next: ReportStatus) {
  status.value = next
  page.value = 1
  void load()
}

async function openDetail(rawRow: unknown) {
  const row = rawRow as Report
  drawerVisible.value = true
  selected.value = row
  selectedLoading.value = true
  try {
    selected.value = await getAdminReport(row.id)
  } catch (error) {
    ElMessage.error(describeError(error))
  } finally {
    selectedLoading.value = false
  }
}

async function openConversation(rawRow: unknown) {
  const row = rawRow as Report
  conversationVisible.value = true
  conversation.value = null
  conversationError.value = ''
  conversationLoading.value = true
  try {
    conversation.value = await getReportConversation(row.id)
  } catch (error) {
    conversationError.value = describeError(error)
  } finally {
    conversationLoading.value = false
  }
}

function openResolve(rawRow: unknown) {
  const row = rawRow as Report
  selected.value = row
  resolveForm.action = 'KEEP'
  resolveForm.reason = ''
  resolveVisible.value = true
}

async function submitResolve() {
  if (!selected.value) return
  const reason = resolveForm.reason.trim()
  if (!reason) {
    ElMessage.warning('请填写处理结论')
    return
  }
  resolving.value = true
  try {
    await resolveReport(selected.value.id, selected.value.version, resolveForm.action, reason)
    ElMessage.success('举报已处理')
    resolveVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(describeError(error))
  } finally {
    resolving.value = false
  }
}

function changePage(next: number) {
  page.value = next
  void load()
}

onMounted(load)
</script>

<template>
  <section class="admin-reports">
    <div class="head">
      <h1>举报处理</h1>
      <div class="tools">
        <el-radio-group :model-value="status" @change="switchStatus($event as ReportStatus)">
          <el-radio-button value="PENDING">待处理</el-radio-button>
          <el-radio-button value="RESOLVED">已处理</el-radio-button>
        </el-radio-group>
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="errorText" :title="errorText" type="warning" :closable="false" show-icon />

    <el-skeleton v-else-if="loading" :rows="5" animated />

    <el-empty v-else-if="rows.length === 0" description="当前没有举报记录" />

    <template v-else>
      <el-table :data="rows" border style="width: 100%">
        <el-table-column prop="id" label="编号" width="80" />
        <el-table-column prop="itemId" label="物品" width="80" />
        <el-table-column label="关联会话" width="110">
          <template #default="{ row }">
            <span v-if="row.claimId">认领 #{{ row.claimId }}</span>
            <span v-else-if="row.clueId">线索 #{{ row.clueId }}</span>
            <span v-else>无</span>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="举报理由" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'RESOLVED' ? 'success' : 'warning'">
              {{ statusText[row.status] ?? row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button
              link
              type="primary"
              :disabled="row.status !== 'PENDING' || (!row.claimId && !row.clueId)"
              @click="openConversation(row)"
            >会话</el-button>
            <el-button
              link
              type="danger"
              :disabled="row.status !== 'PENDING'"
              @click="openResolve(row)"
            >处理</el-button>
          </template>
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

    <!-- 举报详情 -->
    <el-drawer v-model="drawerVisible" title="举报详情" size="480px">
      <el-skeleton v-if="selectedLoading" :rows="6" animated />
      <el-descriptions v-else-if="selected" :column="1" border>
        <el-descriptions-item label="举报编号">{{ selected.id }}</el-descriptions-item>
        <el-descriptions-item label="物品编号">{{ selected.itemId }}</el-descriptions-item>
        <el-descriptions-item label="关联认领">
          {{ selected.claimId ?? '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="关联线索">{{ selected.clueId ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="举报理由">{{ selected.reason }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusText[selected.status] }}</el-descriptions-item>
        <el-descriptions-item label="处置动作">
          {{ selected.action ? (actionText[selected.action] ?? selected.action) : '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="处理结论">{{ selected.resolution ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ formatTime(selected.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="处理时间">{{ formatTime(selected.resolvedAt) }}</el-descriptions-item>
      </el-descriptions>
      <p class="hint">
        管理员只有通过待处理举报的“会话”入口才能读取私有留言；每次读取都会记录审计。
      </p>
    </el-drawer>

    <!-- 关联会话（受限读取） -->
    <el-dialog v-model="conversationVisible" title="举报关联会话" width="620px">
      <el-alert
        v-if="conversationError"
        :title="conversationError"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-skeleton v-else-if="conversationLoading" :rows="5" animated />
      <template v-else-if="conversation">
        <el-descriptions :column="2" border class="conversation-meta">
          <el-descriptions-item label="类型">
            {{ conversation.kind === 'CLAIM' ? '认领' : '线索' }}
          </el-descriptions-item>
          <el-descriptions-item label="上下文编号">{{ conversation.contextId }}</el-descriptions-item>
          <el-descriptions-item label="凭证正文" :span="2">
            {{ conversation.evidenceText }}
          </el-descriptions-item>
        </el-descriptions>

        <el-empty v-if="conversation.items.length === 0" description="该会话暂无留言" />
        <ul v-else class="message-list">
          <li v-for="item in conversation.items" :key="item.id" class="message-item">
            <div class="message-head">
              <span>{{ item.sender.nickname }}</span>
              <span>{{ formatTime(item.createdAt) }}</span>
            </div>
            <p class="message-content">{{ item.content }}</p>
          </li>
        </ul>
      </template>
      <template #footer>
        <el-button @click="conversationVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 处理举报 -->
    <el-dialog v-model="resolveVisible" title="处理举报" width="520px">
      <el-form label-width="100px">
        <el-form-item label="举报编号">{{ selected?.id }}</el-form-item>
        <el-form-item label="处置动作">
          <el-select v-model="resolveForm.action" style="width: 100%">
            <el-option label="维持现状（仅写结论）" value="KEEP" />
            <el-option label="违规下架物品" value="TAKE_DOWN" />
            <el-option
              label="驳回关联的待确认申请"
              value="REJECT_PENDING_CLAIM"
              :disabled="!selected?.claimId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="处理结论">
          <el-input
            v-model="resolveForm.reason"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="必填，1~500 字；业务处置与举报结论同一事务提交"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resolveVisible = false">取消</el-button>
        <el-button type="primary" :loading="resolving" @click="submitResolve">提交处理</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.head h1 {
  margin: 0;
  font-size: 22px;
}
.tools {
  display: flex;
  align-items: center;
  gap: 12px;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
.hint {
  margin-top: 16px;
  color: var(--app-text-secondary, #666);
  font-size: 13px;
}
.conversation-meta {
  margin-bottom: 16px;
}
.message-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 320px;
  overflow-y: auto;
}
.message-item {
  padding: 10px 12px;
  border: 1px solid var(--app-border, #ebeef5);
  border-radius: 8px;
}
.message-head {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--app-text-secondary, #666);
}
.message-content {
  margin: 6px 0 0;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
