<script setup lang="ts">
import { computed } from 'vue'

import {
  CLAIM_STATUS_LABELS,
  ITEM_STATUS_LABELS,
  type ClaimStatus,
  type ItemStatus,
} from '@/types/domain'

// 物品状态或认领状态标签。只负责展示，不根据状态推断用户能做什么操作

type Status = ItemStatus | ClaimStatus
type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

const props = defineProps<{ status: Status }>()

const LABELS: Record<Status, string> = { ...ITEM_STATUS_LABELS, ...CLAIM_STATUS_LABELS }

const TAG_TYPES: Record<Status, TagType> = {
  PENDING_REVIEW: 'warning',
  RETURNED: 'danger',
  PUBLISHED: 'success',
  OFF_SHELF: 'info',
  ARCHIVED: 'info',
  PENDING: 'warning',
  CONFIRMED: 'primary',
  REJECTED: 'danger',
  COMPLETED: 'success',
  CLOSED: 'info',
}

const label = computed(() => LABELS[props.status])
const tagType = computed(() => TAG_TYPES[props.status])
</script>

<template>
  <el-tag :type="tagType" disable-transitions>{{ label }}</el-tag>
</template>
