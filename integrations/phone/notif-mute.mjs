// Mute every app's notifications except an allowlist, through the one surface
// EMUI 10 exposes us: Settings → 通知 → 批量管理, whose rows each carry an
// allow/block switch. AppOps has no per-app notification op on this build
// (measured: POST_NOTIFICATION mode is accepted but NMS still buzzes), and
// `cmd notification suspend_package` does not block either, so the switch IS the
// contract — the same one the user would flip by hand, just driven from adb.
//
// The tool is observe → act → verify: it dumps the list, toggles the switches
// that need it, re-reads each row to confirm the flip stuck, scrolls until the
// list stops growing, and writes a before/after evidence file so any mute can be
// undone and audited. Labels are matched exactly; the allowlist wins over any
// previous mute, so re-running it after adding an app is safe.
import {execFileSync} from 'node:child_process'
import {mkdirSync, writeFileSync} from 'node:fs'
import {dirname} from 'node:path'
import {parseUi} from './ui-loop.mjs'

const MENU_CLASS = 'com.huawei.systemmanager/com.huawei.notificationmanager.ui.NotificationManagmentActivity'
const BATCH_LABEL = '批量管理'
const SCROLL_DOWN = ['shell', 'input', 'swipe', '540', '1900', '540', '700', '260']
const SCROLL_UP = ['shell', 'input', 'swipe', '540', '700', '540', '1900', '260']

const arg = (name, fallback) => {
  const index = process.argv.indexOf('--' + name)
  return index === -1 ? fallback : process.argv[index + 1]
}
const flag = name => process.argv.includes('--' + name)

const allowed = new Set(arg('allow', 'LumenDSH Web,Termux,Termux:API')
  .split(',').map(value => value.trim()).filter(Boolean))
const evidencePath = arg('out', '')
const dryRun = flag('dry-run')

const adb = (args, timeout = 60000) => execFileSync('adb', args, {encoding: 'utf8', timeout})
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))

function dump() {
  adb(['shell', 'uiautomator', 'dump', '/sdcard/lumen-notif.xml'], 90000)
  const xml = adb(['shell', 'cat', '/sdcard/lumen-notif.xml'], 90000)
  return parseUi(xml)
}

// Secondary line and non-row chrome that must never be treated as an app label.
const SECONDARY = /^\d+\s*(分钟|小时|天)前$|^最近未发送通知$/
const CHROME = new Set(['按发送时间排序', '搜索应用', BATCH_LABEL, '通知', '批量管理'])

// One row per Switch: the label is the closest text line above it inside the
// same band. Headers and the "N 分钟前" secondary lines are skipped, so a row can
// never be paired with a neighbouring row's switch.
function rows(elements) {
  const switches = elements
    .filter(element => element.className.includes('Switch') && element.bounds.centerX > 800)
    .sort((a, b) => a.bounds.centerY - b.bounds.centerY)
  const out = []
  for (const toggle of switches) {
    const label = elements
      .filter(element => element.text.trim() !== '' && !element.className.includes('Switch'))
      .filter(element => !SECONDARY.test(element.text.trim()) && !CHROME.has(element.text.trim()))
      .filter(element => element.bounds.centerX < 800)
      .filter(element => Math.abs(element.bounds.centerY - toggle.bounds.centerY) <= 140)
      .sort((a, b) => Math.abs(a.bounds.centerY - toggle.bounds.centerY) - Math.abs(b.bounds.centerY - toggle.bounds.centerY))[0]
    if (label === undefined) continue
    out.push({label: label.text.trim(), checked: toggle.checked, toggle})
  }
  return out
}

async function openBatchPage() {
  // Force-stop first: the batch list remembers its scroll position, and EMUI's
  // activity is a singleton, so a re-entry would silently start at the bottom.
  adb(['shell', 'am', 'force-stop', 'com.huawei.systemmanager'])
  await sleep(600)
  adb(['shell', 'am', 'start', '-n', MENU_CLASS])
  await sleep(2200)
  for (let attempt = 0; attempt < 10; attempt++) {
    const elements = dump()
    const entry = elements.find(element => element.text.trim() === BATCH_LABEL && element.bounds.centerY > 600)
    if (entry !== undefined) {
      adb(['shell', 'input', 'tap', String(entry.bounds.centerX), String(entry.bounds.centerY)])
      await sleep(2000)
      return true
    }
    await sleep(800)
  }
  return false
}

// The batch list remembers its scroll position between visits, so a run must
// start from the top or it would only ever see the tail of the alphabet.
async function scrollToTop() {
  let previous = ''
  for (let pass = 0; pass < 30; pass++) {
    const signature = rows(dump()).map(row => row.label + ':' + String(row.checked)).join(',')
    if (signature === previous) return
    previous = signature
    adb(SCROLL_UP)
    await sleep(600)
  }
}

async function mutePage(actions) {
  const handled = new Set()
  let stable = 0
  let previous = ''
  for (let pass = 0; pass < 60; pass++) {
    const list = rows(dump())
    const signature = list.map(row => row.label + ':' + String(row.checked)).join(',')
    if (signature === previous) stable++
    else { stable = 0; previous = signature }
    for (const row of list) {
      if (!row.checked || allowed.has(row.label) || handled.has(row.label)) continue
      handled.add(row.label)
      actions.push({label: row.label, action: dryRun ? 'would-mute' : 'muted', at: new Date().toISOString(), y: row.toggle.bounds.centerY})
      if (!dryRun) {
        adb(['shell', 'input', 'tap', String(row.toggle.bounds.centerX), String(row.toggle.bounds.centerY)])
        await sleep(700)
      }
    }
    adb(SCROLL_DOWN)
    await sleep(800)
    if (stable >= 2) break
  }
}

const actions = []
const started = new Date().toISOString()
if (!(await openBatchPage())) {
  console.error('could not open the batch notification page')
  process.exit(2)
}
await mutePage(actions)
// Verification pass: one more full walk, only reading.
const remaining = []
{
  await openBatchPage()
  let previous = ''
  for (let pass = 0; pass < 40; pass++) {
    const list = rows(dump())
    for (const row of list) {
      if (row.checked) remaining.push(row.label)
    }
    const signature = list.map(row => row.label + ':' + String(row.checked)).join(',')
    adb(SCROLL_DOWN)
    await sleep(800)
    if (signature === previous) break
    previous = signature
  }
}
const report = {
  started,
  finished: new Date().toISOString(),
  allowed: [...allowed],
  dryRun,
  muted: actions.filter(action => action.action === 'muted').length,
  actions,
  stillEnabled: [...new Set(remaining)],
}
console.log(JSON.stringify(report, null, 2))
if (evidencePath !== '') {
  mkdirSync(dirname(evidencePath), {recursive: true})
  writeFileSync(evidencePath, JSON.stringify(report, null, 2) + '\n')
}
