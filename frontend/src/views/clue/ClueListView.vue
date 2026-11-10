<script setup lang="ts">
import { ref, watch } from 'vue'
import { listClues, type Clue } from '@/api/clues'
import { describeError } from '@/api/feedback'
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
    <el-radio-group v-model="received">
      <el-radio-button :value="false">我提供的线索</el-radio-button>
      <el-radio-button :value="true">我收到的线索</el-radio-button>
    </el-radio-group>
    <el-button :loading="loading" @click="load">刷新</el-button>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-skeleton v-if="loading" :rows="4" animated />
    <el-empty v-else-if="!rows.length" description="暂无线索记录" />
    <el-table v-else :data="rows">
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
    <el-pagination
      v-if="total > 0"
      v-model:current-page="page"
      :page-size="10"
      :total="total"
      layout="prev, pager, next, total"
      :disabled="loading"
      @current-change="load"
    />
  </section>
</template>
