# VibeCheck

A small-step semantics of MIPS, written and proven in Lean 4 — 227 kernel-checked theorems, differentially validated against real MARS 4.5, Comparator-clean — used to catch two real planted arithmetic bugs, and to run a playable Tic-Tac-Toe where every move, win, and block is decided by that same verified machine, not JavaScript.

```sh
git clone --recurse-submodules https://github.com/rShyvers/VibeCheck
cd VibeCheck && make install && make run
```

No Lean toolchain needed to try it — the verified interpreter ships prebuilt.

- **Product:** [github.com/rShyvers/VibeCheck](https://github.com/rShyvers/VibeCheck)
- **Semantics:** [github.com/rShyvers/semantic-mips](https://github.com/rShyvers/semantic-mips)
