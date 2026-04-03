import { chromium } from 'playwright';

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage();

console.log('Opening e-Stat...');
await page.goto('https://www.e-stat.go.jp/municipalities/cities/absorption-separation-of-municipalities', {
  waitUntil: 'networkidle', timeout: 60000,
});

// 1. 検索（空=全件）
console.log('Search...');
await page.click('button[value="search"]');
await page.waitForTimeout(5000);

// 2. ダウンロードボタン
console.log('Open download dialog...');
await page.click('button:has-text("ダウンロード")');
await page.waitForTimeout(2000);

// 3. CSV形式(UTF-8(BOM無し)) のラジオを選択（ラベルテキストで）
console.log('Select UTF-8 no BOM...');
await page.evaluate(() => {
  const labels = document.querySelectorAll('label');
  for (const lbl of labels) {
    if (lbl.textContent.includes('BOM無し')) {
      const radio = lbl.querySelector('input[type="radio"]') || lbl.previousElementSibling;
      if (radio) radio.click();
      break;
    }
  }
});
await page.waitForTimeout(500);

// 4. ダウンロード - ダイアログ内のボタンを探して押す
console.log('Looking for dialog buttons...');
const dialogButtons = await page.$$eval('button', els =>
  els.filter(e => e.offsetParent !== null).map(e => ({ text: e.textContent.trim().slice(0,30), value: e.value }))
);
console.log('Visible buttons:', JSON.stringify(dialogButtons));

// okボタンまたはダウンロードボタンを押す
console.log('Download...');
const [download] = await Promise.all([
  page.waitForEvent('download', { timeout: 60000 }),
  page.evaluate(() => {
    // value="ok" のボタン、またはダイアログ内の確定ボタンを探す
    const btns = document.querySelectorAll('button');
    for (const btn of btns) {
      if (btn.offsetParent && (btn.value === 'ok' || btn.textContent.includes('OK'))) {
        btn.click();
        return 'clicked ok';
      }
    }
    // フォームsubmit
    const form = document.querySelector('#city-merger-form');
    if (form) {
      const input = document.createElement('input');
      input.type = 'hidden'; input.name = 'op'; input.value = 'download';
      form.appendChild(input);
      form.submit();
      return 'form submitted';
    }
    return 'nothing found';
  }),
]);

await download.saveAs('/tmp/estat-haichi.csv');
console.log('Saved to /tmp/estat-haichi.csv');

await browser.close();
