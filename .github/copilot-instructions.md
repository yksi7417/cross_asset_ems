# Copilot instructions — Cross-Asset EMS

## Formatting is enforced by CI — you MUST match it

This repo runs **Spotless** as a hard PR gate (`.github/workflows/ci.yml` →
`./gradlew spotlessCheck`). A PR that is not formatted fails CI. The formatter is:

- **google-java-format 1.24.0** (default / non-AOSP style: 2-space indent, 100-col
  reflow, import ordering) — configured in
  `build-logic/src/main/kotlin/ems.java-conventions.gradle.kts`.
- plus `removeUnusedImports`, `formatAnnotations`, and `endWithNewline`.
- applies to `src/**/*.java`; **excludes** anything under `**/generated/**`.

### Before you finish any change that touches `*.java`

Run the formatter and include its result in your commit:

```bash
./gradlew --no-daemon spotlessApply     # rewrites files to the required style
./gradlew --no-daemon spotlessCheck     # must exit 0 — this is what CI runs
```

If you cannot run Gradle in your environment, then hand-format to match
google-java-format exactly:

- 2-space indentation, never tabs.
- 100-column line limit; wrap long calls/params one-per-line, continuation
  indented 4 spaces.
- No unused imports; imports in a single ASCII-sorted block (no manual grouping).
- Every file ends with exactly one trailing newline.
- Do not reformat or touch generated sources under `**/generated/**`.

### Known local flake (not a real failure)

If `spotlessApply`/`spotlessCheck` errors with *"Spotless JVM-local cache is
stale … regenerate with `rm -rf .gradle/configuration-cache`"* (diffplug/spotless
#987), run `rm -rf .gradle/configuration-cache` and retry — it is an environment
artifact, not a formatting problem in your code.

## Build & test gate (also CI-enforced)

```bash
./gradlew --no-daemon assemble    # compiles (-Werror)
./gradlew --no-daemon allTests    # full unit suite
```

Keep both green. Do not weaken a test to make it pass; fix the code.
