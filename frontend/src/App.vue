<script setup lang="ts">
import { watch } from 'vue'
import { useRoute } from 'vue-router'
import TextPromptDialog from '@/components/TextPromptDialog.vue'

const route = useRoute()
const siteName = '校园失物招领系统'

function focusMain() {
  document.getElementById('main-content')?.focus()
}

watch(
  () => [route.path, route.meta.title] as const,
  ([path, title], previous) => {
    document.title = title && title !== siteName ? `${title} · ${siteName}` : siteName
    if (!previous?.[1] || path === previous[0]) return
    window.scrollTo(0, 0)
    focusMain()
  },
  { flush: 'post', immediate: true },
)
</script>

<template>
  <a class="skip-link" href="#main-content" @click.prevent="focusMain">跳到主要内容</a>
  <RouterView />
  <TextPromptDialog />
</template>
