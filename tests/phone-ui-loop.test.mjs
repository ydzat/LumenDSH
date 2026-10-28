import test from 'node:test';
import assert from 'node:assert/strict';
import {parseUi, parseBounds, findElement, requireElement, elementSignature, act} from '../integrations/phone/ui-loop.mjs';

const XML = `<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="0"><node index="0" text="" resource-id="root" class="android.widget.FrameLayout" content-desc="" clickable="false" bounds="[0,0][1080,2232]">
<node index="0" text="蓝牙" resource-id="com.android.settings:id/switch_bar" class="android.widget.Switch" content-desc="" clickable="false" checkable="true" checked="false" bounds="[0,312][1080,456]" />
<node index="1" text="" resource-id="com.android.settings:id/switch_widget" class="android.widget.Switch" content-desc="" clickable="true" checkable="true" checked="false" bounds="[882,312][1026,456]" />
</node></hierarchy>`;

test('parseBounds reads the four corners and the center', () => {
  assert.deepEqual(parseBounds('[882,312][1026,456]'), {x1: 882, y1: 312, x2: 1026, y2: 456, centerX: 954, centerY: 384, width: 144, height: 144});
  assert.equal(parseBounds('nope'), null);
});

test('parseUi keeps identity, state and geometry', () => {
  const elements = parseUi(XML);
  assert.equal(elements.length, 3);
  const switchWidget = elements.find(element => element.resourceId === 'com.android.settings:id/switch_widget');
  assert.equal(switchWidget.className, 'android.widget.Switch');
  assert.equal(switchWidget.clickable, true);
  assert.equal(switchWidget.checked, false);
  assert.equal(switchWidget.bounds.centerX, 954);
});

test('findElement selects by resource id and flags clickability', () => {
  const elements = parseUi(XML);
  assert.equal(findElement(elements, {resourceId: 'com.android.settings:id/switch_widget'}).length, 1);
  assert.equal(findElement(elements, {className: 'android.widget.Switch'}).length, 2);
  assert.equal(findElement(elements, {resourceId: 'missing'}).length, 0);
});

test('requireElement rejects ambiguity and missing targets', () => {
  const elements = parseUi(XML);
  assert.throws(() => requireElement(elements, {className: 'android.widget.Switch'}), /ambiguous/);
  assert.throws(() => requireElement(elements, {text: '不存在'}), /not found/);
});

test('elementSignature changes when geometry moves, which is the stale-target signal', () => {
  const before = parseUi(XML).find(element => element.resourceId === 'com.android.settings:id/switch_widget');
  const moved = parseUi(XML.replace('[882,312][1026,456]', '[882,400][1026,544]'))
    .find(element => element.resourceId === 'com.android.settings:id/switch_widget');
  assert.notEqual(elementSignature(before), elementSignature(moved));
});

test('act re-resolves a target that moved between observe and tap', async () => {
  const at = x => `<node index="0" text="" resource-id="sw" class="android.widget.Switch" clickable="true" bounds="[${x},300][${x + 144},444]" />`;
  const frames = [at(100), at(300), at(300), at(300), at(300), at(300), at(300)];
  let dump = 0;
  const taps = [];
  const adb = args => {
    if (args[1] === 'uiautomator') { dump++; return ''; }
    if (args[1] === 'cat') return frames[Math.min(dump - 1, frames.length - 1)];
    if (args[1] === 'input' && args[2] === 'tap') { taps.push({x: Number(args[3]), y: Number(args[4])}); return ''; }
    return '';
  };
  const result = await act(adb, {resourceId: 'sw'});
  assert.equal(result.attempts, 2, 'the moved target must be re-resolved once');
  assert.equal(taps.length, 1);
  assert.equal(taps[0].x, 372, 'tap lands on the re-resolved center');
});
