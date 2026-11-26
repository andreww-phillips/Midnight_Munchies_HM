import csv
import sys
from pathlib import Path


def normalize(path):
    return path.strip().replace("\\", "/")


def read_files(report):
    lines = report.read_text(encoding="utf-8").splitlines()
    start = next(i for i, line in enumerate(lines) if line.strip().endswith("function_cnt    file"))
    files = {}
    for line in lines[start + 2:]:
        if line.startswith("="):
            break
        parts = line.split()
        if len(parts) < 7:
            continue
        files[normalize(" ".join(parts[6:]))] = {"nloc": int(parts[0]), "functions": int(parts[5])}
    return files


def read_functions(csv_path):
    functions = {}
    with csv_path.open(encoding="utf-8", newline="") as handle:
        for row in csv.reader(handle):
            entry = functions.setdefault(normalize(row[6]), [])
            entry.append({"ccn": int(row[1]), "cogc": int(row[-1])})
    return functions


def unit_name(path):
    name = Path(path).name
    return name.rsplit(".", 1)[0] if name.endswith(".java") else name


def main(folder):
    folder = Path(folder)
    files = read_files(folder / "lizard-report.txt")
    functions = read_functions(folder / "lizard-functions.csv")
    rows = []
    for path, summary in files.items():
        measured = functions.get(path, [])
        if len(measured) != summary["functions"]:
            raise SystemExit(f"function count mismatch for {path}")
        ccn = [f["ccn"] for f in measured]
        cogc = [f["cogc"] for f in measured]
        rows.append({
            "class": unit_name(path),
            "file": path,
            "nloc": summary["nloc"],
            "functions": summary["functions"],
            "ccn_total": sum(ccn),
            "ccn_max": max(ccn, default=0),
            "ccn_avg": round(sum(ccn) / len(ccn), 2) if ccn else 0,
            "cogc_total": sum(cogc),
            "cogc_max": max(cogc, default=0),
        })
    rows.sort(key=lambda r: (-r["nloc"], r["file"]))
    out = folder / "per-class.csv"
    with out.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    print(f"{len(rows)} files written to {out}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "docs/metrics/phase3")
