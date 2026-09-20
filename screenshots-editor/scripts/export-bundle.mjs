import { chromium } from "playwright";
import path from "node:path";

const editorUrl = process.env.SCREENSHOT_EDITOR_URL ?? "http://127.0.0.1:3000";
const requestedDevice = process.env.SCREENSHOT_DEVICE;
const output = path.resolve(process.argv[2] ?? "exports/pixelpals-play-store-es-en.zip");
const browser = await chromium.launch({
  headless: true,
  executablePath: process.env.CHROME_PATH ?? "/usr/bin/google-chrome",
  args: ["--no-sandbox"],
});
try {
  const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
  page.on("console", (message) => console.log(`[browser:${message.type()}] ${message.text()}`));
  page.on("pageerror", (error) => console.error(`[browser:error] ${error.message}`));
  await page.goto(editorUrl, { waitUntil: "networkidle" });
  await page.evaluate(() => localStorage.clear());
  await page.reload({ waitUntil: "networkidle" });
  if (requestedDevice) {
    const labels = {
      android: "Android Phone",
      "android-7": 'Android 7" Tablet',
      "android-10": 'Android 10" Tablet',
    };
    const label = labels[requestedDevice];
    if (!label) throw new Error(`Unsupported SCREENSHOT_DEVICE: ${requestedDevice}`);
    await page.getByRole("combobox").first().click();
    await page.getByRole("option", { name: label }).click();
  }
  const exportButton = page.getByRole("button", { name: "Export bundle" });
  try {
    await exportButton.waitFor({ state: "visible", timeout: 60_000 });
  } catch (error) {
    console.error(await page.locator("body").innerText());
    await page.screenshot({ path: "/tmp/pixelpals-editor-export-error.png", fullPage: true });
    throw error;
  }
  const downloadPromise = page.waitForEvent("download", { timeout: 180_000 });
  await exportButton.click();
  const download = await downloadPromise;
  await download.saveAs(output);
  console.log(output);
} finally {
  await browser.close();
}
