<template>
  <main id="main-content" class="container" tabindex="-1" aria-labelledby="missing-page-title">
    <el-result icon="warning" sub-title="链接可能有误，或内容已不再公开">
      <template #title><h1 id="missing-page-title">页面不存在</h1></template>
      <template #extra>
        <RouterLink to="/" class="el-button el-button--primary">返回首页</RouterLink>
      </template>
    </el-result>
  </main>
</template>
