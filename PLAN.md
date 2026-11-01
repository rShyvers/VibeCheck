# VibeCheck — project plan (living document)

Reference for all worker briefs. Owner: orchestrator. Update on every decision.

## The story (what we're building and why it wins)

We build **small-step semantics of the MIPS language in Lean 4**, prove it
against a written spec, validate it differentially against MARS (a test
environment, not the spec), then use it to **find, fix, and formally verify a
real math bug in a real MIPS program** — and finally have an LLM optimize that
program with a proof that behavior is unchanged.

1. **Act 1 — Build & prove the semantics.** `step` for the v1 instruction set
   (40 instructions + syscall), proven against spec S (the MIPS language,
   educational dialect per SPEC-NOTES §9). Differential suite vs MARS on an
   *independent* corpus (our own test programs — never the sealed asset).
2. **Act 2 — Unseal & detect. DEFERRED until Semantic MIPS v2 (assembler
   macros), see note below.** The plan is unchanged: the sealed CS47 program ("works" but has a math error deep in its logic)
   enters context only after gate G1; detect via Lean (functional-intent
   spec → counterexample), corroborate with differential runs, pinpoint the
   faulty instruction(s).
3. **Act 3 — Fix & prove surgical.** Fix the assembly. Prove: fixed program ≡
   original on all inputs **except** the buggy behavior (program-level
   equivalence under our semantics — "the code has not migrated away").
4. **Act 4 — LLM optimize & prove.** Claude rewrites/optimizes the fixed MIPS
   program; we prove optimized ≡ fixed on all inputs. Benchmark if measurable.
5. **Act 5 — Demo.** `make run` (shape TBD, quiz Q2), narrative: verified
   semantics → real bug found by Lean → surgical fix proven → LLM optimization
   proven equivalent. Plus the MARS lw/sw sign-extension finding as a bonus.

## Cleanroom protocol (BINDING)

Kept verbatim. Unseal is deferred until v2 per the note below; nothing in it
is rescinded — the asset stays sealed until then.

- **SEALED asset:** `https://github.com/rShyvers/CS47-Final-Project` — the CS47
  final-project MIPS program(s). Contains the real math bug; it is the Act 2
  target.
- **Rule: nothing from that repo (code, filenames, READMEs, history) enters any
  context — orchestrator or worker — before gate G1 passes.** No cloning, no
  browsing, no file listings. Worker briefs must not reference it until unsealed.
- **Unseal gate G1:** (a) `step` complete and `spec_holds step` proven for the v1
  set; (b) differential suite vs MARS green on the independent corpus; (c)
  comparator file passes. Only then: clone to
  `/home/sharkbark/Desktop/VibeCheck-sealed/` (OUTSIDE the project repo so it
  can never leak into worker checkouts or commits).
- **Functional intent** of the sealed program is spec-level information and may
  be provided by the user in their own words at any time (see Open items).

### CS47 bug hunt: deferred to v2, with an interim equivalence corpus

CS47 needs assembler macros to even assemble, and `docs/spec/SPEC-NOTES.md`
§9 puts macros explicitly out of v1 scope — `Asm.lean` cannot assemble it at
all, so Act 2 as planned is not possible against the v1 semantics. **Act 2 is
deferred, not dropped: it runs once Semantic MIPS v2 implements assembler
macros** (the cleanroom protocol above stays binding until then). Act 3's
"prove a fix" framing is deferred with it.

