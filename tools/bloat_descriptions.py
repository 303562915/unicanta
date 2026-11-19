#!/usr/bin/env python3
"""Build and maintain the translated descriptions of the UAD bloat list.

The app ships `files/bloat_descriptions.json`, a package -> description table. This script is the
only thing that writes it: it pulls the packages that still need a translation out of the UAD list,
and merges finished translations back in without touching what is already there.

    python tools/bloat_descriptions.py stats
    python tools/bloat_descriptions.py todo --level Unsafe --out todo.json
    python tools/bloat_descriptions.py merge --in translated.json
    python tools/bloat_descriptions.py check

`todo` prints the packages whose description is not in the table yet, one `{"pkg","en","level"}`
object per line (a JSON array with --out). `merge` takes `{"pkg": "译文"}` or a list of
`{"pkg","zh"}` objects and writes them into the table, refusing entries whose package is not in the
UAD list. Nothing here talks to the network: point --uad at a copy of `uad_lists.json`.
"""

from __future__ import annotations

import argparse
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
TABLE = ROOT / "composeApp/src/commonMain/composeResources/files/bloat_descriptions.json"
DEFAULT_UAD = ROOT / ".bloat-work/uad_lists.json"

# The order translations are worth writing in: a wrong call on an Unsafe package strands a phone,
# on a Recommended one it costs a Play Store download.
LEVEL_ORDER = ["Unsafe", "Expert", "Advanced", "Recommended"]


def load_uad(path: pathlib.Path) -> dict:
    with path.open(encoding="utf-8") as fh:
        return json.load(fh)


def load_table(path: pathlib.Path) -> dict:
    if not path.exists():
        return {"language": "zh", "version": 1, "updated": "", "source": "", "descriptions": {}}
    with path.open(encoding="utf-8") as fh:
        return json.load(fh)


def write_table(path: pathlib.Path, table: dict) -> None:
    table["descriptions"] = dict(sorted(table["descriptions"].items()))
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as fh:
        json.dump(table, fh, ensure_ascii=False, indent=1)
        fh.write("\n")


def needs_translation(entry: dict) -> bool:
    desc = (entry.get("description") or "").strip()
    # An English description that is already Chinese, or empty, is not worth a row.
    return bool(desc)


def cmd_stats(args: argparse.Namespace) -> int:
    uad = load_uad(args.uad)
    table = load_table(args.table)
    done = set(table["descriptions"])
    print(f"uad packages        {len(uad)}")
    print(f"translated          {len(done & set(uad))}")
    print(f"table entries       {len(table['descriptions'])}")
    for level in LEVEL_ORDER:
        total = sum(1 for e in uad.values() if (e.get("removal") or "") == level and needs_translation(e))
        done_level = sum(
            1
            for pkg, e in uad.items()
            if (e.get("removal") or "") == level and needs_translation(e) and pkg in done
        )
        pct = (done_level / total * 100) if total else 0.0
        print(f"  {level:<12} {done_level:>5}/{total:<5} {pct:5.1f}%")
    return 0


def cmd_todo(args: argparse.Namespace) -> int:
    uad = load_uad(args.uad)
    table = load_table(args.table)
    done = set(table["descriptions"])
    levels = args.level or LEVEL_ORDER
    rows = [
        {"pkg": pkg, "en": e["description"].strip(), "level": e.get("removal") or ""}
        for pkg, e in sorted(uad.items())
        if (e.get("removal") or "") in levels and needs_translation(e) and pkg not in done
    ]
    if args.limit:
        rows = rows[: args.limit]
    if args.out:
        args.out.write_text(json.dumps(rows, ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"wrote {len(rows)} entries to {args.out}")
    else:
        print(json.dumps(rows, ensure_ascii=False, indent=1))
    return 0


def cmd_merge(args: argparse.Namespace) -> int:
    uad = load_uad(args.uad)
    table = load_table(args.table)
    payload = json.loads(args.input.read_text(encoding="utf-8"))
    if isinstance(payload, list):
        pairs = {row["pkg"]: row["zh"] for row in payload if row.get("pkg") and row.get("zh")}
    else:
        pairs = {k: v for k, v in payload.items() if isinstance(v, str) and v.strip()}

    unknown = [pkg for pkg in pairs if pkg not in uad]
    if unknown:
        print(f"refusing {len(unknown)} packages that are not in the UAD list:", file=sys.stderr)
        for pkg in unknown[:10]:
            print(f"  {pkg}", file=sys.stderr)
        if not args.allow_unknown:
            return 1

    added = updated = 0
    for pkg, zh in pairs.items():
        if pkg in table["descriptions"]:
            if table["descriptions"][pkg] != zh:
                updated += 1
        else:
            added += 1
        table["descriptions"][pkg] = zh.strip()
    write_table(args.table, table)
    print(f"merged: {added} added, {updated} updated, {len(table['descriptions'])} total")
    return 0


def cmd_check(args: argparse.Namespace) -> int:
    uad = load_uad(args.uad)
    table = load_table(args.table)
    problems = 0
    for pkg, zh in table["descriptions"].items():
        if pkg not in uad:
            print(f"stale package: {pkg}")
            problems += 1
        if not zh.strip():
            print(f"empty translation: {pkg}")
            problems += 1
        original = ((uad.get(pkg) or {}).get("description") or "").strip()
        if zh.strip() == original:
            print(f"untranslated (identical to English): {pkg}")
            problems += 1
    print(f"{len(table['descriptions'])} entries, {problems} problems")
    return 1 if problems else 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--uad", type=pathlib.Path, default=DEFAULT_UAD)
    parser.add_argument("--table", type=pathlib.Path, default=TABLE)
    sub = parser.add_subparsers(dest="cmd", required=True)

    sub.add_parser("stats").set_defaults(func=cmd_stats)

    todo = sub.add_parser("todo")
    todo.add_argument("--level", action="append", choices=LEVEL_ORDER)
    todo.add_argument("--limit", type=int, default=0)
    todo.add_argument("--out", type=pathlib.Path)
    todo.set_defaults(func=cmd_todo)

    merge = sub.add_parser("merge")
    merge.add_argument("--in", dest="input", type=pathlib.Path, required=True)
    merge.add_argument("--allow-unknown", action="store_true")
    merge.set_defaults(func=cmd_merge)

    sub.add_parser("check").set_defaults(func=cmd_check)

    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
