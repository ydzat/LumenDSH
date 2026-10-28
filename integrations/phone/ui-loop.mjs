// Observe → act → verify primitives for the phone body.
//
// The module is transport-agnostic: callers pass an `adb(args)` function, so the
// same logic can later run over the app bridge. It parses UIAutomator dumps into
// compact elements, revalidates a target before acting (stale-element protection),
// and waits for the screen to stabilise after an action.

const ATTR = /([a-z-]+)="([^"]*)"/g;
const decode = value => value.replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"');

export function parseBounds(value) {
  const match = /\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]/.exec(value ?? '');
  if (!match) return null;
  const [x1, y1, x2, y2] = match.slice(1).map(Number);
  return {x1, y1, x2, y2, centerX: Math.round((x1 + x2) / 2), centerY: Math.round((y1 + y2) / 2),
    width: x2 - x1, height: y2 - y1};
}

export function parseUi(xml) {
  const elements = [];
  for (const part of String(xml).split('<node').slice(1)) {
    const tag = part.slice(0, part.indexOf('>') + 1);
    const attrs = {};
    ATTR.lastIndex = 0;
    let match;
    while ((match = ATTR.exec(tag))) attrs[match[1]] = decode(match[2]);
    const bounds = parseBounds(attrs.bounds);
    if (!bounds) continue;
    elements.push({
      index: Number(attrs.index ?? -1),
      resourceId: attrs['resource-id'] ?? '',
      className: attrs.class ?? '',
      text: attrs.text ?? '',
      desc: attrs['content-desc'] ?? '',
      package: attrs.package ?? '',
      clickable: attrs.clickable === 'true',
      checkable: attrs.checkable === 'true',
      checked: attrs.checked === 'true',
      enabled: attrs.enabled !== 'false',
      bounds,
    });
  }
  return elements;
}

// Stable identity of an element for stale-target detection and change detection.
// `checked` participates so a toggle is visible as a real screen change.
export function elementSignature(element) {
  return [element.resourceId, element.className, element.text, element.desc,
    element.bounds.x1, element.bounds.y1, element.bounds.x2, element.bounds.y2,
    element.checked ? 'checked' : 'unchecked'].join('|');
}

export function findElement(elements, selector) {
  const matches = elements.filter(element => {
    if (selector.resourceId && element.resourceId !== selector.resourceId) return false;
    if (selector.text && !element.text.includes(selector.text)) return false;
    if (selector.desc && !element.desc.includes(selector.desc)) return false;
    if (selector.className && element.className !== selector.className) return false;
    if (selector.clickable && !element.clickable) return false;
    return true;
  });
  if (selector.preferClickable) matches.sort((a, b) => Number(b.clickable) - Number(a.clickable));
  return matches;
}

export function requireElement(elements, selector) {
  const matches = findElement(elements, selector);
  if (!matches.length) throw new Error('element not found: ' + JSON.stringify(selector));
  if (matches.length > 1) throw new Error('ambiguous element: ' + JSON.stringify(selector) + ' (' + matches.length + ')');
  return matches[0];
}

export async function screenSignature(adb) {
  adb(['shell', 'uiautomator', 'dump', '/sdcard/lumen-ui.xml'], 45000);
  const xml = adb(['shell', 'cat', '/sdcard/lumen-ui.xml']);
  const elements = parseUi(xml);
  return {xml, elements, signature: elements.map(element => elementSignature(element)).join(';')};
}

export async function waitStable(adb, {timeoutMs = 4000, intervalMs = 250, samples = 2} = {}) {
  const started = Date.now();
  let previous = null, stable = 0, last = null;
  while (Date.now() - started < timeoutMs) {
    last = await screenSignature(adb);
    if (previous === last.signature) stable++;
    else { stable = 0; previous = last.signature; }
    if (stable >= samples) return last;
    await new Promise(resolve => setTimeout(resolve, intervalMs));
  }
  return last ?? await screenSignature(adb);
}

const noProgress = new Map();

// Clear per-process stuck counters (tests, or a deliberate retry after a fix).
export function resetStuckState() { noProgress.clear(); }

// Act on a selector: observe, revalidate immediately before the tap (a target that
// moved or was replaced is stale), tap, wait for stability, observe and verify.
export async function act(adb, selector, options = {}) {
  const verify = options.verify;
  for (let attempt = 0; attempt < 2; attempt++) {
    const before = await screenSignature(adb);
    const target = requireElement(before.elements, selector);
    const signature = elementSignature(target);
    const confirm = await screenSignature(adb);
    const current = confirm.elements.find(element => elementSignature(element) === signature);
    if (!current) continue; // stale: re-resolve once, then give up.
    adb(['shell', 'input', 'tap', String(current.bounds.centerX), String(current.bounds.centerY)]);
    const after = await waitStable(adb);
    const check = verify ? verify(after.elements) : {ok: true};
    const changed = before.signature !== after.signature;
    const key = JSON.stringify(selector);
    if (!changed) {
      const count = (noProgress.get(key) ?? 0) + 1;
      noProgress.set(key, count);
      if (count >= (options.stuckLimit ?? 2)) {
        throw new Error('stuck: screen unchanged after ' + count + ' taps on ' + key);
      }
    } else noProgress.delete(key);
    return {ok: Boolean(check.ok), target, before, after, attempts: attempt + 1,
      detail: check.detail ?? null, changed};
  }
  throw new Error('stale target: element kept changing before the tap');
}

// Return to the home screen and settle. The safe-home routine for failed tasks.
export async function safeHome(adb) {
  adb(['shell', 'input', 'keyevent', '3']);
  return waitStable(adb);
}

// Poll until a selector appears, or fail with the last observation attached.
export async function waitFor(adb, selector, {timeoutMs = 8000, intervalMs = 300} = {}) {
  const started = Date.now();
  let last = null;
  while (Date.now() - started < timeoutMs) {
    last = await screenSignature(adb);
    const matches = findElement(last.elements, selector);
    if (matches.length) return {element: matches[0], observation: last};
    await new Promise(resolve => setTimeout(resolve, intervalMs));
  }
  const error = new Error('timeout waiting for ' + JSON.stringify(selector));
  error.observation = last;
  throw error;
}

// Run a physical task; on failure return home and attach that observation to the
// error so the caller can report where the body ended up.
export async function withRecovery(adb, task) {
  try { return await task(); }
  catch (error) {
    try { error.safeHome = await safeHome(adb); } catch { error.safeHome = null; }
    throw error;
  }
}
