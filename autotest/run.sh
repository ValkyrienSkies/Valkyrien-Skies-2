#!/usr/bin/env bash
# In-game autotest runner: launches the Forge dev client on a virtual display (no window on the desktop),
# drives it with the given script via AutoTestHarness, and collects screenshots + result.
#
# usage: autotest/run.sh <script-name> [world-to-reset]
#   script-name is the basename of a file in autotest/, without .txt
#
# Display: gamescope's headless backend is preferred when present — it is a nested compositor on the real
# GPU, so the client gets a hardware GL 4.6 context, which is what Embeddium/Oculus want. Xvfb is the
# fallback and renders through Mesa's llvmpipe: fine for logic and snapshot-state assertions, but anything
# judging shader output should be run on a real display instead (DISPLAY=:0 AUTOTEST_DISPLAY=none).
#
# Shutdown is not left to the client. Minecraft's exit regularly never completes here: Minecraft#stop()
# calls System.exit, the VM starts its exit sequence, then blocks forever on a thread — usually in the GL
# driver — that never reaches a safepoint. Once the VM is in that state nothing inside it can help. So the
# run is over the moment the harness writes autotest-result.txt, and this script takes the process down.
set -euo pipefail
cd "$(dirname "$0")/.."

NAME="${1:?usage: autotest/run.sh <script-name> [world]}"
SCRIPT="$(realpath "autotest/$NAME.txt")"
WORLD="${2:-autotest}"
RUN_DIR="forge/run"
RESULT_FILE="$RUN_DIR/autotest-result.txt"

# Cap on how long the script may take to produce a result, covering the gradle build and client startup.
TIMEOUT="${AUTOTEST_TIMEOUT:-900}"
# How long the client gets after writing its result to flush screenshots and finish its log.
GRACE="${AUTOTEST_GRACE:-8}"

rm -rf "$RUN_DIR/saves/$WORLD" "$RESULT_FILE"
mkdir -p "$RUN_DIR/screenshots"

# Server-side config for freshly created worlds. vs-core ships enableSealedFluidTopology and
# enableShipFlooding off by default, and with them off no topology or flooding snapshot is ever produced
# — every flooding test would pass vacuously against an empty world. Forge seeds a new world's
# serverconfig from defaultconfigs/, so the tracked copy here is what makes these runs reproducible
# instead of depending on whatever the run directory happened to be left in.
if [ -d autotest/defaultconfigs ]; then
    mkdir -p "$RUN_DIR/defaultconfigs"
    cp -r autotest/defaultconfigs/. "$RUN_DIR/defaultconfigs/"
fi

# Crash detection inputs. Both are cleared/counted up front so a crash left behind by an earlier run cannot
# be mistaken for one from this run: latest.log survives across runs until the client rotates it, and
# grepping a stale copy would fail every run from then on.
CRASH_DIR="$RUN_DIR/crash-reports"
LOG_FILE="$RUN_DIR/logs/latest.log"
mkdir -p "$CRASH_DIR"
CRASH_COUNT_BEFORE="$(find "$CRASH_DIR" -maxdepth 1 -type f | wc -l)"
rm -f "$LOG_FILE"

# The client JVM is forked by the gradle *daemon*, so it is not in this script's process group and a group
# kill does not reach it. Match it by this project's own dev-launch path, which appears in the client's
# command line and which no other checkout or project shares — matching the architectury runtime alone
# would also kill dev clients belonging to unrelated projects.
CLIENT_PATTERN="$PWD/forge/.gradle/architectury"

kill_client() {
    pkill -f "$CLIENT_PATTERN" 2>/dev/null || true
    sleep 2
    pkill -9 -f "$CLIENT_PATTERN" 2>/dev/null || true
}

# A client left behind by an earlier run holds this run directory and its save, and would be
# indistinguishable from the one launched here. Clear it out before starting rather than racing it.
if pgrep -f "$CLIENT_PATTERN" >/dev/null 2>&1; then
    echo "autotest: killing stale client from a previous run" >&2
    kill_client
