# M4.5 Share Intake Increment

Status: implemented intake/review; F09 PARTIAL, attachment delivery is not implemented.

ShareInbox is adapted from a5af7b105b7fdb006e3fc3be2a2179d83907c2cb (MIT,
2026 zhou-gu24). It retains upstream bounded URI copying, atomic manifest commit,
FIFO batches and explicit acknowledgement. Local changes remove public storage
archival and retain pending records in private files rather than cache.

ACTION_SEND / ACTION_SEND_MULTIPLE enqueue on a worker, without submitting a prompt.
The oldest batch is reviewed in the native inbox. Text can be copied into the existing
composer for explicit send; the inbox retains the batch until explicit deletion.
Attachments are inert and visibly marked not transferred to DSH. No attachment
path, MIME claim or share text causes execution. Limits: eight batches, sixteen
files/batch, 16 MiB/file, 32 MiB/batch and 262144 text characters. Truncated text and
failed attachments are reported in the review. The original sender file is untouched.

Verification: host Android API 29 compilation passed. Device checks are recorded
below as exercised; compilation does not prove URI grants or background recovery.

Outstanding: authenticated App-to-Termux file delivery, send/receipt reconciliation,
real URI permission/oversize cases, interrupted staging and rotation during intake.

Real Huawei Android 10 checks (2026-10-25):
- ACTION_SEND text/plain delivered M45_SHARED_TEXT into native review.
- Later/force-stop/relaunch preserved that pending batch.
- Moving text into the existing draft showed the exact marker in EditText and
  no corresponding transcript message: no automatic submission occurred.
- Phone compilation/signature verification and in-place install passed.

Consolidated APK SHA-256:
8d6210ac7f3d13352257b18686f9adf09133634b681f91626464061a6dac3e83.

- Explicit deletion removed the controlled pending batch and displayed an empty inbox.
  The unused test draft was cleared by restarting the client; no DSH task was sent.

Local silent review clip: artifacts/demos/20261025-001139-m45-share-review-0302b7f4.mp4.
Decoded frame inspected at 540x1168; container duration is 2 seconds despite the
6-second wall capture request. This is an incremental preview, not F14 completion
or the required full-stage 5-10 second demonstration.

## Authenticated attachment increment (0.4.12 / adapter 0.2.2)

Implemented: explicit native review/confirmation, session-bound batch release,
reserved request identity, MCP receive tool, private Termux chunk transfer and
SHA-256 receipts. The earlier intake-only limitation above is superseded by this
increment. No automatic resubmission or file execution is introduced.

Real target-device evidence:
- The non-exported test content provider supplied a 19-byte normal fixture through
  a URI read grant. Teardown timing initially revoked the grant before copy; keeping
  the test sender alive through reception resolved it. Failed copies were displayed
  as errors and were never sent. This was a real grant/lifecycle failure, not a mock.
- Explicit confirmation sent one message in the original controlled DSH session.
  The real MCP assistant_receive_share call returned a generated private Termux path
  and the expected digest. DSH read that file and replied M45_SHARED_FILE_OK.
- Repeating the real receive call returned the same bytes/path. Wrong-session access
  was rejected. Android showed transferred and disabled repeat submission.
- A real 17 MiB URI fixture exceeded the 16 MiB limit and was rejected; an unavailable
  fixture produced a visible read failure. Neither negative case was submitted.
- Adapter 0.2.2 was deployed by the existing owned profile patch HMR; no DSH restart,
  model credential access or bundled-engine change was performed.

APK SHA-256 for these checks:
c32fa31a69b064941e6c8b78d1cead32b71a599fd99a40831575d290bbbe6363.

F09 remains PARTIAL: process interruption during staging/transfer, maximum batch,
multi-chunk receipt-loss and submission uncertainty fault injection remain open.
