# Vibecheck submission — install & run (macOS / Linux)
# Windows: use install.bat and run.bat instead.
#
# The project ships a portable Temurin JRE 21 in vendor/jre/, so no system
# Java is required. `make install` extracts the archive for your platform
# into .jre/; `make run` uses that JRE to run the project.

JRE_DIR       := .jre
VENDOR        := vendor/jre
JAVA          := $(JRE_DIR)/bin/java
SEMANTICS     := semantic-mips
MIPSRUN_DIR   := .mipsrun
MIPSRUN_VENDOR := vendor/mipsrun
MIPSRUN_BIN   := $(MIPSRUN_DIR)/MipsRun

UNAME_S := $(shell uname -s)
UNAME_M := $(shell uname -m)

# Map (OS, arch) -> bundled JRE archive. Add more lines to support more platforms.
ifeq ($(UNAME_S),Linux)
  ifeq ($(UNAME_M),x86_64)
    ARCHIVE := $(VENDOR)/linux-x64.tar.gz
    MIPSRUN_SRC := $(MIPSRUN_VENDOR)/linux-x64/MipsRun
  else ifeq ($(UNAME_M),aarch64)
    ARCHIVE := $(VENDOR)/linux-arm64.tar.gz
  endif
else ifeq ($(UNAME_S),Darwin)
  ifeq ($(UNAME_M),arm64)
    ARCHIVE := $(VENDOR)/darwin-arm64.tar.gz
  else ifeq ($(UNAME_M),x86_64)
    ARCHIVE := $(VENDOR)/darwin-x64.tar.gz
  endif
endif

# Semantic MIPS is a git submodule pinned to a specific commit (see README).
# Fetch it on install so a fresh clone satisfies RULES.md without the cloner
# needing to know about submodules. No-op when already checked out.
.PHONY: install run clean submodules build-semantics mipsrun-vendored

submodules:
	@if test -d .git; then git submodule update --init --recursive; else echo "not a git checkout; skipping submodule fetch"; fi

# Semantic MIPS's MipsRun interpreter (the Run-code/Tic-Tac-Toe tabs' real
# backend) is vendored pre-built, same idea as the bundled JRE: a stripped,
# statically-linked-against-Lean binary needing only glibc at runtime, no
# Lean toolchain. Currently vendored for linux-x64 only (see README); other
# platforms fall back to `make build-semantics` (needs elan) or the Run-code
# tab's honest "interpreter not built" state -- install never fails over this.
install: submodules $(JAVA) mipsrun-vendored
	@echo "Bundled JRE ready:"
	@$(JAVA) -version 2>&1 | sed 's/^/    /'

mipsrun-vendored:
	@if [ -n "$(MIPSRUN_SRC)" ] && [ -f "$(MIPSRUN_SRC)" ]; then \
	  mkdir -p $(MIPSRUN_DIR); \
	  cp "$(MIPSRUN_SRC)" $(MIPSRUN_BIN); \
	  chmod +x $(MIPSRUN_BIN); \
	  echo "MipsRun interpreter ready (vendored, $(UNAME_S)/$(UNAME_M)): $(MIPSRUN_BIN)"; \
	 else \
	  echo "No vendored MipsRun for $(UNAME_S)/$(UNAME_M) -- Run code/Tic-Tac-Toe tabs will say"; \
	  echo "'interpreter not built' until you run 'make build-semantics' (needs elan/lake)."; \
	 fi

$(JAVA): $(ARCHIVE)
	@test -n "$(ARCHIVE)" || { echo "error: no bundled JRE for $(UNAME_S)/$(UNAME_M). Download the Temurin 21 JRE from https://adoptium.net/temurin/releases/, save it as a new file in $(VENDOR)/, and add a mapping line to this Makefile."; exit 1; }
	@test -f "$(ARCHIVE)" || { echo "error: $(ARCHIVE) is missing from the repo (incomplete clone?)."; exit 1; }
	rm -rf $(JRE_DIR)
	mkdir -p $(JRE_DIR)
	tar xzf $(ARCHIVE) -C $(JRE_DIR) --strip-components=1

# Build output in out/ is committed, so `run` needs only the JRE (the bundled
# runtime has no compiler). Recompile after changing sources with a system JDK:
#
#     make compile
# --release 21: any modern JDK produces classes the bundled JRE 21 can run.
compile:
	@command -v javac > /dev/null || { echo "error: 'javac' not found. Install a JDK (e.g. 'brew install openjdk' or 'apt install openjdk-21-jdk') to recompile."; exit 1; }
	javac --release 21 -d out $(wildcard src/*.java)

run: install
	$(JAVA) -cp out Main

# Builds the Lean `MipsRun` interpreter that powers the "Run code" view
# (POST /run). Needs the Lean toolchain (elan); the first build also fetches
# Mathlib dependencies, so expect it to take a while. `make run` works without it.
# Builds the MipsRun interpreter from the pinned semantics. `cache get` pulls
# the prebuilt mathlib artifacts first — building mathlib from source takes
# hours, so never skip it (CLAUDE.md §2).
build-semantics: submodules
	@command -v lake > /dev/null || { echo "error: 'lake' not found. Install the Lean toolchain manager elan (https://github.com/leanprover/elan), then re-run 'make build-semantics'."; exit 1; }
	cd $(SEMANTICS) && lake exe cache get && lake build MipsRun

clean:
	rm -rf $(JRE_DIR) $(MIPSRUN_DIR)
