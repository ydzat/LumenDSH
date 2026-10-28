// Declarative phone task runner on top of the observe → act → verify primitives.
// A task is a list of steps; every step records what it observed and whether the
// expectation held. Stop on the first failure and return home.
import {act, safeHome, screenSignature, waitFor} from './ui-loop.mjs';

const escapeText = value => String(value).replace(/([^A-Za-z0-9])/g, '\\$1');

// Keep a bounded step list; a task is data, never generated code.
export async function runTask(adb, steps, {onStep = () => {}} = {}) {
  const report = [];
  try {
    for (const step of steps) {
      const entry = {step: step.type, at: new Date().toISOString()};
      if (step.type === 'start') {
        adb(['shell', 'am', 'start', ...step.args]);
      } else if (step.type === 'tap') {
        const result = await act(adb, step.selector, {verify: step.verify});
        entry.changed = result.changed; entry.detail = result.detail;
        if (!result.ok) throw new Error('tap verify failed: ' + JSON.stringify(step.selector));
      } else if (step.type === 'type') {
        adb(['shell', 'input', 'text', escapeText(step.text)]);
        entry.text = step.text;
      } else if (step.type === 'key') {
        adb(['shell', 'input', 'keyevent', String(step.code)]);
        entry.code = step.code;
      } else if (step.type === 'waitFor') {
        const found = await waitFor(adb, step.selector, {timeoutMs: step.timeoutMs});
        entry.element = found.element;
      } else if (step.type === 'expect') {
        const state = await screenSignature(adb);
        const matched = state.elements.some(element =>
          (step.any ?? []).some(text => element.text.includes(text) || element.desc.includes(text)));
        entry.text = step.any;
        if (!matched) throw new Error('expected text not found: ' + (step.any ?? []).join(', '));
      } else if (step.type === 'home') {
        await safeHome(adb);
      } else throw new Error('unknown step: ' + step.type);
      report.push(entry); onStep(entry);
    }
    return {ok: true, steps: report};
  } catch (error) {
    try { report.push({step: 'recovery', at: new Date().toISOString(), home: true}); await safeHome(adb); }
    catch { /* best effort */ }
    return {ok: false, error: String(error.message ?? error), steps: report};
  }
}
