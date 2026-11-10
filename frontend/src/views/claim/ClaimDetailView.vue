<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { describeError } from '@/api/feedback'
import {
  completeClaim,
  confirmClaim,
  getClaim,
  getContactPermission,
  getCounterpartyContact,
  listMessages,
  rejectClaim,
  sendMessage,
  setContactPermission,
} from '@/api/claim'
import type { ClaimDetail, ContactPermission, CounterpartyContact, Message } from '@/api/claim'
import { createReport } from '@/api/reports'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const auth = useAuthStore()

const claimId = Number(route.params.claimId)
const detail = ref<ClaimDetail | null>(null)
const loading = ref(true)
const acting = ref(false)
const errorText = ref('')

const statusText: Record<string, string> = {
  PENDING: '待确认',
  CONFIRMED: '已确认',
  REJECTED: '已驳回',
  COMPLETED: '已完成',
  CLOSED: '已关闭',
}

const isPublisher = computed(
  () =>
    !!detail.value &&
    !!auth.me &&
    detail.value.claim.publisher.id === Number(auth.me.id),
)
const isApplicant = computed(
  () =>
    !!detail.value &&
    !!auth.me &&
    detail.value.claim.applicant.id === Number(auth.me.id),
)
const isParticipant = computed(() => isPublisher.value || isApplicant.value)

/** BR-08：PENDING/CONFIRMED 且物品仍 PUBLISHED 时可留言。 */
const canChat = computed(() => {
  const claim = detail.value?.claim
  const item = detail.value?.item
  return (
    !!claim &&
    !!item &&
    (claim.status === 'PENDING' || claim.status === 'CONFIRMED') &&
    item.status === 'PUBLISHED'
  )
})

/** BR-07：确认且物品发布中才可设置或查询联系方式。 */
const contactEffective = computed(
  () =>
    detail.value?.claim.status === 'CONFIRMED' && detail.value?.item.status === 'PUBLISHED',
)

const messages = ref<Message[]>([])
const messageText = ref('')
const sending = ref(false)
const loadingMessages = ref(false)
const messageError = ref('')

const permission = ref<ContactPermission | null>(null)
const permissionForm = ref({ phoneVisible: false, emailVisible: false })
const savingPermission = ref(false)
const permissionError = ref('')
const counterparty = ref<CounterpartyContact | null>(null)
const loadingContact = ref(false)
const contactError = ref('')

function formatTime(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    detail.value = await getClaim(claimId)
    if (isParticipant.value) {
      await Promise.all([loadMessages(), loadPermission()])
    }
  } catch (error) {
    errorText.value = describeError(error)
  } finally {
    loading.value = false
  }
}

async function loadMessages() {
  loadingMessages.value = true
  messageError.value = ''
  try {
    const page = await listMessages(claimId, 1, 50)
    messages.value = page.items
  } catch (error) {
    messageError.value = describeError(error)
  } finally {
    loadingMessages.value = false
  }
}

async function submitMessage() {
  const text = messageText.value.trim()
  if (!text || sending.value) return
  sending.value = true
  try {
    await sendMessage(claimId, text)
    messageText.value = ''
    await loadMessages()
  } catch (error) {
    ElMessage.error(describeError(error))
  } finally {
    sending.value = false
  }
}

async function loadPermission() {
  try {
    permission.value = await getContactPermission(claimId)
    permissionForm.value = {
      phoneVisible: permission.value.phoneVisible,
      emailVisible: permission.value.emailVisible,
    }
  } catch (error) {
    permissionError.value = describeError(error)
  }
}

async function savePermission() {
  if (!permission.value?.effective || savingPermission.value) return
  savingPermission.value = true
  permissionError.value = ''
  try {
    permission.value = await setContactPermission(
      claimId,
      permission.value.version,
      permissionForm.value.phoneVisible,
      permissionForm.value.emailVisible,
    )
    ElMessage.success('授权设置已保存')
    // 授权开关变化后，已展示的对方联系方式立即清除，避免继续显示失效数据
    counterparty.value = null
  } catch (error) {
    permissionError.value = describeError(error)
  } finally {
    savingPermission.value = false
  }
}

async function revealCounterparty() {
  if (loadingContact.value) return
  loadingContact.value = true
  contactError.value = ''
  counterparty.value = null
  try {
    counterparty.value = await getCounterpartyContact(claimId)
  } catch (error) {
    contactError.value = describeError(error)
  } finally {
    loadingContact.value = false
  }
}

