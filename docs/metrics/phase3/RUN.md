# Phase III baseline measurement: run record

## Tool
| Item | Value |
|---|---|
| Tool | Lizard 1.26.0 (`pip install lizard==1.26.0`) |
| Runtime | Python 3.13.5 (Windows 11) |
| Date | 2026-11-26 |
| Measured commit | `07490cfe874f819d7c4e558a4d312e57f2cdef40` (branch `phase3-metrics`) |
| Tag `phase3` | `7fac6fc4be1e61ae139437b3212908dd59356409` |

## Configuration
Run from the repository root. The same command and options are used for every Phase IV measurement (phase3, phase4-refactored, phase4).

Text report:
```
python -m lizard backend/src/main/java frontend/src -l java -l javascript -l tsx -x "*.test.js" -x "*.test.jsx" -C 15 -Ecognitive -o docs/metrics/phase3/lizard-report.txt
```

Per-function CSV: the same command with `--csv` added.
```
python -m lizard backend/src/main/java frontend/src -l java -l javascript -l tsx -x "*.test.js" -x "*.test.jsx" -C 15 -Ecognitive --csv -o docs/metrics/phase3/lizard-functions.csv
```

Lizard writes the files itself (`-o`), with LF line endings, so the output is the same from any shell. Shell redirection (`>`) is not used, because Windows PowerShell 5.1 would write UTF-16.

| Option | Meaning |
|---|---|
| `backend/src/main/java frontend/src` | Production code only. Backend tests live in `backend/src/test` and are not scanned. |
| `-l java -l javascript -l tsx` | Java, `.js` files, and `.jsx` files. Lizard reads `.jsx` with its TSX reader; without `-l tsx` every React file is skipped. |
| `-x "*.test.js" -x "*.test.jsx"` | Excludes the frontend tests, which sit next to the code they test. |
| `-C 15` | Cyclomatic complexity warning threshold (Lizard default, stated explicitly). |
| `-Ecognitive` | Adds cognitive complexity (`CogC`). This is Lizard's implementation of cognitive complexity, not SonarQube's. With this extension, the warning list also includes functions with cognitive complexity above 15. |

## Measured commit vs. tag `phase3`
The measured commit differs from `phase3` only by two later commits (`15f1578` and its merge `07490cf`). Together they add 36 lines of documentation comments to five backend files and change no code:
`CourierService`, `DispatchBacklogFeed`, `DispatchQueueService`, `DispatchQueue`, `DispatchRule`.

Check: Lizard was run on those five files as they are at `phase3` and as they are on the measured commit. The function metrics (NLOC, CCN, tokens, parameters, CogC) for all 55 functions are identical, and so is the per-file summary. Only line positions differ.

## Files
| File | Content |
|---|---|
| `lizard-report.txt` | Raw text output: one row per function, the warning list, one row per file (including files with no functions), and the totals. |
| `lizard-functions.csv` | Raw CSV output, one row per function. It has no header row. The columns are described below. |
| `per-class.csv` | Derived, not raw. One row per class (file), built from the two raw files by `docs/metrics/per_class.py`. |
| `coupling.md` | Hand-counted class coupling of the five hotspots, with the counting rule and the full list of referenced types. |
| `RUN.md` | This record. |

## Derived per-class table
Lizard measures functions, not classes. In this code base each Java file holds one top-level class, so a file is treated as a class; for `.js` and `.jsx` files the file is the unit. Nested records and helper types in a Java file count toward its top-level class.

Regenerate it with:
```
python docs/metrics/per_class.py docs/metrics/phase3
```

| Column | Source |
|---|---|
| `class`, `file` | File name, and its path with `/` separators |
| `nloc` | Per-file NLOC from `lizard-report.txt` (excludes comments and blank lines; includes code outside functions) |
| `functions` | Per-file function count from `lizard-report.txt` |
| `ccn_total` | Sum of the CCN of the file's functions (weighted methods per class) |
| `ccn_max`, `ccn_avg` | Highest and mean CCN of a single function |
| `cogc_total`, `cogc_max` | Sum and highest cognitive complexity of the file's functions |

The script stops with an error if the function count in the text report and the CSV disagree for any file. For this run, the 162 rows add up to the report totals: 7522 NLOC and 822 functions.

`lizard-functions.csv` columns, in order:
`NLOC, CCN, token, PARAM, length, location, file, function, long_name, start_line, end_line, CogC`

## Notes
- The text report command exits with code 1. That is Lizard's signal that warnings exist (12 functions above a threshold), not an error.
- 15 files are Phase III architecture placeholders that contain only a package line or a comment (for example `MenuController.java`, `Handoff.java`, `Ledger.java`, `map_page.jsx`). They appear in the per-file section with 0 functions.
- Interfaces, records and enums without method bodies also appear with 0 functions.
- On Windows, paths in the output mix `/` and `\`. The raw files are kept as produced.

## Totals
| NLOC | Functions | Avg NLOC | Avg CCN | Avg CogC | Warnings |
|---|---|---|---|---|---|
| 7522 | 822 | 6.9 | 2.2 | 1.2 | 12 |
