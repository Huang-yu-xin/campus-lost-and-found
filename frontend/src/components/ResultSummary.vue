<script setup lang="ts">
defineProps<{
  loading: boolean
  failed?: boolean
  total: number
  page: number
  name: string
}>()
</script>

<template>
  <p class="visually-hidden" role="status">
    <template v-if="loading">正在加载{{ name }}</template>
    <template v-else-if="!failed">
      {{ total ? `共 ${total} 条${name}，当前第 ${page} 页` : `暂无${name}` }}
    </template>
  </p>
</template>
