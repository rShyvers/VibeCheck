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

## Open decisions (quiz)

- **Q2** Demo shape of `make run`: (a) polished batch CLI + trace [floor] / (b) REPL-TUI / (c) Java GUI shell. Framing: "verified MIPS simulator that found a bug in MARS" vs consumer-shaped?
- **Q3-refined** LLM optimization now targets the **MIPS program** (Act 4). Confirm: our documented Claude worker is "the LLM"; Step itself gets an optional optimization pass only if time allows.
- **Q4** Versioning: git tags `v1-buggy` → `v2-fixed` → `v3-optimized` on one branch (proposed) vs branches/copies?
- **Q5** Deadline + demo venue (this box / Mac / judge machine)? Cut order if tight: TUI/GUI → MARS-bug divergence demo → benchmark polish.
- **Program intent:** user to describe, in their own words, what the CS47 program is supposed to compute (needed to write its functional spec at unseal time; cleanroom-safe).

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
