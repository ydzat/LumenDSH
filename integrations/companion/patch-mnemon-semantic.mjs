import {readFile,writeFile} from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
// Applied only to the pinned Mnemon 0.2.12 source. Retain upstream retrieval.
const root=process.argv[2];assert(root,'Expected Mnemon source directory');
async function replace(file,oldText,newText){const p=path.join(root,file);const s=await readFile(p,'utf8');assert.equal(s.split(oldText).length,2,'Source drift: '+file);await writeFile(p,s.replace(oldText,newText));}
await replace('cmd/memory/recall.go',
`\t\tif ec.Available() {
\t\t\tqueryVec, _ = ec.Embed(keyword)
\t\t}`,
`\t\tif ec.Available() {
\t\t\tquery := keyword
\t\t\tif instruction := os.Getenv("MNEMON_QUERY_INSTRUCTION"); instruction != "" {
\t\t\t\tquery = "Instruct: " + instruction + "\\nQuery: " + keyword
\t\t\t}
\t\t\tvar embedErr error
\t\t\tqueryVec, embedErr = ec.Embed(query)
\t\t\tif os.Getenv("MNEMON_REQUIRE_SEMANTIC") == "1" && embedErr != nil {
\t\t\t\treturn fmt.Errorf("semantic recall unavailable: %w", embedErr)
\t\t\t}
\t\t}
\t\tif os.Getenv("MNEMON_REQUIRE_SEMANTIC") == "1" && len(queryVec) == 0 {
\t\t\treturn fmt.Errorf("semantic recall unavailable: embedding service returned no vector")
\t\t}`);
await replace('cmd/memory/remember.go',
'\n\t\t// 2. Built-in diff: check for duplicates/conflicts (read-only, before transaction)',
`\n\t\tif os.Getenv("MNEMON_REQUIRE_SEMANTIC") == "1" && len(embeddingVec) == 0 {
\t\t\treturn fmt.Errorf("semantic memory write unavailable: embedding failed; no record written")
\t\t}
\n\t\t// 2. Built-in diff: check for duplicates/conflicts (read-only, before transaction)`);
console.log('MNEMON_SEMANTIC_PATCH_APPLIED');
