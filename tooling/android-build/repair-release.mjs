#!/usr/bin/env node
// Rebuild retained, verified source at a higher version; Android APK downgrade is not assumed.
import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {shaBuffer} from '../content-release/receipts.mjs';
const [evidenceFile,baseCode,targetCode,id]=process.argv.slice(2);
const evidence=JSON.parse(fs.readFileSync(evidenceFile,'utf8'));
if(!Number.isSafeInteger(Number(baseCode))||!Number.isSafeInteger(Number(targetCode))||Number(targetCode)<=Number(baseCode))throw new Error('Repair version must increase');
const original=path.dirname(path.dirname(evidence.apk));
if(!evidence.inputs?.length||!evidence.receipt)throw new Error('Missing retained build inputs');
if(shaBuffer(Buffer.from(JSON.stringify({files:evidence.inputs,dependencies:evidence.dependencies})))!==evidence.receipt.sourceSha256)throw new Error('Build evidence integrity mismatch');
for(const entry of evidence.inputs){
 if(path.isAbsolute(entry.path)||entry.path.split(/[\\/]/).includes('..'))throw new Error('Invalid source path');
 const file=path.join(original,entry.path);if(fs.lstatSync(file).isSymbolicLink()||shaBuffer(fs.readFileSync(file))!==entry.sha256)throw new Error('Retained source changed: '+entry.path);
}
const root=fs.mkdtempSync(path.join(path.dirname(original),'candidate-repair-'));
for(const entry of evidence.inputs){const destination=path.join(root,entry.path);fs.mkdirSync(path.dirname(destination),{recursive:true});fs.copyFileSync(path.join(original,entry.path),destination);}
const manifest=path.join(root,'apps/android/AndroidManifest.xml');
fs.writeFileSync(manifest,fs.readFileSync(manifest,'utf8').replace(/android:versionCode="\d+"/,`android:versionCode="${targetCode}"`).replace(/android:versionName="[^"]+"/,`android:versionName="0.6.${targetCode}-repair"`));
const run=spawnSync('node',[path.join(root,'tooling/android-build/native-release.mjs'),root,baseCode,id],{stdio:'inherit'});
process.exit(run.status??1);
