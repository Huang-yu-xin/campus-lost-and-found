<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'

import { describeError } from '@/api/feedback'
import { listItems, type ItemFilters, type PublicItem } from '@/api/items'
import { CATEGORY_LABELS, ITEM_TYPE_LABELS } from '@/types/domain'

const filters = reactive<ItemFilters>({
  keyword: '',
  type: '',
  category: '',
  location: '',
  dateFrom: '',
  dateTo: '',
})
let appliedFilters: ItemFilters = {}
const items = ref<PublicItem[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const errorMessage = ref('')

async function load(nextPage: number) {
  if (loading.value) return
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await listItems(nextPage, appliedFilters)
    items.value = result.items
    total.value = result.total
    page.value = result.page
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    loading.value = false
  }
}

function search() {
  if (loading.value) return
  appliedFilters = { ...filters }
  void load(1)
}

onMounted(() => load(1))
</script>

<template>
  <section>
    <h1>浏览物品</h1>
    <form class="filters" @submit.prevent="search">
      <label
        >关键词
        <el-input v-model="filters.keyword" aria-label="关键词" placeholder="搜索标题或描述"
      /></label>
      <label>
        类型
        <el-select v-model="filters.type" aria-label="类型">
          <el-option label="全部" value="" />
          <el-option
            v-for="(label, value) in ITEM_TYPE_LABELS"
            :key="value"
            :label="label"
            :value="value"
          />
        </el-select>
      </label>
      <label>
        类别
        <el-select v-model="filters.category" aria-label="类别">
          <el-option label="全部" value="" />
          <el-option
            v-for="(label, value) in CATEGORY_LABELS"
            :key="value"
            :label="label"
            :value="value"
          />
        </el-select>
      </label>
      <label>地点 <el-input v-model="filters.location" aria-label="地点" /></label>
      <label>起始日期 <input v-model="filters.dateFrom" type="date" /></label>
      <label>结束日期 <input v-model="filters.dateTo" type="date" /></label>
      <el-button native-type="submit" type="primary" :loading="loading">搜索</el-button>
    </form>
    <p v-if="loading" role="status">正在加载物品…</p>
    <el-alert v-else-if="errorMessage" :title="errorMessage" type="error" :closable="false" />
    <el-empty v-else-if="items.length === 0" description="没有符合条件的公开物品" />
    <template v-else>
      <ul class="items">
        <li v-for="item in items" :key="item.id">
          <RouterLink :to="{ name: 'item-detail', params: { itemId: item.id } }">{{
            item.title
          }}</RouterLink>
          <p>
            {{ ITEM_TYPE_LABELS[item.type] }} · {{ CATEGORY_LABELS[item.category] }} ·
            {{ item.location }} · {{ item.occurredDate }}
          </p>
        </li>
      </ul>
      <el-pagination
        v-if="total > 10"
        :current-page="page"
        :total="total"
        :page-size="10"
        :pager-count="5"
        layout="prev, pager, next"
        @current-change="load"
      />
    </template>
  </section>
</template>

<style scoped>
.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: 12px;
}
.filters label {
  display: grid;
  gap: 4px;
  width: min(100%, 160px);
}
.filters input[type='date'] {
  min-height: 32px;
  font: inherit;
}
.items {
  padding: 0;
  list-style: none;
}
.items li {
  padding: 12px 0;
  border-bottom: 1px solid var(--app-border);
}
.items li p {
  margin-bottom: 0;
  color: var(--app-text-secondary);
}

@media (max-width: 640px) {
  .filters {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .filters label {
    width: 100%;
    min-width: 0;
  }

  .filters input[type='date'] {
    width: 100%;
    min-width: 0;
    min-height: 44px;
  }

  .filters > .el-button {
    grid-column: 1 / -1;
  }
}

@media (max-width: 400px) {
  .filters {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
