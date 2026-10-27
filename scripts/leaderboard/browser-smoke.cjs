'use strict';

const fs = require('node:fs/promises');
const path = require('node:path');
const { chromium } = require('playwright');

const base = process.env.OBSERVATORY_BASE_URL || 'http://127.0.0.1:8090';
const artifactDir = path.resolve(process.env.OBSERVATORY_SMOKE_ARTIFACTS || 'build/browser-smoke');
const viewports = [
  [320, 700], [360, 800], [390, 844], [430, 932], [768, 1024],
  [1024, 768], [1440, 900], [1536, 816], [1920, 1080]
];
const routes = [
  '/overview', '/regions/ap', '/countries', '/leaderboard', '/trends',
  '/archive', '/compare', '/watchlist', '/settings'
];

async function main() {
  await fs.mkdir(artifactDir, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ reducedMotion: 'reduce' });
  const pageErrors = [];
  const serverErrors = [];
  page.on('pageerror', error => pageErrors.push(error.message));
  page.on('response', response => {
    if (response.status() >= 500) serverErrors.push(`${response.status()} ${response.url()}`);
  });
  try {
    for (const [width, height] of viewports) {
      await page.setViewportSize({ width, height });
      for (const route of routes) {
        const response = await page.goto(new URL(route, base).toString(), { waitUntil: 'domcontentloaded' });
        if (!response || response.status() >= 400) throw new Error(`${route} returned HTTP ${response?.status() ?? 'no response'}`);
        await page.locator('#pageTitle').waitFor({ state: 'visible' });
        await page.waitForTimeout(100);
        const result = await page.evaluate(() => ({
          viewport: document.documentElement.clientWidth,
          content: document.documentElement.scrollWidth,
          title: document.querySelector('#pageTitle')?.innerText.trim(),
          mainLabel: document.querySelector('main')?.getAttribute('id'),
          mobileNavVisible: getComputedStyle(document.querySelector('.mobile-observatory-nav')).display !== 'none'
        }));
        if (!result.title || !result.mainLabel) throw new Error(`${route} did not expose its page heading/main landmark`);
        if (result.content > result.viewport + 1) throw new Error(`${route} overflows at ${width}x${height}: ${result.content}/${result.viewport}`);
        if (width <= 430 && !result.mobileNavVisible) throw new Error(`${route} hides mobile navigation at ${width}px`);
        if (width === 1440 && route === '/overview') await page.screenshot({ path: path.join(artifactDir, 'overview-desktop.png'), fullPage: true });
        if (width === 390 && route === '/regions/ap') await page.screenshot({ path: path.join(artifactDir, 'region-mobile.png'), fullPage: true });
        if (width === 390 && route === '/trends') await page.screenshot({ path: path.join(artifactDir, 'trends-mobile.png'), fullPage: true });
      }
    }

    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto(new URL('/overview', base).toString(), { waitUntil: 'domcontentloaded' });
    await page.keyboard.press('Control+k');
    await page.locator('#globalSearchDialog').waitFor({ state: 'visible' });
    if (!await page.locator('#globalSearchDialog').evaluate(dialog => dialog.open)) throw new Error('Ctrl+K did not open player search');
    await page.locator('#overviewSearch').press('Escape');
    if (await page.locator('#globalSearchDialog').evaluate(dialog => dialog.open)) throw new Error('Escape did not close player search');

    await page.goto(new URL('/regions/ap', base).toString(), { waitUntil: 'domcontentloaded' });
    await page.locator('#regionOverviewTab').press('ArrowRight');
    if (await page.locator('#regionStandingsTab').getAttribute('aria-selected') !== 'true') throw new Error('ArrowRight did not advance the region tab');
    if (pageErrors.length) throw new Error(`Browser exceptions: ${pageErrors.join(' | ')}`);
    if (serverErrors.length) throw new Error(`HTTP 5xx responses: ${serverErrors.join(' | ')}`);
    process.stdout.write(`PASS: ${viewports.length * routes.length} route/viewport checks, search shortcut, Escape and region-tab keyboard navigation.\n`);
  } finally {
    await browser.close();
  }
}

main().catch(error => {
  process.stderr.write(`${error.stack || error}\n`);
  process.exitCode = 1;
});
