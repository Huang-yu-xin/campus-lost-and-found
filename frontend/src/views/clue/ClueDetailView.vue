<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import {
  getClue,
  clueMessages,
  sendClueMessage,
  resolveLostItem,
  type ClueDetail,
  type ClueMessage,
} from '@/api/clues'
import { createReport } from '@/api/reports'
import { describeError } from '@/api/feedback'
import { useAuthStore } from '@/stores/auth'
import { textLength } from '@/utils/text'
const route = useRoute()
const auth = useAuthStore()
const data = ref<ClueDetail>()
const messages = ref<ClueMessage[]>([])
const page = ref(1)
const total = ref(0)
const content = ref('')
const error = ref('')
const busy = ref(false)
const id = computed(() => String(route.params.clueId))
const writable = computed(() => data.value?.item.status === 'PUBLISHED')
const owner = computed(() => data.value?.clue.publisher.id === auth.me?.id)
async function loadMessages() {
  try {
    const result = await clueMessages(id.value, page.value)
    messages.value = result.items
    total.value = result.total
  } catch (e) {
    error.value = describeError(e)
  }
}
async function load() {
  error.value = ''
  data.value = undefined
  messages.value = []
  page.value = 1
  try {
    data.value = await getClue(id.value)
    await loadMessages()
  } catch (e) {
    error.value = describeError(e)
  }
}
async function send() {
  if (busy.value || !writable.value) return
  if (textLength(content.value.trim()) < 1 || textLength(content.value.trim()) > 1000) {
    error.value = '留言需为 1～1000 字'
    return
  }
  busy.value = true
  error.value = ''
  try {
    await sendClueMessage(id.value, content.value.trim())
    content.value = ''
    const latest = await clueMessages(id.value, 1)
    page.value = Math.max(1, Math.ceil(latest.total / 10))
    await loadMessages()
  } catch (e) {
    await load()
    error.value = describeError(e)
  } finally {
    busy.value = false
  }
}
async function resolve() {
  if (!data.value || busy.value) return
  busy.value = true
  try {
    await ElMessageBox.confirm('确认物品已找回？归档后线索和留言将只读。', '标记已找回')
    data.value.item = await resolveLostItem(data.value.item.id, data.value.item.version)
    ElMessage.success('已标记找回')
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') error.value = describeError(e)
  } finally {
    busy.value = false
  }
}
async function report() {
  if (!data.value || busy.value) return
  busy.value = true
  try {
    const { value } = await ElMessageBox.prompt('请填写举报理由（1～500 字）', '举报此线索会话', {
      inputValidator: (v: string) =>
        (textLength(v?.trim() ?? '') >= 1 && textLength(v?.trim() ?? '') <= 500) ||
        '请填写 1～500 字',
    })
    await createReport({ itemId: data.value.item.id, clueId: id.value, reason: value.trim() })
    ElMessage.success('举报已提交')
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') error.value = describeError(e)
  } finally {
    busy.value = false
  }
}
watch(id, load, { immediate: true })
</script>
<template>
  <section>
    <RouterLink :to="{ name: 'my-clues' }">返回我的线索</RouterLink>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template v-if="data">
      <h1>{{ data.item.title }}</h1>
      <p>{{ data.item.description }}</p>
      <img v-for="image in data.item.images" :key="image.id" :src="image.url" alt="物品图片" />
      <h2>线索内容</h2>
      <p class="text">{{ data.clue.provider.nickname }}：{{ data.clue.content }}</p>
      <p>仅线索提供者与发布人可见。请勿在留言中泄露个人敏感信息。</p>
      <el-button v-if="owner && writable" :disabled="busy" type="success" @click="resolve"
        >标记已找回</el-button
      >
      <el-button :disabled="busy" type="danger" link @click="report">举报此会话</el-button>
      <h2>双方留言</h2>
      <el-button :disabled="busy" @click="loadMessages">刷新留言</el-button>
      <el-empty v-if="!messages.length" description="暂无留言" />
      <article v-for="message in messages" :key="message.id">
        <strong>{{ message.sender.nickname }}</strong> ·
        {{ new Date(message.createdAt).toLocaleString('zh-CN') }}
        <p class="text">{{ message.content }}</p>
      </article>
      <el-pagination
        v-if="total > 0"
        v-model:current-page="page"
        :page-size="10"
        :total="total"
        :pager-count="5"
        layout="prev, pager, next, total"
        @current-change="loadMessages"
      />
      <form v-if="writable" @submit.prevent="send">
        <el-input
          v-model="content"
          type="textarea"
          :rows="3"
          :disabled="busy"
          placeholder="留言（1～1000 字）"
        />
        <el-button native-type="submit" :loading="busy" type="primary">发送留言</el-button>
      </form>
      <p v-else>物品已结束处理，线索和留言仅供查看。</p>
    </template>
  </section>
</template>
<style scoped>
.text {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
article {
  border-bottom: 1px solid var(--app-border);
  padding: 12px 0;
}
form {
  display: grid;
  gap: 12px;
  margin-top: 16px;
  max-width: 640px;
}
img {
  max-width: 100%;
  width: 240px;
  max-height: 240px;
  object-fit: contain;
}
</style>
