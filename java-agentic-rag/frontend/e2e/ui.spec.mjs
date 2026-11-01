import { test, expect } from '@playwright/test';

async function connect(page) {
  await page.goto('/ui/index.html');
  await page.getByRole('button',{name:'Connect workspace',exact:true}).click();
  await page.getByLabel('Access token').fill('synthetic-browser-test-token');
  await page.getByRole('button',{name:'Connect',exact:true}).click();
  await expect(page.getByRole('button',{name:'Workspace connected'})).toBeVisible();
}
test('streamed evidence, literal untrusted text, feedback and disconnect',async({page})=>{
  const errors=[]; page.on('pageerror',error=>errors.push(error.message));
  await page.route('**/api/documents?*',route=>route.fulfill({json:[]}));
  const evidence={chunkId:'chunk-1',sourceId:'source-1',title:'<img src=x onerror=alert(1)>',text:'Orion acknowledgment is 17 minutes.',score:.82};
  await page.route('**/query',route=>route.fulfill({contentType:'text/event-stream',body:
    `event: sources\ndata: ${JSON.stringify([evidence])}\n\nevent: token\ndata: 17 minutes.\n\nevent: done\ndata: ${JSON.stringify({queryId:'query-1',answer:'17 minutes.',sources:[evidence],latencyMs:150,cached:false,toolTrace:[]})}\n\n`}));
  await page.route('**/feedback',async route=>{expect(route.request().postDataJSON()).toMatchObject({queryId:'query-1',rating:5});await route.fulfill({status:201,json:{id:'feedback-1'}});});
  await connect(page);
  await page.getByLabel('Your question').fill('How long to acknowledge Orion?');
  await page.getByRole('button',{name:'Ask question ↗'}).click();
  await expect(page.locator('.answer-text').last()).toHaveText('17 minutes.');
  await expect(page.locator('.evidence-card summary')).toContainText('<img');
  expect(await page.locator('.evidence-card img').count()).toBe(0);
  await page.getByRole('button',{name:'Send feedback'}).click();
  await expect(page.getByRole('button',{name:'Feedback saved'})).toBeVisible();
  await page.getByRole('button',{name:'Workspace connected'}).click();
  await page.getByRole('button',{name:'Disconnect',exact:true}).click();
  await expect(page.locator('.message')).toHaveCount(0);
  expect(errors).toEqual([]);
});
test('invalid token is actionable and mobile layout fits',async({page})=>{
  await page.setViewportSize({width:390,height:844});
  await page.route('**/api/documents?*',route=>route.fulfill({status:401}));
  await page.goto('/ui/index.html');
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  await page.getByRole('button',{name:'Connect workspace',exact:true}).click();
  await page.getByLabel('Access token').fill('invalid');
  await page.getByRole('button',{name:'Connect',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('invalid or expired');
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
});
test('truncated stream reports incomplete answer and restores controls',async({page})=>{
  await page.route('**/api/documents?*',route=>route.fulfill({json:[]}));
  await page.route('**/query',route=>route.fulfill({contentType:'text/event-stream',body:'event: token\ndata: Partial answer\n\n'}));
  await connect(page);
  await page.getByLabel('Your question').fill('A question');
  await page.getByRole('button',{name:'Ask question ↗'}).click();
  await expect(page.locator('.message-meta').last()).toContainText('ended before');
  await expect(page.getByRole('button',{name:'Stop',exact:true})).toHaveCount(0);
  await expect(page.getByRole('button',{name:'Send feedback'})).toHaveCount(0);
});
test('failed document submission keeps draft and reuses the idempotency key',async({page})=>{
  const keys=[];
  await page.route('**/api/documents?*',route=>route.fulfill({json:[]}));
  await page.route('**/api/documents',async route=>{
    keys.push(route.request().headers()['idempotency-key']);
    expect(route.request().postDataJSON().text).toBe('Synthetic document content.');
    await route.fulfill(keys.length===1?{status:503}:{status:202,json:{id:'new-id',status:'QUEUED'}});
  });
  await connect(page);
  await page.getByRole('button',{name:'Documents',exact:true}).click();
  await page.getByRole('button',{name:'+ Add document',exact:true}).click();
  await page.getByLabel('Title',{exact:true}).fill('Synthetic upload');
  await page.getByLabel('Import text or Markdown (optional)').setInputFiles({name:'sample.md',mimeType:'text/markdown',buffer:Buffer.from('Synthetic document content.')});
  await page.getByRole('button',{name:'Save and index'}).click();
  await expect(page.getByRole('alert')).toContainText('503');
  await expect(page.getByLabel('Content',{exact:true})).toHaveValue('Synthetic document content.');
  await page.getByRole('button',{name:'Save and index'}).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(keys).toHaveLength(2); expect(keys[0]).toBeTruthy(); expect(keys[1]).toBe(keys[0]);
});
test('pending replacement is visible and cannot be replaced a second time',async({page})=>{
  await page.route('**/api/documents?*',route=>route.fulfill({json:[{id:'new',title:'Runbook pending',kind:'runbooks',status:'QUEUED',activeSourceId:'old',pendingSourceId:'new'}]}));
  await connect(page);
  await page.getByRole('button',{name:'Documents',exact:true}).click();
  await expect(page.getByRole('button',{name:'Replace',exact:true})).toBeDisabled();
  await expect(page.locator('.document-row')).toContainText('Previous version remains searchable');
});
