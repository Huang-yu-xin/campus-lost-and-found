<script setup lang="ts">
import { reactive, ref } from 'vue'

import { describeError } from '@/api/feedback'
import { createItem, type CreateItemRequest } from '@/api/items'
import { CATEGORY_LABELS, ITEM_TYPE_LABELS } from '@/types/domain'
import { textLength } from '@/utils/text'

const initialForm: CreateItemRequest = {
  type: 'LOST',
  category: 'DOCUMENT',
  title: '',
  location: '',
  occurredDate: '',
  description: '',
}
const form = reactive({ ...initialForm })
const submitting = ref(false)
const publishedId = ref('')
const errorMessage = ref('')

async function submit() {
  if (submitting.value || publishedId.value) return
  errorMessage.value = ''

  if (textLength(form.title) < 1 || textLength(form.title) > 50) {
    errorMessage.value = '标题需填写 1～50 字'
    return
  }
  if (textLength(form.location) < 1 || textLength(form.location) > 50) {
    errorMessage.value = '地点需填写 1～50 字'
    return
  }
  if (textLength(form.description) < 10 || textLength(form.description) > 500) {
    errorMessage.value = '描述需填写 10～500 字'
    return
  }

  submitting.value = true
  try {
    const item = await createItem({ ...form })
    publishedId.value = item.id
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    submitting.value = false
  }
}

function startNewItem() {
  Object.assign(form, initialForm)
  publishedId.value = ''
}
</script>

<template>
  <section class="publish-page">
    <h1>发布物品信息</h1>
    <el-alert v-if="publishedId" title="已提交，等待审核" type="success" :closable="false">
      <p>物品编号 {{ publishedId }}。审核通过后，访客可以在物品列表中查看。</p>
      <el-button @click="startNewItem">发布另一条</el-button>
    </el-alert>
    <form v-else @submit.prevent="submit">
      <p>以下各项均为必填。公开内容请勿填写手机号、邮箱或社交账号。</p>
      <label>
        信息类型
        <el-select v-model="form.type" aria-label="信息类型">
          <el-option
            v-for="(label, value) in ITEM_TYPE_LABELS"
            :key="value"
            :label="label"
            :value="value"
          />
        </el-select>
      </label>
      <label>
        物品类别
        <el-select v-model="form.category" aria-label="物品类别">
          <el-option
            v-for="(label, value) in CATEGORY_LABELS"
            :key="value"
            :label="label"
            :value="value"
          />
        </el-select>
      </label>
      <label for="item-title">标题（1～50 字）</label>
      <el-input id="item-title" v-model="form.title" />
      <label for="item-location">发生地点（1～50 字）</label>
      <el-input id="item-location" v-model="form.location" />
      <label for="item-date">发生日期</label>
      <input id="item-date" v-model="form.occurredDate" type="date" required />
      <label for="item-description">物品描述（10～500 字）</label>
      <el-input id="item-description" v-model="form.description" type="textarea" :rows="5" />
      <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" />
      <el-button native-type="submit" type="primary" :loading="submitting" :disabled="submitting">
        提交审核
      </el-button>
    </form>
  </section>
</template>

<style scoped>
.publish-page {
  max-width: 640px;
}
form {
  display: grid;
  gap: 12px;
}
form label {
  font-weight: 600;
}
form label .el-select {
  display: block;
  margin-top: 8px;
}
form .el-button {
  justify-self: start;
}
input[type='date'] {
  min-height: 32px;
  padding: 4px 8px;
  font: inherit;
}
</style>
