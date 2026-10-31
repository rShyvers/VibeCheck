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
