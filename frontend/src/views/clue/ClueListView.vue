<script setup lang="ts">
import { ref, watch } from 'vue'
import { listClues, type Clue } from '@/api/clues'
import { describeError } from '@/api/feedback'
import { formatTime } from '@/utils/time'
const received = ref(false)
const page = ref(1)
const total = ref(0)
const rows = ref<Clue[]>([])
const loading = ref(false)
const error = ref('')
let request = 0
async function load() {
  const current = ++request
  loading.value = true
  error.value = ''
  try {
    const result = await listClues(received.value, page.value)
    if (current === request) {
      rows.value = result.items
      total.value = result.total
    }
  } catch (e) {
    if (current === request) error.value = describeError(e)
  } finally {
    if (current === request) loading.value = false
  }
}
watch(
  received,
  () => {
    page.value = 1
    void load()
  },
  { immediate: true },
)
</script>
<template>
  <section>
    <h1>我的线索</h1>
    <div class="clue-tools">
      <el-radio-group v-model="received" aria-label="线索方向">
        <el-radio-button :value="false">我提供的线索</el-radio-button>
        <el-radio-button :value="true">我收到的线索</el-radio-button>
      </el-radio-group>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-skeleton v-if="loading" :rows="4" animated />
    <el-empty v-else-if="!rows.length" description="暂无线索记录" />
    <template v-else>
      <div class="mobile-records" aria-label="线索记录">
        <article v-for="row in rows" :key="row.id" class="record-card">
          <header>
            <h2>寻物 #{{ row.lostItemId }}</h2>
          </header>
          <dl>
            <dt>{{ received ? '提供者' : '发布人' }}</dt>
            <dd>{{ received ? row.provider.nickname : row.publisher.nickname }}</dd>
            <dt>线索内容</dt>
            <dd>{{ row.content }}</dd>
            <dt>提交时间</dt>
            <dd>{{ formatTime(row.createdAt) }}</dd>
          </dl>
          <RouterLink :to="{ name: 'clue-detail', params: { clueId: row.id } }" class="record-link"
            >查看 / 留言</RouterLink
          >
        </article>
      </div>
      <el-table class="desktop-records" :data="rows">
        <el-table-column prop="lostItemId" label="寻物编号" />
        <el-table-column
          :prop="received ? 'provider.nickname' : 'publisher.nickname'"
          :label="received ? '提供者' : '发布人'"
        />
        <el-table-column prop="content" label="线索内容" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作"
          ><template #default="{ row }"
            ><RouterLink :to="{ name: 'clue-detail', params: { clueId: row.id } }"
              >查看 / 留言</RouterLink
            ></template
          ></el-table-column
        >
      </el-table>
    </template>
    <el-pagination
      v-if="total > 0"
      v-model:current-page="page"
      :page-size="10"
      :total="total"
      :pager-count="5"
      layout="prev, pager, next, total"
      :disabled="loading"
      @current-change="load"
    />
  </section>
</template>

<style scoped>
.clue-tools {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
}
</style>
