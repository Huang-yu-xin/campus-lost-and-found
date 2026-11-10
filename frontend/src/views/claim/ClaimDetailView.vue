<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { describeError } from '@/api/feedback'
import { completeClaim, confirmClaim, getClaim, rejectClaim } from '@/api/claim'
import type { ClaimDetail } from '@/api/claim'
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
  () => !!detail.value && !!auth.me && detail.value.claim.publisher.id === auth.me.id,
)

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    detail.value = await getClaim(claimId)
  } catch (error) {
    errorText.value = describeError(error)
  } finally {
    loading.value = false
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
        '确认已完成线下交接？确认后申请变为「已完成」，物品归档并从列表消失。',
        '确认交接完成',
        { type: 'warning' },
      )
      await completeClaim(claimId)
      ElMessage.success('交接完成，物品已归档')
    }
    await load()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(describeError(error))
  } finally {
    acting.value = false
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
          <el-descriptions-item label="提交时间" :span="2">{{ detail.claim.createdAt }}</el-descriptions-item>
        </el-descriptions>
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
        <p>等待发布者处理。已被确认后可通过站内留言约定交接时间与地点。</p>
      </el-card>
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
  margin: 12px 0 0;
  color: var(--app-text-secondary, #666);
  font-size: 13px;
}
</style>
