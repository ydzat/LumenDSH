#!/usr/bin/env node
// Adds one activity to the installed companion policy through the authenticated
// local route. Used after a host upgrade to enable the develop activity without
// rebuilding the client UI.
import {readFileSync} from 'node:fs';
import path from 'node:path';

const activity = process.argv[2];
if (!['consolidate', 'reflect', 'develop'].includes(activity)) throw new Error('Unknown activity');
const connection = JSON.parse(readFileSync(path.join(process.env.HOME,
  '.local/state/sliver-wolf-backend/connection-3080.json'), 'utf8'));
const base = 'http://127.0.0.1:3080';
const headers = {cookie: connection.cookie, 'content-type': 'application/json'};
async function call(route, options = {}) {
  const response = await fetch(base + route, {headers, ...options});
  const body = await response.json();
  if (!response.ok) throw new Error(route + ' -> ' + response.status + ' ' + JSON.stringify(body));
  return body;
}
const status = await call('/sliver-wolf/companion/status');
const policy = {...status.policy, activities: [...status.policy.activities]};
if (!policy.activities.includes(activity)) policy.activities.push(activity);
if (policy.activities.length > 3) throw new Error('Activity list would exceed three entries');
const saved = await call('/sliver-wolf/companion/policy', {method: 'POST',
  body: JSON.stringify({policy, expectedRevision: status.revision})});
process.stdout.write(JSON.stringify({activities: saved.policy.activities, enabled: saved.policy.enabled}) + '\n');
