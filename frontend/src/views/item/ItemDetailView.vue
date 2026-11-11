<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { describeError } from '@/api/feedback'
import { getItem, type PublicItem } from '@/api/items'
import { createClaim } from '@/api/claim'
import { createClue } from '@/api/clues'
import { createReport } from '@/api/reports'
import { useAuthStore } from '@/stores/auth'
import { CATEGORY_LABELS, ITEM_TYPE_LABELS } from '@/types/domain'
import { textLength } from '@/utils/text'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const item = ref<PublicItem>()
const errorMessage = ref('')
const claimDescription = ref('')
const claimError = ref('')
const submittingClaim = ref(false)
const reporting = ref(false)
const clueContent = ref('')
async function submitClue() {
  if (!item.value || submittingClaim.value) return
  if (textLength(clueContent.value.trim()) < 10 || textLength(clueContent.value.trim()) > 500) {
    claimError.value = '请填写 10～500 字的线索'
    return
  }
  submittingClaim.value = true
  claimError.value = ''
  try {
    const clue = await createClue(item.value.id, clueContent.value.trim())
    await router.push({ name: 'clue-detail', params: { clueId: clue.id } })
  } catch (e) {
    claimError.value = describeError(e)
  } finally {
    submittingClaim.value = false
  }
}
const isOwner = computed(() => item.value?.publisher.id === auth.me?.id)
const canClaim = computed(
  () =>
    auth.isLoggedIn &&
    !auth.isAdmin &&
    !isOwner.value &&
    item.value?.type === 'FOUND' &&
    !item.value.hasConfirmedClaim,
)

async function submitClaim() {
  if (!item.value || !canClaim.value || submittingClaim.value) return
  const description = claimDescription.value.trim()
  if (textLength(description) < 10 || textLength(description) > 500) {
    claimError.value = '请填写 10～500 字的物品特征说明'
    return
  }
  submittingClaim.value = true
  claimError.value = ''
  try {
    const claim = await createClaim(item.value.id, description)
    await router.push({ name: 'claim-detail', params: { claimId: String(claim.id) } })
  } catch (error) {
    claimError.value = describeError(error)
  } finally {
    submittingClaim.value = false
  }
}

async function reportItem() {
  if (!item.value || reporting.value) return
  reporting.value = true
  try {
    const { value } = await ElMessageBox.prompt('请填写举报理由（1～500 字）', '举报物品信息', {
      inputValidator: (value: string) =>
        textLength(value?.trim() ?? '') >= 1 && textLength(value?.trim() ?? '') <= 500
          ? true
          : '请填写 1～500 字的举报理由',
    })
    await createReport({ itemId: item.value.id, reason: value.trim() })
    ElMessage.success('举报已提交，可在“我的举报”查看处理进度')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(describeError(error))
  } finally {
    reporting.value = false
  }
}
watch(
  () => route.params.itemId,
  async (id, _, onCleanup) => {
    let active = true
    onCleanup(() => {
      active = false
    })
    item.value = undefined
    errorMessage.value = ''
    claimDescription.value = ''
    claimError.value = ''
    try {
      const result = await getItem(String(id))
      if (active) item.value = result
    } catch (error) {
      if (active) errorMessage.value = describeError(error)
    }
  },
  { immediate: true },
)
</script>

<template>
  <section>
    <RouterLink :to="{ name: 'items' }">返回物品列表</RouterLink>
    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" />
    <article v-else-if="item">
      <h1>{{ item.title }}</h1>
      <dl>
        <dt>类型</dt>
        <dd>{{ ITEM_TYPE_LABELS[item.type] }}</dd>
        <dt>类别</dt>
        <dd>{{ CATEGORY_LABELS[item.category] }}</dd>
        <dt>发生地点</dt>
        <dd>{{ item.location }}</dd>
        <dt>发生日期</dt>
        <dd>{{ item.occurredDate }}</dd>
        <dt>发布人</dt>
        <dd>{{ item.publisher.nickname }}</dd>
      </dl>
      <h2>物品描述</h2>
      <p class="description">{{ item.description }}</p>
      <div class="images">
        <img
          v-for="(image, index) in item.images"
          :key="image.id"
          :src="image.url"
          :alt="`物品图片 ${index + 1}`"
        />
      </div>
      <section v-if="item.type === 'FOUND'" class="claim-section">
        <h2>申请认领</h2>
        <p v-if="isOwner">这是你发布的信息，可以在“我收到的认领”中处理其他人的申请。</p>
        <p v-else-if="item.hasConfirmedClaim">这条信息已确认认领人，不能继续申请。</p>
        <RouterLink
          v-else-if="!auth.isLoggedIn"
          :to="{ name: 'login', query: { redirect: route.fullPath } }"
        >
          登录后申请认领
        </RouterLink>
        <form v-else-if="canClaim" @submit.prevent="submitClaim">
          <label for="claim-description">物品特征说明（10～500 字，帮助发布人核实归属）</label>
          <el-input
            id="claim-description"
            v-model="claimDescription"
            type="textarea"
            :rows="3"
            :disabled="submittingClaim"
          />
          <el-alert v-if="claimError" :title="claimError" type="error" :closable="false" />
          <el-button native-type="submit" type="primary" :loading="submittingClaim"
            >提交认领申请</el-button
          >
        </form>
      </section>
      <el-button
        v-if="auth.isLoggedIn && !auth.isAdmin"
        class="report-button"
        type="danger"
        link
        :loading="reporting"
        @click="reportItem"
        >举报这条信息</el-button
      >
      <section v-if="item.type === 'LOST'" class="claim-section">
        <h2>提供线索</h2>
        <RouterLink v-if="isOwner" :to="{ name: 'my-clues' }">查看我收到的线索</RouterLink>
        <RouterLink
          v-else-if="!auth.isLoggedIn"
          :to="{ name: 'login', query: { redirect: route.fullPath } }"
          >登录后提供线索</RouterLink
        >
        <form v-else-if="!auth.isAdmin" @submit.prevent="submitClue">
          <label for="clue-content">线索（10～500 字，仅你和发布人可见）</label>
          <el-input
            id="clue-content"
            v-model="clueContent"
            type="textarea"
            :rows="3"
            :disabled="submittingClaim"
          />
          <el-alert v-if="claimError" :title="claimError" type="error" :closable="false" />
          <el-button native-type="submit" type="primary" :loading="submittingClaim"
            >提交线索</el-button
          >
        </form>
      </section>
    </article>
    <p v-else role="status">正在加载详情…</p>
  </section>
</template>

<style scoped>
dl {
  display: grid;
  grid-template-columns: max-content minmax(0, 1fr);
  gap: 8px 16px;
}
dd {
  margin: 0;
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
.claim-section {
  margin-top: 24px;
}
.claim-section form {
  display: grid;
  gap: 12px;
  max-width: 640px;
}
.claim-section form .el-button {
  justify-self: start;
}
.report-button {
  margin-top: 24px;
}
img {
  max-width: 100%;
  width: 320px;
  max-height: 320px;
  object-fit: contain;
}
</style>
