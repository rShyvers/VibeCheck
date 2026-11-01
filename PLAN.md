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

## 27-hour battle plan (from ~12:30 Oct 31)

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
