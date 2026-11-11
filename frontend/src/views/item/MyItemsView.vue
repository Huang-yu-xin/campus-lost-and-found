<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { describeError } from '@/api/feedback'
import { listOwnItems, withdrawItem, type ItemRecord } from '@/api/items'
import { ITEM_STATUS_LABELS, type ItemStatus } from '@/types/domain'
import { resolveLostItem } from '@/api/clues'

const items = ref<ItemRecord[]>([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const busyId = ref('')
const errorMessage = ref('')
const statusFilter = ref<ItemStatus | ''>('')
async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await listOwnItems(page.value, statusFilter.value || undefined)
    items.value = result.items
    total.value = result.total
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    loading.value = false
  }
}
async function withdraw(item: ItemRecord) {
  if (busyId.value) return
  busyId.value = item.id
  try {
    await ElMessageBox.confirm(
      '下架后公开列表将不再显示，且不能再次编辑。确定下架吗？',
      '下架信息',
      {
        confirmButtonText: '确定下架',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
    await withdrawItem(item.id, item.version)
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') errorMessage.value = describeError(error)
  } finally {
    busyId.value = ''
  }
}
onMounted(load)
// 切换状态后回到第一页，避免停留在超出新结果范围的页码
watch(statusFilter, () => {
  page.value = 1
  void load()
})
async function resolve(item: ItemRecord) {
  if (busyId.value) return
  busyId.value = item.id
  try {
    await ElMessageBox.confirm('确认已找回？归档后停止接收线索和留言。', '标记已找回')
    await resolveLostItem(item.id, item.version)
    await load()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') errorMessage.value = describeError(e)
  } finally {
    busyId.value = ''
  }
}
</script>

<template>
  <section>
    <h1>我的发布</h1>
    <p>修改信息后需要重新审核。有待确认或已确认认领时不能修改。</p>
    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" />
    <div class="toolbar">
      <el-select
        v-model="statusFilter"
        placeholder="全部状态"
        clearable
        aria-label="按状态筛选"
        :disabled="loading || !!busyId"
        style="width: 180px"
      >
        <el-option
          v-for="(text, value) in ITEM_STATUS_LABELS"
          :key="value"
          :value="value"
          :label="text"
        />
      </el-select>
      <el-button :disabled="loading || !!busyId" @click="load">刷新列表</el-button>
    </div>
    <p v-if="loading" role="status">正在加载…</p>
    <p v-else-if="!items.length">
      {{ statusFilter ? `暂无${ITEM_STATUS_LABELS[statusFilter]}的发布记录。` : '暂无发布记录。' }}
    </p>
    <article v-for="item in items" :key="item.id">
      <h2>{{ item.title }}</h2>
      <p>编号 {{ item.id }} · {{ ITEM_STATUS_LABELS[item.status] }} · {{ item.occurredDate }}</p>
      <p>{{ item.description }}</p>
      <RouterLink v-if="item.type === 'LOST'" :to="{ name: 'my-clues' }">查看线索记录</RouterLink>
      <el-button
        v-if="item.type === 'LOST' && item.status === 'PUBLISHED'"
        :disabled="!!busyId"
        @click="resolve(item)"
        >标记已找回</el-button
      >
      <div class="images">
        <img v-for="image in item.images" :key="image.id" :src="image.url" alt="物品图片" />
      </div>
      <el-alert
        v-if="item.reviewReason"
        :title="`审核反馈：${item.reviewReason}`"
        type="warning"
        :closable="false"
      />
      <div v-if="['PENDING_REVIEW', 'RETURNED', 'PUBLISHED'].includes(item.status)" class="actions">
        <RouterLink :to="{ name: 'item-edit', params: { itemId: item.id } }"
          >修改并重新提交审核</RouterLink
        >
        <el-button
          type="danger"
          plain
          :disabled="!!busyId || loading"
          :loading="busyId === item.id"
          @click="withdraw(item)"
          >下架</el-button
        >
      </div>
    </article>
    <el-pagination
      v-if="total > 10"
      v-model:current-page="page"
      :page-size="10"
      :total="total"
      :pager-count="5"
      layout="prev, pager, next"
      :disabled="loading || !!busyId"
      @current-change="load"
    />
  </section>
</template>

<style scoped>
article {
  padding: 16px 0;
  border-bottom: 1px solid var(--app-border);
  overflow-wrap: anywhere;
}
article p {
  white-space: pre-wrap;
}
h2 {
  font-size: 18px;
}
.toolbar,
.actions,
.images {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
  margin-top: 12px;
}
img {
  width: 140px;
  height: 110px;
  object-fit: contain;
}
</style>
