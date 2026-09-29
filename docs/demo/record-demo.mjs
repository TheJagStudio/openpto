import { chromium } from 'playwright';
import fs from 'node:fs';

const BASE = 'http://localhost:4200';
const SAMPLE = 'D:/JavaSpring/projects/openpto/ingest-service/samples/ipg250107-sample.xml';
const W = 1920, H = 1080;

const browser = await chromium.launch();
const ctx = await browser.newContext({ viewport: { width: W, height: H } });
const page = await ctx.newPage();
const wait = (ms) => page.waitForTimeout(ms);

// High-quality capture: CDP screencast frames (JPEG q100) with timestamps -> ffconcat list.
fs.rmSync('frames', { recursive: true, force: true }); fs.mkdirSync('frames');
const cdp = await ctx.newCDPSession(page);
const frames = [];
cdp.on('Page.screencastFrame', async ({ data, metadata, sessionId }) => {
  const f = `frames/${String(frames.length).padStart(5, '0')}.jpg`;
  fs.writeFileSync(f, Buffer.from(data, 'base64'));
  frames.push({ f, t: metadata.timestamp });
  await cdp.send('Page.screencastFrameAck', { sessionId }).catch(() => {});
});
await cdp.send('Page.startScreencast', { format: 'jpeg', quality: 100, maxWidth: W, maxHeight: H, everyNthFrame: 1 });

async function caption(title, sub = '') {
  await page.evaluate(([t, s]) => {
    let el = document.getElementById('__cap');
    if (!el) {
      el = document.createElement('div');
      el.id = '__cap';
      el.style.cssText = 'position:fixed;left:50%;bottom:28px;transform:translateX(-50%);z-index:99999;' +
        'background:rgba(15,23,42,.92);color:#fff;padding:12px 22px;border-radius:12px;font:600 20px system-ui,sans-serif;' +
        'box-shadow:0 8px 30px rgba(0,0,0,.35);text-align:center;pointer-events:none;max-width:80%';
      document.body.appendChild(el);
    }
    el.innerHTML = t + (s ? `<div style="font:400 14px system-ui;opacity:.8;margin-top:4px">${s}</div>` : '');
  }, [title, sub]);
}
async function go(path, title, sub) {
  await page.goto(BASE + path, { waitUntil: 'networkidle' }).catch(() => {});
  await wait(500);
  await caption(title, sub);
}
async function scroll(px, steps = 6, ms = 180) {
  for (let i = 0; i < steps; i++) { await page.mouse.wheel(0, px / steps); await wait(ms); }
}
async function step(name, fn) { try { await fn(); } catch (e) { console.log('skip', name, e.message.split('\n')[0]); } }

// 1. Landing
await step('landing', async () => {
  await go('/', 'OpenPTO — modern patent & trademark data portal', 'Spring Boot microservices · API gateway · Angular');
  await wait(2500); await scroll(700); await wait(1200);
});

// 2. Patent search
await step('patents', async () => {
  await go('/patents', 'Full-text patent search', 'PostgreSQL FTS with filters by CPC, inventor, assignee, dates');
  await page.fill('#patent-q', '');
  await page.type('#patent-q', 'battery', { delay: 70 });
  await page.keyboard.press('Enter');
  await wait(2200); await scroll(500); await wait(800);
  await page.locator('a[href^="/patents/"]').first().click();
  await page.waitForLoadState('networkidle'); await caption('Patent detail', 'Claims, inventors, classifications');
  await wait(1800); await scroll(600); await wait(900);
});

// 3. Trademarks
await step('trademarks', async () => {
  await go('/trademarks', 'Trademark search (TSDR equivalent)');
  await page.type('#tm-q', 'coffee', { delay: 70 });
  await page.keyboard.press('Enter');
  await wait(2000);
  await page.locator('a[href^="/trademarks/"]').first().click();
  await page.waitForLoadState('networkidle'); await caption('Trademark status & owner detail');
  await wait(2000);
});

// 4. Fees
await step('fees', async () => {
  await go('/fees', 'Fee calculator microservice', 'Entity size, excess claims, late fees — 515 tests');
  await wait(1200);
  const nums = page.locator('main input[type="number"]');
  const n = await nums.count();
  for (let i = 0; i < Math.min(n, 2); i++) { await nums.nth(i).fill(''); await nums.nth(i).type(String(i === 0 ? 25 : 4), { delay: 90 }); await wait(400); }
  const small = page.getByText(/small/i).first();
  if (await small.count()) await small.click().catch(() => {});
  await wait(1500); await scroll(500); await wait(1500);
});
await step('schedule', async () => {
  await go('/fees/schedule', 'Versioned fee schedules');
  await wait(1500); await scroll(500); await wait(900);
});

// 5. Login + pipeline
await step('login', async () => {
  await go('/login', 'Sign in — RS256 JWT auth');
  await page.type('#login-email', 'admin@openpto.local', { delay: 35 });
  await page.type('#login-password', 'Admin#12345', { delay: 35 });
  await page.locator('button[type="submit"]').click();
  await wait(1800);
});
await step('pipeline', async () => {
  await go('/pipeline', 'Legacy XML → JSON pipeline', 'Upload → bucket → ObjectCreated event → transform Lambda → DB');
  await page.setInputFiles('#ingest-files', SAMPLE);
  await wait(900);
  const up = page.getByRole('button', { name: /upload|ingest|start/i }).first();
  if (await up.count()) await up.click();
  await wait(3500);
  const job = page.locator('a[href*="/pipeline/jobs/"]').first();
  if (await job.count()) { await job.click(); await page.waitForLoadState('networkidle'); await caption('Ingest job — stage-by-stage status'); await wait(2800); }
});

// 6. Developers
await step('developers', async () => {
  await go('/developers', 'Developer portal', 'API keys, rate-limit tiers, code snippets, Swagger');
  await wait(1000);
  if (await page.locator('#key-name').count()) {
    await page.type('#key-name', 'demo-notebook', { delay: 60 });
    await page.locator('#key-name').press('Enter');
    await wait(1800);
  }
  await scroll(900, 8); await wait(1200); await scroll(900, 8); await wait(1500);
});

// 7. Status + admin
await step('status', async () => { await go('/status', 'Live system status', 'Gateway, services & circuit breakers'); await wait(2500); });
await step('admin', async () => { await go('/admin', 'Admin console', 'Users, keys and usage metering'); await wait(2200); await scroll(500); await wait(1000); });

// Outro
await step('outro', async () => {
  await go('/', 'OpenPTO', 'Runs locally · maps 1:1 to AWS (API Gateway, ECS, Aurora, S3, Lambda)');
  await wait(2500);
});

await cdp.send('Page.stopScreencast');
const end = Date.now() / 1000;
let list = 'ffconcat version 1.0\n';
frames.forEach((fr, i) => {
  const next = i + 1 < frames.length ? frames[i + 1].t : end;
  list += `file '${fr.f}'\nduration ${Math.max(next - fr.t, 0.001).toFixed(4)}\n`;
});
list += `file '${frames.at(-1).f}'\n`;
fs.writeFileSync('frames.txt', list);
console.log('frames', frames.length, 'seconds', (end - frames[0].t).toFixed(2));
await ctx.close();
await browser.close();
