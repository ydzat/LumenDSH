# M4.5 Reply Card Increment

Status: C01/C03 foundations implemented; F03/F04 PARTIAL, not milestone completion.
Version: 0.4.8-m45-preview (15). Date: 2026-10-24.

## Reused code

ReplyCard adapts OverlayService collapseCard/expandCard, MaxHeightScrollView,
isCardAtBottom/renderCardText/updateCard from Android reference revision
`a5af7b105b7fdb006e3fc3be2a2179d83907c2cb` (MIT, 2026 zhou-gu24).
Full license and local changes ship in APK THIRD_PARTY_NOTICES.txt.
The local Activity owns the view; no WindowManager/service or engine polling is copied.
Full response text is preserved rather than stripping Markdown markers.

The existing installed rc.7 protocol inspection exposes public text-delta chunks.
Presentation anchors each response to its first public-text sequence, updates the
same queued card through assistant/message completion, and does not resurrect a
dismissed response. Reasoning chunks are excluded. Pending task time does not expire
the card. Opening a card pins reading; controls remain outside its scroll viewport.

## Executed checks

- Host javac/API 29 compilation passed.
- Actual Android check-pets.sh passed PET_IMPORT_CHECKS_PASS and
  PROFILE_AND_ATTENTION_CHECKS_PASS. Added cases cover pinned stream replacement,
  queued replacement and non-resurrection after dismissal. These are state tests,
  not proof of live scrolling.
- Termux native build/signature verification and in-place install passed.
- A real prompt in the existing controlled conversation requested 18 fictional
  sentences, bounded by CARD_START/CARD_END, without tools. The actual reply was
  shown as one queued item and restored after a client-only restart.
- Actual phone UI: expand, full text present in a capped ScrollView, swipe scroll,
  reading retained beyond 12 seconds, and explicit collapse passed.
- Visual inspection found the initial close button overlapping body text; it was
  moved to the title row and included in the final build.

## Remaining scope

F03 is partial: automatic character-relative placement, both compositions and
large-font/landscape checks remain. F04 is partial: live streaming updates are wired,
but user-scroll preservation during an actively growing real reply and interruption
by real approval/listening have not been exercised in this increment. Neither
source inspection nor queue tests are substituted for those checks. C02/C04-C12 and
the complete C01 notification/scheduling capability mapping remain open.
No DSH plugin, deployment, identity or voice provider was changed.

Final installed APK SHA-256: 28d97e42aae3f6c065b5f76a759fccb5dcdadb05b3a3650c6cf030e0f5216ec9.
Final title-row close placement inspected on phone screenshot; temporary text-input
instrumentation uninstalled. Silent 8-second scroll/collapse demo:
artifacts/demos/20261024-235418-m45-reply-card-9803be2b.mp4.


The remaining-scope paragraph above describes 0.4.8, not current implementation.
Character-relative placement is implemented; 0.4.24 adds measured landscape admission
and source-specific expanded captions. Latest observations and remaining acceptance
are in [closure evidence](m45-closure-evidence.md).
