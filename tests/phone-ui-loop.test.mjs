import test from 'node:test';
import assert from 'node:assert/strict';
import {parseUi, parseBounds, findElement, requireElement, elementSignature} from '../integrations/phone/ui-loop.mjs';

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
