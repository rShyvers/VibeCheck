# Vibecheck submission — install & run (macOS / Linux)
# Windows: use install.bat and run.bat instead.
#
# The project ships a portable Temurin JRE 21 in vendor/jre/, so no system
# Java is required. `make install` extracts the archive for your platform
# into .jre/; `make run` uses that JRE to run the project.

JRE_DIR := .jre
VENDOR  := vendor/jre
JAVA    := $(JRE_DIR)/bin/java

UNAME_S := $(shell uname -s)
UNAME_M := $(shell uname -m)

# Map (OS, arch) -> bundled JRE archive. Add more lines to support more platforms.
ifeq ($(UNAME_S),Linux)
  ifeq ($(UNAME_M),x86_64)
    ARCHIVE := $(VENDOR)/linux-x64.tar.gz
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

.PHONY: install run clean

install: $(JAVA)
	@echo "Bundled JRE ready:"
	@$(JAVA) -version 2>&1 | sed 's/^/    /'

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
compile:
	@command -v javac > /dev/null || { echo "error: 'javac' not found. Install a JDK (e.g. 'brew install openjdk' or 'apt install openjdk-21-jdk') to recompile."; exit 1; }
	javac -d out $(wildcard src/*.java)

run: install
	$(JAVA) -cp out Main

clean:
	rm -rf $(JRE_DIR)
