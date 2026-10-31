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
2. **Act 2 — Unseal & detect.** The sealed CS47 program ("works" but has a math
   error deep in its logic) enters context only after gate G1. Detection via
   Lean: verify the program against its *functional intent* spec → counterexample;
   corroborate with differential runs. Pinpoint the faulty instruction(s).
3. **Act 3 — Fix & prove surgical.** Fix the assembly. Prove: fixed program ≡
   original on all inputs **except** the buggy behavior (program-level
   equivalence under our semantics — "the code has not migrated away").
4. **Act 4 — LLM optimize & prove.** Claude rewrites/optimizes the fixed MIPS
   program; we prove optimized ≡ fixed on all inputs. Benchmark if measurable.
5. **Act 5 — Demo.** `make run` (shape TBD, quiz Q2), narrative: verified
   semantics → real bug found by Lean → surgical fix proven → LLM optimization
   proven equivalent. Plus the MARS lw/sw sign-extension finding as a bonus.

## Cleanroom protocol (BINDING)

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

## Build state

| Wave | Modules | Status |
|---|---|---|
| 0 | Word.lean, Types.lean (foundation) | ✅ merged, tested |
| 1 | State.lean ∥ Instruction.lean (41 variants = full v1 set + syscall) ∥ Asm.lean (40 instrs + li/la/move/nop) | ✅ merged, tested, green on main |
| 2 | Step.lean (+ Sim.run loop), single writer, high thinking | ⏳ next — brief must note: decode knows all v1 variants; implement exactly the v1 set per SPEC-NOTES §6 (which IS the full 40+syscall); raw immediates need signExt16/zeroExt16 per INSTRUCTION-REF; HashMap ops are cbv_opaque → use #guard for stateful checks |
| 3 | DiffHarness (exe: Sim vs spawned MARS) ∥ Specs layer (spec S statements, satisfiable examples, bad-output rejections) | ⏳ after Wave 2 |
| — | Comparator file + `lake check`/comparator gate | with Act 1 closeout |

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

- Remote: github.com/rShyvers/VibeCheck (private for now).
- Every worker branch: review by orchestrator → push branch → open PR →
  GitHub Actions (`ci.yml`: full `lake build` + `make install && make run`
  smoke on fresh checkout) green → merge to main.
- Lean toolchain pinned in MipsSim/lean-toolchain; CI fetches the mathlib
  build cache (`lake exe cache get`) — no source builds of mathlib anywhere.

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