In the meantime, [rShyvers/semantic-mips](https://github.com/rShyvers/semantic-mips)
now has `tests/equiv/` + `EquivHarness.lean`: 7 equivalence claims across 12
self-written `.asm` programs, each pairing a direct arithmetic instruction
(`mult`, `div`, `sub`) against a bit-shift/bitwise equivalent (the kind of
strength-reduction a compiler would do). Two of the seven are deliberately
wrong (the classic divide/mod-by-power-of-two-via-raw-shift bug, which
breaks on negative dividends), so the corpus still has to catch a real
divergence, not just confirm agreement everywhere — the same spirit as the
CS47 bug hunt, self-contained. `EquivHarness` is a "thin interpreter": it
runs both sides of each pair directly through `assemble`/`run`, no MARS, no
JVM, no per-case compilation, since equivalence between two
already-validated programs is a question internal to the semantics, not one
that needs an external oracle. All 7 cases were hand-written, then
independently validated by 5 parallel subagents (one per arithmetic pair,
each in its own git worktree) before `EquivHarness.lean` was written as the
permanent, CI-running version of that same check. See that repo's README
("Equivalence corpus") for the full case table.

Acts 4–5 (LLM optimization + demo) still stand in the interim, applied to
this equivalence corpus's "correct" variants; if v2 lands before the
deadline, Acts 2–3 run against CS47 as originally planned and the corpus
stays as supporting evidence.

## Build state

The Lean implementation (`step` + its proven spec) is referred to as
**Semantic MIPS** elsewhere in the docs (see root README "Verification
gates"); this table tracks its build waves. As of the cleanup pass,
Semantic MIPS lives in its own repo, [rShyvers/semantic-mips]
(https://github.com/rShyvers/semantic-mips) — extracted out of this one,
history preserved, so it can be read and reviewed as just the semantics,
with no VibeCheck-specific scaffolding around it. This table is kept here
as the historical build log; current status lives in that repo's README.

| Wave | Modules | Status |
|---|---|---|
| 0 | Word.lean, Types.lean (foundation) | ✅ merged, tested |
| 1 | State.lean ∥ Instruction.lean (41 variants = full v1 set + syscall) ∥ Asm.lean (40 instrs + li/la/move/nop) | ✅ merged, tested, green on main |
| 2 | Step.lean (+ Sim.run loop) | ✅ merged PR #1 + foundation fix a6d63ae (syscall mnemonic in Asm, syscall-1 no-newline, SPEC-NOTES endianness correction) |
| 3a | DiffHarness (exe: Sim vs spawned MARS) | ✅ merged PR #2 (12/12 corpus PASS vs real MARS, independently re-run) |
| 3b | Specs layer (spec S statements, satisfiable examples, bad-output rejections) | ✅ merged PR #3 (21 closed theorems, 3 documented sorries with closed weaker substitutes) |
| — | Comparator file + `lake check`/comparator gate | ✅ `lake comparator` exits 0 on an 8-theorem trusted surface (`comparator.json`, axioms propext/Classical.choice/Quot.sound); `lake check` exits 0 on a new sorry-free `Checked` default target (`Checked.lean`) that excludes `StepSpec`'s 3 documented sorries by design — see README "Verification gates" |

Known assembler constraint for the diff corpus: `la` only in `la $rt, label` form.

### VibeCheck-side product (Act 5 demo, built ahead of schedule)

- **Verdict view**: `make run` opens a two-tab page served by the bundled JRE.
  **Verdict** tab renders gate status / MARS agreement / equivalence claims
  from a JSON contract (`web/sample-verdict.json` until a real harness run
  writes `out/verdict.json`). **Run code** tab lets a user paste MIPS
  assembly and run it on the real semantics.
- **Run code is live against real Semantic MIPS**, not a stub: semantic-mips
  shipped `MipsRun` (their issue #1) on `feature/mips-run` (PR #3, open);
  submodule pinned to that commit (`abf580d`); verified end to end through
  our own `/run` endpoint with a live browser render (register grid showing
  the computation, correct halt/output). Re-pin to their `main` once that PR
  merges.
- **Known limitation, carried from their side**: `MipsRun` wraps
  `assemble`/`initialState`/`run` only, so `.data` is never materialized into
  memory — `la`+`syscall 4` string-printing programs read zeros. Flagged
  in-UI; our bundled sample program avoids it (registers + `syscall 1`/`11`
  only).
- **Slide deck**: `slides/index.html`, 8 slides, zero dependencies, led with
  what Semantic MIPS actually models/proves (real `step` code, real theorem
  names) rather than our build process.

- **Tic-Tac-Toe tab**: a third tab where the ENTIRE rules engine (move
  validation, win/draw detection, opponent strategy) is a real MIPS program
  executed through the unchanged `/run` endpoint — browser holds only the
  board, no game logic in JS. Discovered along the way: the v1 ISA pinned at
  `abf580d` has **no `jr`/`jalr`** — `jal` sets `$ra` but nothing can ever
  jump back to it, so subroutine call/return is impossible. Worked around
  with a bitboard design (one register, 2 bits/cell, 16 straight-line
  `and`+`beq` win-mask checks, no memory, no loops) — a technique found by
  researching existing GitHub MIPS tic-tac-toe implementations (none were
  directly usable: all relied on `jr`, interactive syscalls, or `.data`).
  Exhaustively tested against the real binary: 34,529 board/move pairs + all
  3^9 boards + 775 full games, zero failures; mutation-tested (9 broken
  variants, all caught). Known, disclosed-in-UI limitation: the opponent has
  no fork detection (12/489 games end in a human win).

### Upstream update (semantic-mips, Nov 1 evening): jr/jalr and much more real MIPS32 coverage landed

Checked directly against their repo: **`main` moved substantially past our
pin.** PRs #4–#8 (all merged) added, on top of the v2 ISA expansion already
on `main`: **`jr`/`jalr`** (PR #6, `dev/v3`, commit `85adc84` — the exact gap
above), plus `mul`/variable shifts (`sllv`/`srlv`/`srav`), `ll`/`sc`,
REGIMM branches (`bltz`/`bgez(al)`), `madd`/`maddu`/`msub`/`msubu`/`mthi`/
`mtlo`, immediate-operand traps, MIPS32r2 rotate/bitfield instructions,
unaligned `lwl`/`lwr`/`swl`/`swr`, and FPU rounding/compare instructions —
all green on their own gates (`lake check`, `lake comparator`, DiffHarness,
EquivHarness), no new `sorry`s. Issue #1 (`MipsRun`) is now **closed**.

**Still missing anywhere in their history: assembler macros.** That remains
the one specific, unresolved blocker for the CS47 unseal (PLAN.md's
cleanroom protocol above) — everything else that was "out of v1 scope" six
hours ago now exists.

### Additional game candidates researched (Nov 1): Connect Four noted, deferred

Researched what else could run on this architecture, now scoped against
what's actually on semantic-mips' `main` (not our pin). Findings and
decision:

- **Tic-Tac-Toe stays as shipped, unchanged.** No rewrite, no re-pin.
- **Connect Four (Tromp's 49-bit bitboard encoding)** is a real, well-scoped
  candidate for later: 7x4-direction win-check via bitwise shift+mask
  (same style as Tic-Tac-Toe's mask checks, just four shift distances
  instead of eight fixed lines), recognizable, visually a step up.
  Estimated ~3-4h port effort. **Requires `jr`/`jalr` and real branch delay
  slots** (both landed on their `main`, see the re-pin review below) and,
  separately, a full rewrite of *all* branch-based game logic for delay-slot
  timing — the same cost that blocks any re-pin today. Not started; revisit
  only alongside a deliberate decision to re-pin and redo Tic-Tac-Toe's
  engine for delay slots at the same time, not before.
- Nim (Bouton's XOR theorem) was also researched as a lower-risk near-term
  option — notably, it needs **no** re-pin (the move logic is straight-line,
  no subroutines required) and has a genuinely clean, provable property.
  Not pursued this round (not asked for); noted here in case it's picked up
  later.

### Re-pin review (Nov 1, later): checked directly, decided against for now

Since the note above, `main` gained a second wave: PR #9 ("32 -> 116
StepSpec theorems") and PR #10 ("116 -> 145 theorems") proved nearly every
previously-implemented-but-unspecified instruction family, closed
`lwl`/`lwr`/`swl`/`swr` fully, and caught + fixed a real false theorem
statement (`syscall1_spec` was missing a `pendingBranch = none` hypothesis).
`jr`/`jalr` now have kernel-checked spec theorems, not just implementations.
4 documented `sorry`s (1 new, narrow, fully explained), same spirit as
before. All independently re-verified by us, not just read from PR text:
built their `main` ourselves, ran `lake check`/`lake comparator` (both
green, same 8-theorem trusted surface), `DiffHarness` (12/12),
`EquivHarness` (7/7).

**Then checked the actual re-pin question with hard evidence, not
speculation.** Built `MipsRun` from `main` and ran every program this repo
ships against it:
- Hello world, both bug-example programs (no branches): identical, correct
  output on both pins.
- **Tic-Tac-Toe's full test suite against the new binary: 18,356 failures**
  out of ~70,000 checks. The win-mask `beq` chains are corrupted by the now-
  mandatory delay slot (the instruction after every branch now always
  executes too, which the straight-line mask-check code never accounted
  for) — e.g. placing X in an empty cell wrongly reports an immediate win.

**Decision: not re-pinning.** The new semantics and proof coverage are
genuinely better, but the only upstream win-condition improvement available
to us from this (a stronger, delay-slot-aware opponent) requires rewriting
Tic-Tac-Toe's engine from scratch and re-earning the same exhaustive-testing
rigor it already passed once, under whatever time is left. Holding at
`abf580d` keeps the shipped, tested, honestly-caveated game working. Revisit
if there's ever clearly enough runway to redo that work properly — not a
casual bump.

## Decisions (locked 10/31)

- **Deadline: 6 PM, Nov 1 (~27 h).** Venue n/a — we build the demo + a slide deck.
- **CS47 program:** user's own hand-written MIPS project, **6 files, 2 of which are
  function test files** (the other 4: presumably main/entry, function implementations,
  data/support). Goal at unseal: run it through our semantics, prove what we can, and
  find the arithmetic error (location unknown to us — that's the point).
  **Act 2 strategy:** write a functional spec per function (its mathematical intent,
  from user description + repo docs — not from reading the implementation first);
  verify each function under our semantics → counterexample localizes the faulty
  file/instruction; the 2 test files double as executable cross-checks (run them in
  both our sim and MARS, compare).
- **Demo direction (Act 5):** small **web-based verdict view** of Lean4 results —
  parse run/verification output (exit code, program output, proof/bug verdict)
  and display it so bug-freeness is visible at a glance. Defer building until the
  bug story works; keep it minimal (structured output from the Lean binary + tiny
  single-page frontend). Not the CLI simulator itself.
- **Versioning (v1-buggy/v2-fixed/v3-optimized):** deferred — decide when we reach Act 2/3.
- **LLM optimization (Act 4):** targets the MIPS program; our documented Claude worker
  is "the LLM". Stretch goal given the deadline.

## Schedule status (updated ~9:15 PM Oct 31 — ~8h45m elapsed, ~21h to deadline)

The original battle plan below (kept for the historical record) assumed we'd
do the CS47 unseal and Act 2/3 ourselves on this timeline. That changed:
Semantic MIPS is now a separate team on their own repo/schedule, and Act 2/3
is deferred until their v2 ISA work lands macro support — not on our
critical path. Our actual critical path became the Act 5 demo product, and
it's done well ahead of the original "morning Nov 1" slot:

| Item | Status |
|---|---|
| Act 1 (Semantic MIPS v1: step + proofs + gates + diff corpus) | ✅ done, extracted to its own repo |
| Verdict view (two-tab page, `make run`) | ✅ shipped |
| Interactive Run-code tab | ✅ shipped, **verified live against real Semantic MIPS** (not a stub) — semantic-mips shipped `MipsRun`, pinned at `abf580d` on their open PR #3 |
| Slide deck (8 slides, zero deps) | ✅ shipped, leads with what Semantic MIPS proves |
| Act 2/3 (CS47 unseal + fix proof) | ⏸ deferred to v2 (their side; v2 ISA expansion — delay slots/FPU/exceptions — in progress on PR #2) |
| Act 4 (LLM optimization + equivalence proof) | Not started; depends on Act 2/3 landing first; stretch per the original decision below |

**Remaining-time plan, roughly budgeted against the ~21h left:**

| Budget | Work |
|---|---|
| now | Canned bug-example picker in the Run-code tab (load the equivalence corpus's two planted-bug programs so a judge can run the actual counterexample themselves) |
| ongoing, low-effort | Re-pin `semantic-mips` to their `main` once PR #3 merges; watch PR #2 (v2 ISA) for the same |
| as time allows | Real (non-headless) browser check of the full demo on this machine; refresh this table's "done" column as work lands |
| if Act 2/3 lands with hours to spare | Act 4 becomes possible again — revisit then, not before |
| final hours | Rehearse the demo narrative end to end against the slide deck; no new features this late |

**Cut lines still stand as written below** (Act 3 proof deadline, Act 4 demotion,
web view floor, slide count) — they just apply to whichever team/timeline
Acts 2–4 actually land on now.

### Original 27-hour battle plan (historical; see status table above for what actually happened)

| Window | Work | Parallelism |
|---|---|---|
| now → +3 h | Wave 2 Step lands → review → PR → merge | Claude worker (running) |
| +3 → +7 h | Wave 3: DiffHarness (vs MARS, independent corpus) ∥ Specs layer (spec S theorems: invariants + per-instruction lemmas + satisfiable examples + bad-output rejections) | 2 workers, worktrees |
| +7 → +9 h | Act 1 closeout: comparator file + `lake check`/comparator gate; diff corpus green ⇒ **G1** | orchestrator |
| +9 h → evening | **Unseal + Act 2 bug hunt**: clone sealed asset outside repo, functional spec for the program, verify in Lean → counterexample → pinpoint arithmetic error | worker + orchestrator |
| evening → morning | Act 3: fix assembly, prove fixed ≡ original except buggy behavior (program-level equivalence under our semantics) | worker, high thinking |
| morning Nov 1 | Act 4 (stretch): LLM-optimizes program + equivalence proof; web verdict view (minimal); slide deck | workers ∥ orchestrator |

**Cut lines (honest-shipping rules):**
- If Act 3's proof isn't done by ~noon Nov 1: ship with fix **differentially validated**
  (fixed program matches expected outputs on the full test matrix) and say so plainly;
  keep the proof attempt as appendix material.
- Act 4 demotes to "demonstrated on one subroutine" or is cut entirely; the verified-
  semantics + real-bug-found story stands without it.
- Web view floor: static page rendering the JSON verdict of the final program run.
- Slides: 6–8 max, built from PLAN.md + commit history (the git log IS the narrative).

## CI & merge flow

- Remote: github.com/rShyvers/VibeCheck. Semantic MIPS' own remote:
  github.com/rShyvers/semantic-mips — **public**, so the submodule fetches
  with no credentials on a fresh clone.
- **Semantic MIPS is embedded as a git submodule** at `semantic-mips/`, pinned
  to a specific commit (see README "Semantic MIPS (submodule)"). The pin is
  bumped deliberately when the other team ships something we need — never by
  tracking a branch. `make install` fetches it, so fresh-clone runs work.
- Every worker branch: review by orchestrator → push branch → open PR →
  GitHub Actions green → merge to main. In this (VibeCheck) repo, `ci.yml`
  now just runs the `make install && make run` smoke test; the Lean build +
  both verification gates run in semantic-mips's own CI.
- Lean toolchain pinned in semantic-mips's lean-toolchain; its CI fetches
  the mathlib build cache (`lake exe cache get`) — no source builds of
  mathlib anywhere.

## Conventions (binding on all workers)

- CLAUDE.md governs: no sorry/axiom, specs in their own files, #spec_surface /
  axiom audits per main theorem, Comparator gate before "final".
- Lake 5: `lake build [targets]` only (no `lake add`, no `lake exe build`);
  binaries in `.lake/build/bin/`. BitVec is Lean core; Std.HashMap via
  `import Std.Data.HashMap.Basic` (∅ for empty). HashMap ops don't kernel-reduce
  → `#guard` for stateful checks.
- One writer per checkout; parallel work only in managed git worktrees.
- Foundation (Word/Types) read-only since Wave 0; contract changes go through
  the orchestrator + DESIGN.md.
