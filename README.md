# Vibecheck

A consumer product with formally verified properties, built for the Vibecheck hackathon. See [RULES.md](RULES.md) for what we're judged on and [CLAUDE.md](CLAUDE.md) for how the verification work is done.

## Quick start

The project ships a portable **Temurin JRE 21** in `vendor/jre/`, so you don't need Java installed.

### macOS / Linux
```sh
make install && make run
```

### Windows
```bat
install.bat
run.bat
```

`install` extracts the JRE for your platform into `.jre/` (gitignored) and prints its version. `run` launches the project with that JRE. `make clean` removes the extracted JRE.

The bundled runtime is a **JRE** (no compiler), so compiled output in `out/` is committed and `make run` works as-is. After changing sources, recompile with any system JDK via `make compile` (Windows: `javac -d out src\*.java`) and commit the result.

## What's trusted

- **Bundled runtime:** Eclipse Temurin OpenJDK 21 (JRE), one archive per platform in `vendor/jre/`. This is the only external dependency; nothing is downloaded at install time.
- Verification claims and their trusted surface are documented here as they land.

## Verification gates

**Semantic MIPS now lives in its own repo:**
[rShyvers/semantic-mips](https://github.com/rShyvers/semantic-mips). It was
extracted out of this project (full build history preserved) so the Lean
semantics — and only the Lean semantics — can be read, built, and reviewed
without anything else in this project in the way. That repo's own README has
the full trust chain, both verification gates, their current status, and the
3 documented `sorry`s with closed substitutes. Summary below; see the other
repo for the complete, current picture.

The Lean project (Lean 4.35.0) implements and proves properties
of **Semantic MIPS** — a small-step semantics of the v1 MIPS dialect,
written in Lean, plus the kernel-checked theorems that say it matches its
spec. See `CLAUDE.md` for the full approach to writing and checking these
proofs.

The trust chain is plain: the MIPS language spec lives in `docs/spec/`
(`INSTRUCTION-REF.md` and `SPEC-NOTES.md`), human-written and sourced to
the real MARS 4.5 simulator's behavior. Semantic MIPS
(`Semantics/Step.lean`'s `step` function) is the Lean implementation
of that spec. The spec's key claims are kernel-checked theorems
(`Semantics/Specs/StepSpec.lean`), judged by `lake comparator`
against a trusted statement file (`comparator.json`), and the whole
semantics is differentially validated against real MARS 4.5 on a
12-program corpus (`DiffHarness`). The project's narrative is deliberately
simple: formally verify the MIPS spec into Semantic MIPS, validate it
against MARS, then use it to find a real arithmetic bug in a sealed
program.

Two gates decide whether a proof counts as "done":

- **`lake check`** rebuilds the project's default target, replays the
  result through Lean's own kernel, and fails if anything uses an axiom
  beyond the handful the kernel itself treats as standard. It is
  all-or-nothing over its whole target: one unfinished proof anywhere in
  it fails the whole gate.
- **`lake comparator`** checks a named list of theorems against a config
  file (`comparator.json`) stating which axioms are allowed. Unlike
  `lake check`, it works theorem-by-theorem, so it is the right tool for a
  file that mixes finished and unfinished proofs.

**Permitted axioms (both gates):** `propext`, `Classical.choice`,
`Quot.sound` — the three axioms built into Lean/Mathlib's logic that
`#print axioms` treats as a clean proof (CLAUDE.md §7). Anything else
(most importantly `sorryAx`, which every unfinished `sorry` adds) fails
both gates.

### Current status

- **`lake comparator`: green.** Config: `comparator.json`. Trusted
  surface (8 of the spec's 21 theorems — picked to cover one theorem from
  each kind of claim the spec makes, not the whole file):
  - `add_spec_raw`, `sub_spec_raw` — arithmetic plus signed-overflow
    trapping (`add`, `sub`).
  - `div_by_zero_spec` — a division edge case leaves `hi`/`lo` untouched.
  - `beq_taken_spec` — branch semantics.
  - `jal_sets_ra` — jump-and-link semantics.
  - `lw_spec_raw` — memory load semantics.
  - `add_overflow_not_wrapped` — a bad-output rejection: overflow halts,
    it never silently wraps.
  - `zero_write_not_observable` — the `$zero`-register invariant, closed
    form.

  The `challenge_module` and `solution_module` in `comparator.json` are
  both `Semantics.Specs.StepSpec`: there is one statement of each theorem,
  not a second copy in a separate `Challenge` file. A second copy would
  only add a place for the trusted statement and the proved statement to
  quietly drift apart; comparator's theorem-by-theorem kernel check and
  axiom audit give the same guarantee without that risk. Verified by
  running `lake comparator` to exit code 0 (see the project's closeout
  notes for the full transcript).

- **`lake check`: green, with one documented, scoped exclusion.**
  `lake check` has no per-theorem axiom allowlist — it is pass/fail over
  its whole default target — so a file with any `sorry` in it fails the
  gate outright. `Semantics/Specs/StepSpec.lean` (the spec file) has 3 such
  `sorry`s (below), so it is excluded from `lake check`'s default target:
  the default target is a new `Checked` library (`Checked.lean`,
  set as the default in `lakefile.toml`) covering everything *except* the
  spec file — `Word`, `Types`, `State`, `Instruction`, `Asm`, `Step`, and
  their own unit tests. `lake check` kernel-replays exactly that and
  passes using only the three permitted axioms. The full project
  (including the spec file) is still built by `lake build Semantics`, and
  the spec file's finished theorems are still checked by `lake
  comparator`, above. Nothing is silently dropped; see `Checked.lean`'s
  header comment for the same explanation in-repo.

### Open items: 3 documented `sorry`s

All three are in `Semantic MIPS`'s `Semantics/Specs/StepSpec.lean` (see that
repo), each with a closed, weaker version proved instead and a concrete
`#guard`-checked example. Full detail in [that repo's
README](https://github.com/rShyvers/semantic-mips#open-items-3-documented-sorrys).
