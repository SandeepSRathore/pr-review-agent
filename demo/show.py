"""Pretty-prints a ReviewReport JSON file for the terminal demo."""
import json
import sys
import textwrap

path, label = sys.argv[1], sys.argv[2]
report = json.load(open(path))

print(f"\n{'=' * 100}\n{label}\n{'=' * 100}")
if "findings" not in report:
    print(json.dumps(report, indent=2))
    sys.exit()

s = report["stats"]
print(f"model {s['model']} | prompt v{s['promptVersion']} | {s['filesReviewed']} file(s), {s['batches']} batch(es) | "
      f"{s['inputTokens']} in / {s['outputTokens']} out tokens | {s['durationMs'] / 1000:.0f}s")

findings = report["findings"]
print(f"\nFINDINGS ({len(findings)})" if findings else "\nFINDINGS: none - nothing worth a comment")
for f in findings:
    where = f"{f['file'].rsplit('/', 1)[-1]}:{f['startLine']}" + (f"-{f['endLine']}" if f['endLine'] != f['startLine'] else "")
    fix = "  [has suggested fix]" if f.get("suggestedFix") else ""
    print(f"\n  {f['severity']:<8} {f['category']:<19} {where:<26} conf {f['confidence']:.2f}{fix}")
    print(f"  {f['title']}")
    for line in textwrap.wrap(f["rationale"], 94):
        print(f"      {line}")

if report["rejected"]:
    print(f"\nDROPPED BY VALIDATOR ({len(report['rejected'])})")
    for r in report["rejected"]:
        print(f"  - {r['finding']['title'][:60]!r}: {r['reason']}")

print("\nSUMMARY")
for line in textwrap.wrap(report["summary"], 96):
    print(f"  {line}")
