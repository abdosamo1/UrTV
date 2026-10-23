"""The adhan's arithmetic (android/app/.../AdhanTimes.java) on a JVM: next prayer, Fajr optional, the clock change,
Aladhan's format, a late alarm, a Mawaqit page. Plus the adhan files themselves. Needs javac; skipped without it.

    python3 tests/adhan_test.py
"""
import re, shutil, subprocess, sys, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok

# every voice in Adhan.java has its file, every file a voice, and the defaults exist
src = (ROOT / "android/app/src/main/java/sa/urtv/screensaver/Adhan.java").read_text(encoding="utf-8")
voices = re.findall(r'\{"([a-z0-9-]+)", "[^"]+"\}', src[src.index("VOICES"):src.index("FAJR_FIRST")])
files = sorted(p.stem for p in (ROOT / "assets/adhan").glob("*.mp3"))
check("each adhan voice has its file, and each file a voice", sorted(voices) == files, f"{voices} vs {files}")
for d in re.findall(r'DEFAULT_(?:VOICE|FAJR) = "([^"]+)"', src):
    check(f"default voice {d} exists", d in files)
size = sum(p.stat().st_size for p in (ROOT / "assets/adhan").glob("*.mp3"))
check(f"adhan files stay small ({size // 1_000_000} MB, under 14)", size < 14_000_000, size)

javac, java = shutil.which("javac"), shutil.which("java")
if not (javac and java):
    print("SKIP AdhanTimes.java: no javac here (CI's Build APKs compiles it)")
else:
    with tempfile.TemporaryDirectory() as tmp:
        r = subprocess.run([javac, "-encoding", "UTF-8", "-d", tmp,
                            str(ROOT / "android/app/src/main/java/sa/urtv/screensaver/AdhanTimes.java"),
                            str(ROOT / "tests/AdhanTimesRun.java")], capture_output=True, text=True)
        check("AdhanTimes.java compiles", r.returncode == 0, r.stderr[-800:])
        if r.returncode == 0:
            r = subprocess.run([java, "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-cp", tmp,
                                "sa.urtv.screensaver.AdhanTimesRun"], capture_output=True)
            out = r.stdout.decode("utf-8", "replace")
            print(out.rstrip())
            check("AdhanTimes checks", r.returncode == 0, r.stderr.decode()[-600:])

print("\nall adhan checks passed" if not failed else f"\n{failed} failed")
sys.exit(1 if failed else 0)