async function act(action: 'confirm' | 'reject' | 'complete') {
  if (acting.value || !detail.value) return
  acting.value = true
  try {
    if (action === 'confirm') {
      await ElMessageBox.confirm(
        '确认后该信息下的其他认领申请将不可再被确认，确定吗？',
        '确认认领',
        { type: 'warning' },
      )
      await confirmClaim(claimId)
      ElMessage.success('已确认认领')
    } else if (action === 'reject') {
      const { value } = await ElMessageBox.prompt('请填写驳回原因（申请人可见）', '驳回认领', {
        inputValidator: (v: string) => (v && v.trim().length > 0 ? true : '驳回原因必填'),
      })
      await rejectClaim(claimId, value.trim())
      ElMessage.success('已驳回')
    } else {
      await ElMessageBox.confirm(
        '确认已完成线下交接？确认后申请变为「已完成」，物品归档并从列表消失，双方联系方式授权同时失效。',
        '确认交接完成',
        { type: 'warning' },
      )
      await completeClaim(claimId)
      ElMessage.success('交接完成，物品已归档')
    }
    counterparty.value = null
    await load()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(describeError(error))
  } finally {
    acting.value = false
  }
}

async function reportConversation() {
  if (!detail.value) return
  try {
    const { value } = await ElMessageBox.prompt(
      '请说明举报理由（仅举报人与管理员可见，最长 500 字）',
      '举报该认领会话',
      {
        inputValidator: (v: string) =>
          v && v.trim().length > 0 && v.trim().length <= 500
            ? true
            : '举报理由必填且不超过 500 字',
      },
    )
    await createReport({
      itemId: detail.value.item.id,
      claimId,
      reason: value.trim(),
    })
    ElMessage.success('举报已提交，管理员会通过处理；不会自动通知对方')
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(describeError(error))
  }
}

onMounted(load)
</script>

