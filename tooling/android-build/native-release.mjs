#!/usr/bin/env node
// Independent Termux build entry. Signing material stays outside candidate source and receipts.
import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {shaBuffer,writeJson} from '../content-release/receipts.mjs';
const [rootArg,baseArg,id]=process.argv.slice(2), root=path.resolve(rootArg??'.');
if(!/^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$/.test(id??'')||!Number.isSafeInteger(Number(baseArg)))throw new Error('Usage: native-release.mjs CANDIDATE_SOURCE BASE_CODE RELEASE_ID');
const digest=file=>shaBuffer(fs.readFileSync(file));
function files(dir){return fs.readdirSync(dir,{withFileTypes:true}).sort((a,b)=>a.name.localeCompare(b.name,'en')).flatMap(e=>{const f=path.join(dir,e.name);if(e.isSymbolicLink())throw new Error('Symlink in candidate inputs');return e.isDirectory()?files(f):[f];});}
function inputs(){return [...files(path.join(root,'apps/android')), ...files(path.join(root,'tooling/android-build')), ...files(path.join(root,'tooling/content-release'))].map(file=>({path:path.relative(root,file),sha256:digest(file)}));}
const before=inputs();
const result=spawnSync('bash',[path.join(root,'tooling/android-build/build.sh')],{cwd:root,encoding:'utf8',maxBuffer:8*1024*1024});
if(result.status!==0){process.stderr.write(result.stderr??'');throw new Error('Phone build failed');}
const match=result.stdout.match(/^APK=(.+)$/m);if(!match)throw new Error('Build output missing APK');
const apk=match[1].trim(),dir=path.dirname(apk),after=inputs();
if(JSON.stringify(before)!==JSON.stringify(after))throw new Error('Build inputs changed during compilation; inspect and rebuild');
const manifest=fs.readFileSync(path.join(root,'apps/android/AndroidManifest.xml'),'utf8');
const targetCode=Number(manifest.match(/android:versionCode="(\d+)"/)[1]);
if(targetCode<=Number(baseArg))throw new Error('Native upgrade must increase versionCode');
const verify=spawnSync('apksigner',['verify','--verbose','--print-certs',apk],{encoding:'utf8'});
if(verify.status!==0)throw new Error('APK signature verification failed');
const dependencies=fs.readFileSync(path.join(dir,'dependencies-sha256.txt'),'utf8');
const sourceSha256=shaBuffer(Buffer.from(JSON.stringify({files:before,dependencies})));
const receipt={releaseId:id,baseCode:Number(baseArg),targetCode,apkSha256:digest(apk),sourceSha256,dexSha256:digest(path.join(dir,'dex/classes.dex'))};
writeJson(path.join(dir,'native-release.json'),receipt);
writeJson(path.join(dir,'build-evidence.json'),{schemaVersion:1,inputs:before,dependencies,receipt,signatureVerification:verify.stdout,apk});
console.log(JSON.stringify({apk,receipt:path.join(dir,'native-release.json'),evidence:path.join(dir,'build-evidence.json')}));