fi

GRADLE_CMD=(./gradlew :forge:runClient -Pvs_autotest="$SCRIPT" ${AUTOTEST_GRADLE_ARGS:-} --console=plain)

DISPLAY_MODE="${AUTOTEST_DISPLAY:-auto}"
if [ "$DISPLAY_MODE" = auto ]; then
    if command -v gamescope >/dev/null 2>&1; then DISPLAY_MODE=gamescope; else DISPLAY_MODE=xvfb; fi
fi

# Launched without setsid on purpose. setsid forks when it is already a process-group leader and the
# wrapper then exits immediately, so $! would name a PID that is gone within milliseconds — the wait loop
# below reads that as "the launcher died" on its first pass, gives up, and kills a client that was in fact
# starting up fine. Teardown does not need a group kill anyway: the client JVM is matched by
# CLIENT_PATTERN, and the wrapper is killed by PID.
case "$DISPLAY_MODE" in
    gamescope) gamescope -W 1600 -H 900 --backend headless -- "${GRADLE_CMD[@]}" & ;;
    xvfb)      xvfb-run -a -s "-screen 0 1600x900x24" "${GRADLE_CMD[@]}" & ;;
    none)      "${GRADLE_CMD[@]}" & ;;
    *) echo "autotest: unknown AUTOTEST_DISPLAY=$DISPLAY_MODE" >&2; exit 64 ;;
esac
LAUNCHER_PID=$!

trap 'kill_client; kill "$LAUNCHER_PID" 2>/dev/null || true' EXIT INT TERM

TIMED_OUT=0
CRASHED=0
DEADLINE=$((SECONDS + TIMEOUT))
while [ ! -f "$RESULT_FILE" ]; do
    if ! kill -0 "$LAUNCHER_PID" 2>/dev/null; then
        break  # launcher died without a result; the check below reports it
    fi
    # Fail fast on a crash instead of waiting out the timeout. A mod-loading failure does not exit the
    # process: FML puts the client on its error screen and it sits there until something kills it, so no
    # result file is ever written and the launcher stays alive. Without this the run blocks for the full
    # TIMEOUT for a failure that was already decided in the first thirty seconds.
    if [ "$(find "$CRASH_DIR" -maxdepth 1 -type f | wc -l)" -gt "$CRASH_COUNT_BEFORE" ]; then
        CRASHED=1
        break
    fi
    if [ -f "$LOG_FILE" ] && grep -qaE "Mod Loading has failed|has failed to load correctly|Minecraft has crashed|Failed to start the minecraft server" "$LOG_FILE"; then
        CRASHED=1
        break
    fi
    if [ "$SECONDS" -ge "$DEADLINE" ]; then
        TIMED_OUT=1
        break
    fi
    sleep 2
done

if [ -f "$RESULT_FILE" ]; then
    sleep "$GRACE"
fi

kill_client
kill "$LAUNCHER_PID" 2>/dev/null || true
wait "$LAUNCHER_PID" 2>/dev/null || true
trap - EXIT INT TERM

if [ "$TIMED_OUT" = 1 ]; then
    echo "autotest: timed out after ${TIMEOUT}s with no result" >&2
    exit 124
fi
if [ "$CRASHED" = 1 ]; then
    LATEST_CRASH="$(find "$CRASH_DIR" -maxdepth 1 -type f -printf '%T@ %p\n' 2>/dev/null | sort -rn | head -1 | cut -d' ' -f2-)"
    echo "autotest: client crashed${LATEST_CRASH:+ (see $LATEST_CRASH)}" >&2
    exit 2
fi
if [ ! -f "$RESULT_FILE" ]; then
    echo "autotest: client exited without writing a result" >&2
    exit 1
fi

RESULT="$(cat "$RESULT_FILE")"
echo "autotest: $RESULT"
[[ "$RESULT" == OK* ]]
