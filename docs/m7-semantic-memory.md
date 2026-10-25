# M7 mandatory semantic memory

Status: implemented and installed on 2026-10-25 as Android `0.7.43-semantic`
(code 43). This supersedes the keyword-only baseline. It closes the semantic
integration gap, not every M7 requirement or every memory-quality scenario.

## Reused implementation and provenance

- [Termux llama.cpp recipe](https://github.com/termux/termux-packages/blob/master/packages/llama-cpp/build.sh): package `0.7.0`, MIT; upstream source archive SHA-256 `55d67acf3ad2ad747a9bbeb75f9f0e173dbcc0d1753a471038a90c819421c5e1`.
- [Official Qwen model](https://huggingface.co/Qwen/Qwen3-Embedding-0.6B-GGUF): revision `370f27d7550e0def9b39c1f16d3fbaa13aa67728`, Apache-2.0, Q8_0 file 639150592 bytes, SHA-256 `06507c7b42688469c4e7298b0a1e16deff06caf291cf0a5b278c308249c3e439`.
- [Mnemon](https://github.com/mnemon-dev/mnemon/tree/v0.2.12): source archive SHA-256 `da28cd49afb5c967414576485f689a0ba0127b267bb0379dcd90dd06d325b628`, Apache-2.0. Built on the phone with Go 1.27.3, CGO disabled, Linux ARM64 target. Patched binary version `0.2.12-sw-semantic.1`, SHA-256 `ad102e871bd6d0154edc393b43884dbcc2c006f710cf0f939bfc1a0e74b8a65b`.
- dsh-mnemon remains pinned at `0.5.34`, MIT. Its actual installed `embedding-status` endpoint supplies per-workspace coverage; no simulated UI status is used.

The user explicitly approved the new runtime/model. Inference stays on loopback
`127.0.0.1:3083`; no new cloud provider receives memory. Runtime/model files remain
outside the APK and Git. Original memory records and session IDs are retained.

## Retrieval and lifecycle

Mnemon still owns vector storage, hybrid vector/keyword/graph ranking and deletion.
The model emits 1024-dimensional embeddings with last-token pooling. Query-only
instructions follow Qwen's documented retrieval format; document text is unchanged.
`patch-mnemon-semantic.mjs` makes two bounded upstream adaptations: add that query
instruction and reject a failed semantic query/write rather than silently reporting
keyword-only retrieval or a vectorless write as success. Explicit keyword operations
remain available for exact lookup; ordinary smart recall uses vectors.

`start-embedding.sh` serializes startup and starts the dedicated Termux tmux process
when missing. `mnemon-termux` waits for readiness before CLI execution. An existing
service is reused; no new inference process is started per query. The DSH profile
records the endpoint/model and existing workspace records are backfilled before
activation. Candidate source and tests remain isolated from owner memory.

The server currently uses four CPU threads, one slot and a 2048-token context.
Oversized embedding input fails explicitly; it is not silently truncated. This is
not the model's full advertised context. A sampled RSS after the retrieval suite was
909040 KiB (about 888 MiB); a short Chinese embedding request took 472 ms.
These are individual phone observations, not battery or latency guarantees.

## Real-device evidence

`semantic-check.mjs` executes the actual native model and patched Mnemon binary on
the phone, with four controlled records in a private test store:

| Query | First returned fact |
| --- | --- |
| 如果要给她点奶咖，配料应该怎样调整？ | Lactose intolerance; replace cow milk with oat drink |
| 休息日安排哪种户外活动会让他开心？ | Weekend hiking preference |
| 夜深了还能向她播报提醒吗？ | Quiet after 23:00 due to light sleep |
| 资料的备份频率和保存地点是什么？ | Weekly Friday backup to an external disk at home |

All four ranked the expected record first; coverage was 4/4. The suite also verified
that an unreachable embedding endpoint rejects semantic recall and new writes, that
a failed write creates no record, and that a forgotten item is excluded from semantic
recall. The extended run took 5508 ms. This small controlled set does not establish
universal retrieval accuracy or unrelated-query precision.

The actual DSH model then used `mnemon_recall`, `mnemon_remember`, and smart recall in
the existing controlled session. A differently worded question about ordering a latte
retrieved the oat-drink requirement from the real long-term space. Independent Android
Java transport checks found semantic coverage 2/2, the pre-existing durable test fact,
no forgotten marker, and no controlled fact leaking into the owner workspace.

Stopping only the embedding tmux session and invoking the production wrapper restarted
the service automatically; coverage remained 2/2. DSH was restarted for configuration,
and retained its existing sessions. An initial duplicate YAML key was diagnosed and
fixed in both activation and profile preparation before delivery; the active backend
is connected. Full phone APK build, signature verification, hash verification and
in-place install passed. APK SHA-256:
`c3156cc13764bbbbeb81df40e18b5a7d7a230860a8e64fbdbc5514aba12ac486`.

The installed memory page was visually inspected and displays real availability and
2/2 coverage. [Reviewed real-device demonstration, approximately 7 seconds, silent](../artifacts/demos/20261025-135134-m7-semantic-memory-2b0bb303.mp4).
Original user session selection is restored after verification.

## Remaining M7 closure work

Keep the milestone open. A complete audit must still cover proactive activity policy,
busy/budget branches, unrelated-query quality, longer-lived correction/consolidation
behavior, and previously deferred human acoustic scenarios within their agreed scope.
Do not convert an implementation claim, prompt policy or this small retrieval suite
into acceptance of all those behaviors. The user rejected reduced delivery; the next
batch must close actual requirements rather than rename omissions as optional extras.


## Subsequent full-requirement implementation

The original remaining-work paragraph above is superseded by the current
[R01-R20 audit](full-requirements-audit.md) and [C20-C25 evidence](m7-behavior-policy.md).
Whale scheduling, versioned behavior, bounded same-agent activities, real source
projection, semantic Documents, native sleep and native compaction continuity are
implemented. Previously waived human acoustic checks remain waived for closure.
The Documents adapter preserves native storage/grants and returns the actual ranked
paragraph, including a relevant tail passage beyond the beginning of a long record.
