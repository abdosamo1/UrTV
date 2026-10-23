"""optimize/tv.py: the one-shot `--install` (Play Protect holding or blocking it) and reopening port 5555 through
Wireless debugging after a restart (Sony BRAVIA, Skyworth 24AE). A fake ADB connection and a fake `adb`; no TV.

    python3 tests/tv_install_test.py
"""
import contextlib, io, os, stat, sys, tempfile, types

MODE = {"install": "Success", "refuse_until_reopen": False, "reopened": False, "calls": []}

class Dev:
    def __init__(self, host, port, **k): self.port = port
    def connect(self, **k):
        if MODE["refuse_until_reopen"] and not MODE["reopened"]: raise ConnectionRefusedError("refused")
    def shell(self, cmd, read_timeout_s=0, transport_timeout_s=None):
        MODE["calls"].append((cmd, transport_timeout_s))
        if cmd.startswith("pm install"):
            r = MODE["install"]
            if isinstance(r, Exception): raise r
            return r
        if cmd == "getprop sys.boot_completed": return "1"
        return ""
    def close(self): pass

for name in ["adb_shell", "adb_shell.adb_device", "adb_shell.auth", "adb_shell.auth.keygen", "adb_shell.auth.sign_pythonrsa"]:
    sys.modules[name] = types.ModuleType(name)
sys.modules["adb_shell.adb_device"].AdbDeviceTcp = Dev
sys.modules["adb_shell.auth.keygen"].keygen = lambda p: None
sys.modules["adb_shell.auth.sign_pythonrsa"].PythonRSASigner = lambda a, b: None

# a fake official adb: mDNS lists the TV's TLS port; `tcpip 5555` opens the port for the fake connection
tmp = tempfile.mkdtemp()
flag = os.path.join(tmp, "opened")
fake = os.path.join(tmp, "adb")
open(fake, "w").write(f'''#!/bin/sh
case "$1" in
  mdns) printf 'adb-SONY-x\\t_adb-tls-connect._tcp\\t192.168.1.20:37123\\nother\\t_adb-tls-connect._tcp\\t192.168.1.99:40000\\n' ;;
  connect) echo "connected to $2" ;;
  -s) [ "$2" = 192.168.1.20:37123 ] && [ "$3" = tcpip ] && touch {flag} && echo "restarting in TCP mode port: $4" ;;
esac
''')
os.chmod(fake, os.stat(fake).st_mode | stat.S_IEXEC)
os.environ.update(TV_HOST="192.168.1.20", TV_TRIES="3", ADB=fake)
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "optimize"))
import tv
tv.time.sleep = lambda s: None
tv.signer = lambda: None

failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + detail))
    failed += not ok

def install(result):
    MODE.update(install=result, calls=[])
    out, code = io.StringIO(), 0
    with contextlib.redirect_stdout(out):
        try: tv.install(tv.TV(), "/data/local/tmp/x.apk")
        except SystemExit as e: code = e.code
    return code, out.getvalue(), [c for c in MODE["calls"] if c[0].startswith("pm install")]

c, o, calls = install("Success")
check("install: Success passes through", c == 0 and "Success" in o and len(calls) == 1, o)
c, o, calls = install(TimeoutError("TcpTimeoutException"))
check("install held by Play Protect: one try, not retried, says why", c == 3 and len(calls) == 1 and "Play Protect" in o, f"{c} {len(calls)} {o}")
check("install waits minutes, not the default 60 s", calls and calls[0][1] >= 180, str(calls))
c, o, calls = install("Failure [INSTALL_FAILED_VERIFICATION_FAILURE]")
check("install blocked by verification: says Play Protect", c == 3 and "Play Protect" in o, o)

# port 5555 closed after a restart: the connection is refused until `adb tcpip 5555` ran on the TV's TLS port
MODE.update(refuse_until_reopen=True, reopened=False)
orig = tv.reopen_5555
def reopen():
    ok = orig()
    MODE["reopened"] = os.path.exists(flag)
    return ok
tv.reopen_5555 = reopen
out = io.StringIO()
with contextlib.redirect_stdout(out), contextlib.redirect_stderr(io.StringIO()):
    try: r = tv.TV().shell("echo hi"); ok = True
    except SystemExit as e: ok = False
check("refused 5555: reopened through Wireless debugging (the TV's own TLS port), then the command runs",
      ok and os.path.exists(flag), out.getvalue())

os.remove(flag); MODE["reopened"] = False
t0 = [0]
tv.time.time = lambda: (t0.__setitem__(0, t0[0] + 20) or t0[0])   # 20 s per look
with contextlib.redirect_stdout(out), contextlib.redirect_stderr(io.StringIO()):
    try: tv.wait_for_tv(); ok = True
    except SystemExit: ok = False
check("after a restart: wait_for_tv reopens 5555 and sees the TV back", ok and os.path.exists(flag))

os.environ["ADB"] = "/nonexistent/adb"
check("no official adb: reopen does nothing", orig() is False)

print("\nall tv.py install/reconnect checks passed" if not failed else f"\n{failed} failed")
sys.exit(1 if failed else 0)
