<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
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
import { restoreFocus } from '@/utils/restoreFocus'
import { askForText } from '@/composables/useTextPrompt'
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
const messageError = ref('')
const messageStatus = ref('')
const loadingMessages = ref(false)
const busy = ref(false)
const id = computed(() => String(route.params.clueId))
const writable = computed(() => data.value?.item.status === 'PUBLISHED')
const owner = computed(() => data.value?.clue.publisher.id === auth.me?.id)
async function loadMessages() {
  messageStatus.value = ''
  loadingMessages.value = true
  try {
    const result = await clueMessages(id.value, page.value)
    messages.value = result.items
    total.value = result.total
  } catch (e) {
    error.value = describeError(e)
  } finally {
    loadingMessages.value = false
  }
}
async function load() {
  error.value = ''
  messageError.value = ''
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
    messageError.value = '留言需为 1～1000 字'
    await nextTick()
    document.getElementById('clue-message')?.focus()
    return
  }
  busy.value = true
  error.value = ''
  messageError.value = ''
  const previous = document.activeElement
  try {
    await sendClueMessage(id.value, content.value.trim())
    content.value = ''
    const latest = await clueMessages(id.value, 1)
    page.value = Math.max(1, Math.ceil(latest.total / 10))
    await loadMessages()
    messageStatus.value = '留言已发送'
  } catch (e) {
    await load()
    error.value = describeError(e)
  } finally {
    busy.value = false
    await restoreFocus(previous)
  }
}
async function resolve() {
  if (!data.value || busy.value) return
  const previous = document.activeElement
  try {
    await ElMessageBox.confirm('确认物品已找回？归档后线索和留言将只读。', '标记已找回', {
      autofocus: false,
    })
    busy.value = true
    data.value.item = await resolveLostItem(data.value.item.id, data.value.item.version)
    ElMessage.success('已标记找回')
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') error.value = describeError(e)
  } finally {
    busy.value = false
    await restoreFocus(previous)
  }
}
async function report() {
  if (!data.value || busy.value) return
  const previous = document.activeElement
  try {
    const { value } = await askForText('请填写举报理由（1～500 字）', '举报此线索会话', {
      inputValidator: (v: string) =>
        (textLength(v?.trim() ?? '') >= 1 && textLength(v?.trim() ?? '') <= 500) ||
        '请填写 1～500 字',
    })
    busy.value = true
    await createReport({ itemId: data.value.item.id, clueId: id.value, reason: value.trim() })
    ElMessage.success('举报已提交')
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') error.value = describeError(e)
  } finally {
    busy.value = false
    await restoreFocus(previous)
  }
}
watch(id, load, { immediate: true })
</script>
<template>
  <section>
    <RouterLink :to="{ name: 'my-clues' }">返回我的线索</RouterLink>
    <h1>{{ data?.item.title ?? '线索详情' }}</h1>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template v-if="data">
      <p>{{ data.item.description }}</p>
      <img
        v-for="(image, index) in data.item.images"
        :key="image.id"
        :src="image.url"
        :alt="`${data.item.title}，图片 ${index + 1}`"
      />
      <h2>线索内容</h2>
      <p class="text">{{ data.clue.provider.nickname }}：{{ data.clue.content }}</p>
      <p>仅线索提供者与发布人可见。请勿在留言中泄露个人敏感信息。</p>
      <el-button v-if="owner && writable" :disabled="busy" type="success" @click="resolve"
        >标记已找回</el-button
      >
      <el-button :disabled="busy" type="danger" link @click="report">举报此会话</el-button>
      <h2>双方留言</h2>
      <el-button :disabled="busy" @click="loadMessages">刷新留言</el-button>
      <p class="visually-hidden" role="status">
        {{
          loadingMessages
            ? '正在加载留言'
            : messageStatus || (total ? `共 ${total} 条留言，当前第 ${page} 页` : '暂无留言')
        }}
      </p>
      <el-empty v-if="!messages.length" description="暂无留言" />
      <article v-for="message in messages" :key="message.id">
        <strong>{{ message.sender.nickname }}</strong> ·
        {{ new Date(message.createdAt).toLocaleString('zh-CN') }}
        <p class="text">{{ message.content }}</p>
      </article>
      <el-pagination
        role="navigation"
        aria-label="线索会话留言分页"
        v-if="total > 0"
        v-model:current-page="page"
        :page-size="10"
        :total="total"
        :pager-count="5"
        layout="prev, pager, next, total"
        @current-change="loadMessages"
      />
      <form v-if="writable" @submit.prevent="send">
        <label for="clue-message">会话留言（1～1000 字）</label>
        <el-input
          id="clue-message"
          v-model="content"
          type="textarea"
          :rows="3"
          :disabled="busy"
          aria-required="true"
          :aria-invalid="!!messageError || undefined"
          :aria-describedby="messageError ? 'clue-message-error' : undefined"
          @input="messageError = ''"
        />
        <el-alert
          v-if="messageError"
          id="clue-message-error"
          :title="messageError"
          type="error"
          :closable="false"
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
