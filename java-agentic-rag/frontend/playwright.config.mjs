import { defineConfig } from '@playwright/test';
export default defineConfig({testDir:'e2e',timeout:180000,expect:{timeout:15000},workers:1,
  webServer:process.env.UI_START_PREVIEW ? {command:'node scripts/preview.mjs',url:'http://127.0.0.1:4200/ui/index.html',timeout:30000,reuseExistingServer:false}:undefined,
  use:{baseURL:process.env.UI_BASE_URL || 'http://127.0.0.1:8081',browserName:'chromium',channel:process.env.UI_BROWSER_CHANNEL || undefined,trace:'off',screenshot:'off'},
  reporter:'list'});
