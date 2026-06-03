<template>
  <router-view />
</template>

<style>
* { box-sizing: border-box; margin: 0; padding: 0; }

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
               'Noto Sans SC', 'Microsoft YaHei', sans-serif;
  background: #f5f7fa;
  color: #1d2129;
  font-size: 14px;
  line-height: 1.6;
  -webkit-font-smoothing: antialiased;
}

/* 覆盖 Element Plus 主色 */
:root {
  --el-color-primary:          #1677ff;
  --el-color-primary-light-3:  #40a9ff;
  --el-color-primary-light-5:  #69c0ff;
  --el-color-primary-light-7:  #bae0ff;
  --el-color-primary-light-9:  #e6f4ff;
  --el-color-primary-dark-2:   #0958d9;
}
</style>
