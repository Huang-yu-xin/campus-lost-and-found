<template>
  <el-result icon="warning" title="页面不存在" sub-title="链接可能有误，或内容已不再公开">
    <template #extra>
      <RouterLink to="/">
        <el-button type="primary">返回首页</el-button>
      </RouterLink>
    </template>
  </el-result>
</template>