<template>
  <section class="claim-detail">
    <h1>认领详情</h1>

    <el-alert v-if="errorText" :title="errorText" type="warning" :closable="false" show-icon>
      <p v-if="!auth.isLoggedIn" class="hint">
        先打开 <code>/api/v1/dev/login?userId=1</code> 建立开发期会话，再刷新本页。
      </p>
    </el-alert>

    <el-skeleton v-else-if="loading" :rows="6" animated />

    <template v-else-if="detail">
      <el-card shadow="never" class="block">
        <template #header>
          <div class="card-head">
            <span>申请状态</span>
            <el-tag :type="detail.claim.status === 'CONFIRMED' ? 'success' : 'info'">
              {{ statusText[detail.claim.status] ?? detail.claim.status }}
            </el-tag>
          </div>
        </template>

        <el-descriptions :column="2" border>
          <el-descriptions-item label="申请人">{{ detail.claim.applicant.nickname }}</el-descriptions-item>
          <el-descriptions-item label="发布者">{{ detail.claim.publisher.nickname }}</el-descriptions-item>
          <el-descriptions-item label="关联物品">{{ detail.item.title }}</el-descriptions-item>
          <el-descriptions-item label="物品状态">{{ detail.item.status }}</el-descriptions-item>
          <el-descriptions-item label="认领凭证" :span="2">
            {{ detail.claim.description }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.claim.reason" label="驳回/关闭原因" :span="2">
            {{ detail.claim.reason }}
          </el-descriptions-item>
          <el-descriptions-item label="提交时间" :span="2">{{ formatTime(detail.claim.createdAt) }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- FR-19 留言：双方可聊，终态只读 -->
      <el-card v-if="isParticipant" shadow="never" class="block">
        <template #header>
          <div class="card-head">
            <span>会话留言</span>
            <el-tag v-if="canChat" type="success" size="small">可发送</el-tag>
            <el-tag v-else type="info" size="small">已只读</el-tag>
          </div>
        </template>

        <el-alert
          v-if="messageError"
          :title="messageError"
          type="warning"
          :closable="false"
          show-icon
        />
        <el-skeleton v-else-if="loadingMessages" :rows="3" animated />
        <el-empty v-else-if="messages.length === 0" description="还没有留言，打个招呼约定交接细节吧" />

        <ul v-else class="message-list">
          <li v-for="message in messages" :key="message.id" class="message-item">
            <div class="message-head">
              <span class="message-sender">{{ message.sender.nickname }}</span>
              <span class="message-time">{{ formatTime(message.createdAt) }}</span>
            </div>
            <p class="message-content">{{ message.content }}</p>
          </li>
        </ul>

        <div class="message-editor">
          <el-input
            v-model="messageText"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            :disabled="!canChat"
            placeholder="纯文本留言，1~1000 字；不要写手机号或邮箱，请在下方授权查看"
          />
          <div class="editor-foot">
            <span class="hint">申请提交后即可留言；交接完成或申请关闭后只保留历史。</span>
            <el-button type="primary" :loading="sending" :disabled="!canChat || !messageText.trim()" @click="submitMessage">
              发送留言
            </el-button>
          </div>
        </div>
      </el-card>

      <!-- FR-06 联系方式授权 -->
      <el-card v-if="isParticipant" shadow="never" class="block">
        <template #header>
          <div class="card-head">
            <span>联系方式授权</span>
            <el-tag :type="contactEffective ? 'success' : 'info'" size="small">
              {{ contactEffective ? '授权中' : '未生效' }}
            </el-tag>
          </div>
        </template>

        <el-alert
          v-if="!contactEffective"
          title="认领确认且物品仍在发布中时才能查看双方联系方式；交接完成或下架后授权自动失效。"
          type="info"
          :closable="false"
          show-icon
        />

        <template v-else>
          <el-alert v-if="permissionError" :title="permissionError" type="warning" :closable="false" show-icon />
          <el-form v-else label-width="120px" class="permission-form">
            <el-form-item label="授权对方查看">
              <el-checkbox v-model="permissionForm.phoneVisible">我的手机号</el-checkbox>
              <el-checkbox v-model="permissionForm.emailVisible">我的邮箱</el-checkbox>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="savingPermission" @click="savePermission">
                保存授权
              </el-button>
              <span class="hint">两个都不勾选即撤回；撤回只能阻止后续查看，不能收回对方已看到的内容。</span>
            </el-form-item>
          </el-form>

          <el-divider />

          <div class="contact-actions">
            <el-button :loading="loadingContact" @click="revealCounterparty">查看对方授权的联系方式</el-button>
            <span v-if="permission" class="hint">我的授权版本 v{{ permission.version }}</span>
          </div>
          <el-alert v-if="contactError" :title="contactError" type="warning" :closable="false" show-icon />
          <el-descriptions v-if="counterparty" :column="1" border class="contact-result">
            <el-descriptions-item v-if="counterparty.phone" label="对方手机号">
              {{ counterparty.phone }}
            </el-descriptions-item>
            <el-descriptions-item v-if="counterparty.email" label="对方邮箱">
              {{ counterparty.email }}
            </el-descriptions-item>
          </el-descriptions>
        </template>
      </el-card>

      <el-card v-if="isPublisher" shadow="never" class="block">
        <template #header>发布者操作</template>
        <div class="actions">
          <el-button
            v-if="detail.claim.status === 'PENDING'"
            type="primary"
            :loading="acting"
            @click="act('confirm')"
          >确认认领</el-button>
          <el-button
            v-if="detail.claim.status === 'PENDING'"
            :loading="acting"
            @click="act('reject')"
          >驳回申请</el-button>
          <el-button
            v-if="detail.claim.status === 'CONFIRMED'"
            type="success"
            :loading="acting"
            @click="act('complete')"
          >确认交接完成</el-button>
        </div>
        <p class="hint">仅当前信息的发布者可执行上述操作，后端会再次校验权限。</p>
      </el-card>

      <el-card v-else shadow="never" class="block">
        <template #header>申请人视角</template>
        <p>等待发布者处理。确认后可通过留言约定交接时间与地点，并按需授权联系方式。</p>
      </el-card>

      <!-- FR-27 举报入口：绑定当前认领会话 -->
      <div v-if="isParticipant" class="report-line">
        <el-button link type="danger" @click="reportConversation">举报此认领会话</el-button>
        <span class="hint">举报会同时告知管理员当前会话上下文，处理前管理员读取会话会被审计。</span>
      </div>
    </template>
  </section>
</template>

<style scoped>
.claim-detail h1 {
  margin: 0 0 16px;
  font-size: 22px;
}
.block {
  margin-bottom: 16px;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.actions {
  display: flex;
  gap: 10px;
}
.hint {
  margin: 0;
  color: var(--app-text-secondary, #666);
  font-size: 13px;
}
.message-list {
  list-style: none;
  margin: 0 0 16px;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.message-item {
  padding: 10px 12px;
  border: 1px solid var(--app-border, #ebeef5);
  border-radius: 8px;
  background: var(--app-surface, #fff);
}
.message-head {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--app-text-secondary, #666);
}
.message-sender {
  font-weight: 600;
  color: var(--app-text, #303133);
}
.message-content {
  margin: 6px 0 0;
  white-space: pre-wrap;
  word-break: break-word;
}
.message-editor {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.editor-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.permission-form :deep(.el-checkbox) {
  margin-right: 20px;
}
.contact-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.contact-result {
  margin-top: 12px;
}
.report-line {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 8px 0 24px;
}
</style>
