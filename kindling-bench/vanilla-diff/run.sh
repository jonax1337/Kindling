#!/usr/bin/env bash
# Runs Mojang's server jar and a server jar under test on the same seed and the same scripted console input, then
# compares the saved worlds. Works in Linux, WSL and Git Bash.
#
#   ./run.sh --jar <server jar under test> [--mode contraptions|worldgen|all] [--keep]
#            [--vanilla-jar <jar>] [--vanilla-version 26.3] [--overlay <folder>] [--heap 2G] [--self-check]
#
# Exit code: 0 the worlds are identical, 1 they differ, 2 something other than a difference went wrong.
set -u -o pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNS="$HERE/runs"
PORT=25999          # AGENTS.md: a server of this repository only ever listens here
ADDRESS=127.0.0.1
SEED=-4172144997902289642
MIN_JAVA=25
START_TIMEOUT=300   # seconds to the "Done" line
QUERY_TIMEOUT=60
LOAD_TIMEOUT=900
STOP_TIMEOUT=180

JAR_UNDER_TEST=""
VANILLA_JAR=""
VANILLA_VERSION="26.3"
MODE="contraptions"
OVERLAY=""
HEAP="2G"
KEEP=0
SELF_CHECK=0

usage() { sed -n '2,10p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }
die() { echo "error: $*" >&2; exit 2; }

absolute_path() {  # a path that stays valid after a cd
  printf '%s/%s' "$(cd "$(dirname "$1")" && pwd)" "$(basename "$1")"
}

native_path() {  # Git Bash: the mixed form (E:/dir) that native Windows programs read; elsewhere unchanged
  if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s' "$1"; fi
}

parse_arguments() {
  while [ $# -gt 0 ]; do
    case "$1" in
      --jar) JAR_UNDER_TEST="${2:-}"; shift 2 ;;
      --vanilla-jar) VANILLA_JAR="${2:-}"; shift 2 ;;
      --vanilla-version) VANILLA_VERSION="${2:-}"; shift 2 ;;
      --mode) MODE="${2:-}"; shift 2 ;;
      --overlay) OVERLAY="${2:-}"; shift 2 ;;
      --heap) HEAP="${2:-}"; shift 2 ;;
      --keep) KEEP=1; shift ;;
      --self-check) SELF_CHECK=1; shift ;;
      -h|--help) usage; exit 0 ;;
      *) die "unknown argument: $1 (see --help)" ;;
    esac
  done
  case "$MODE" in contraptions|worldgen|all) ;; *) die "--mode must be contraptions, worldgen or all" ;; esac
  if [ "$SELF_CHECK" -eq 0 ] && [ -z "$JAR_UNDER_TEST" ]; then die "--jar <server jar> is required (or --self-check)"; fi
  if [ -n "$JAR_UNDER_TEST" ] && [ ! -f "$JAR_UNDER_TEST" ]; then die "no such jar: $JAR_UNDER_TEST"; fi
  if [ -n "$VANILLA_JAR" ] && [ ! -f "$VANILLA_JAR" ]; then die "no such jar: $VANILLA_JAR"; fi
  if [ -n "$OVERLAY" ] && [ ! -d "$OVERLAY" ]; then die "no such overlay folder: $OVERLAY"; fi
  [ -z "$JAR_UNDER_TEST" ] || JAR_UNDER_TEST="$(absolute_path "$JAR_UNDER_TEST")"
  [ -z "$VANILLA_JAR" ] || VANILLA_JAR="$(absolute_path "$VANILLA_JAR")"
  [ -z "$OVERLAY" ] || OVERLAY="$(cd "$OVERLAY" && pwd)"
}

find_python() {
  local candidate
  for candidate in "${PYTHON:-}" python3 python py; do
    [ -n "$candidate" ] || continue
    if "$candidate" -I -c 'import sys; sys.exit(sys.version_info < (3, 10))' >/dev/null 2>&1; then
      PYTHON="$candidate"
      return 0
    fi
  done
  die "Python 3.10 or newer is needed (set PYTHON=/path/to/python)"
}

check_java() {
  JAVA="${JAVA:-java}"
  local banner major
  banner="$("$JAVA" -version 2>&1 | head -n 1)" || die "cannot run '$JAVA'"
  major="$(printf '%s' "$banner" | sed -E 's/.*version "([0-9]+).*/\1/')"
  case "$major" in ''|*[!0-9]*) die "cannot read the Java version from: $banner" ;; esac
  [ "$major" -ge "$MIN_JAVA" ] || die "Java $MIN_JAVA or newer is needed, '$JAVA' is: $banner"
}

# ---- what a server folder contains ------------------------------------------------------------------------------

write_server_files() {  # <folder> <mode folder holding mode.properties>
  local folder=$1 mode_folder=$2
  mkdir -p "$folder"
  printf 'eula=true\n' > "$folder/eula.txt"
  {
    printf 'server-ip=%s\nserver-port=%s\nonline-mode=false\nlevel-name=world\nlevel-seed=%s\n' "$ADDRESS" "$PORT" "$SEED"
    printf 'view-distance=4\nsimulation-distance=4\nspawn-protection=0\nmax-players=2\n'
    printf 'pause-when-empty-seconds=-1\nmax-tick-time=-1\nsync-chunk-writes=true\nregion-file-compression=deflate\n'
    printf 'enable-rcon=false\nenable-query=false\nenable-status=false\n'
    cat "$mode_folder/mode.properties"
  } > "$folder/server.properties"
}

# ---- the feeder: waits for the server, sends the commands, waits for the effects ---------------------------------
# Runs on the left of a pipe into the server, so its standard output is the server's standard input: only commands
# may be printed there (through send); everything else goes to standard error. send must never run inside $( ).

send() { printf '%s\n' "$1"; }

abort_server() {
  [ -f "$FEED_FOLDER/java.pid" ] && kill "$(cat "$FEED_FOLDER/java.pid")" 2>/dev/null
  return 0
}

fail() {
  echo "FAILED: $*" >&2
  echo "$*" > "$FEED_FOLDER/feeder.failed"
  abort_server
  return 1
}

check_deadline() {  # <deadline in SECONDS> <what is awaited>
  [ ! -e "$FEED_FOLDER/server.exited" ] || { fail "the server exited while waiting for: $2"; return 1; }
  [ "$SECONDS" -lt "$1" ] || { fail "timed out waiting for: $2"; return 1; }
}

count_matches() { grep -c -E "$2" "$1" 2>/dev/null || true; }

wait_for_line() {  # <pattern> <seconds> <what>
  local deadline=$((SECONDS + $2))
  until grep -q -E "$1" "$FEED_LOG" 2>/dev/null; do
    check_deadline "$deadline" "$3" || return 1
    sleep 1
  done
}

query_gametime() {  # sets GAMETIME to the game time that the server reports
  local pattern='The game time is [0-9]+' before deadline=$((SECONDS + QUERY_TIMEOUT))
  before="$(count_matches "$FEED_LOG" "$pattern")"
  send "time query gametime"
  while [ "$(count_matches "$FEED_LOG" "$pattern")" -le "$before" ]; do
    check_deadline "$deadline" "the answer to 'time query gametime'" || return 1
    sleep 0.2
  done
  GAMETIME="$(grep -o -E "$pattern" "$FEED_LOG" | tail -n 1 | grep -o -E '[0-9]+$')"
}

step_ticks() {  # <ticks>: tick step, then wait until the game time has advanced by that much
  local ticks=$1 target deadline
  deadline=$((SECONDS + QUERY_TIMEOUT + ticks / 5))
  query_gametime || return 1
  target=$((GAMETIME + ticks))
  send "tick step $ticks"
  while :; do
    sleep 0.3
    query_gametime || return 1
    [ "$GAMETIME" -ge "$target" ] && break
    check_deadline "$deadline" "tick step $ticks (game time $GAMETIME of $target)" || return 1
  done
  [ "$GAMETIME" -eq "$target" ] || { fail "tick step $ticks ended at game time $GAMETIME, expected $target"; return 1; }
}

await_loaded() {  # <min chunk x> <min chunk z> <max chunk x> <max chunk z>
  local min_x=$1 min_z=$2 max_x=$3 max_z=$4 x z pending _ cx cz
  local deadline=$((SECONDS + LOAD_TIMEOUT))
  local -A loaded=()
  while :; do
    while read -r _ _ cx cz; do loaded["$cx,$cz"]=1; done \
      < <(grep -o -E '\[Server\] LOADED -?[0-9]+ -?[0-9]+' "$FEED_LOG" | sort -u)
    pending=0
    for ((x = min_x; x <= max_x; x++)); do
      for ((z = min_z; z <= max_z; z++)); do
        [ -n "${loaded["$x,$z"]:-}" ] && continue
        pending=$((pending + 1))
        send "execute if loaded $((x * 16)) 0 $((z * 16)) run say LOADED $x $z"
      done
    done
    [ "$pending" -eq 0 ] && return 0
    check_deadline "$deadline" "$pending chunks of $min_x,$min_z..$max_x,$max_z to load" || return 1
    sleep 2
  done
}

feed_commands() {  # <server folder> <commands file>
  FEED_FOLDER=$1
  FEED_LOG="$1/server.log"
  local line
  wait_for_line 'Done \([0-9.,]+s\)!' "$START_TIMEOUT" "the server to finish starting" || return 1
  while IFS= read -r line || [ -n "$line" ]; do
    line="${line%$'\r'}"
    case "$line" in
      ''|'#'*) ;;
      '@await-loaded '*) await_loaded ${line#@await-loaded } || return 1 ;;
      '@step '*) step_ticks "${line#@step }" || return 1 ;;
      *) send "$line" ;;
    esac
  done < "$2"
  wait_for_stop
}

wait_for_stop() {
  local deadline=$((SECONDS + STOP_TIMEOUT))
  while [ ! -e "$FEED_FOLDER/server.exited" ]; do
    check_deadline "$deadline" "the server to stop" || return 1
    sleep 1
  done
}

# ---- running one server ------------------------------------------------------------------------------------------

start_java() {  # <jar> inside the current folder; keeps standard input (a background job would lose it)
  "$JAVA" "-Xmx$HEAP" -jar "$(native_path "$1")" --nogui <&0 > server.log 2>&1 &
  local pid=$!
  echo "$pid" > java.pid
  wait "$pid"
  echo "$?" > server.exited
}

run_server() {  # <label> <jar> <folder> <mode folder>; returns 0 when the server ran the script and stopped cleanly
  local label=$1 jar=$2 folder=$3 mode_folder=$4
  echo "== $label: starting $(basename "$jar") in $folder" >&2
  write_server_files "$folder" "$mode_folder"
  if [ -n "$OVERLAY" ] && [ "$label" != "vanilla" ]; then cp -R "$OVERLAY"/. "$folder"/; fi
  ( feed_commands "$folder" "$mode_folder/commands.txt" ) | ( cd "$folder" && start_java "$jar" )
  local feeder_status=${PIPESTATUS[0]}
  if [ "$feeder_status" -ne 0 ] && [ ! -e "$folder/feeder.failed" ]; then echo "feeder stopped with status $feeder_status" > "$folder/feeder.failed"; fi
  if [ -e "$folder/feeder.failed" ]; then echo "== $label: failed ($(cat "$folder/feeder.failed"))" >&2; return 1; fi
  if [ "$(cat "$folder/server.exited" 2>/dev/null)" != "0" ]; then echo "== $label: exit code $(cat "$folder/server.exited" 2>/dev/null)" >&2; return 1; fi
  echo "== $label: finished" >&2
}

# ---- a whole run -------------------------------------------------------------------------------------------------

run_mode() {  # <mode>; returns the comparer's exit code, or 2 when a server failed
  local mode=$1 run_folder="$RUNS/$(date +%Y%m%d-%H%M%S)-$1" extra_args=()
  mkdir -p "$run_folder"
  "$PYTHON" -I "$HERE/gen_commands.py" "$mode" "$run_folder" || return 2
  run_server vanilla "$VANILLA_JAR" "$run_folder/vanilla" "$run_folder" || { echo "kept: $run_folder" >&2; return 2; }
  run_server test "$TEST_JAR" "$run_folder/test" "$run_folder" || { echo "kept: $run_folder" >&2; return 2; }
  [ "$mode" = worldgen ] && extra_args=(--skip-entities)
  local status=0
  "$PYTHON" -I "$HERE/compare_worlds.py" "$run_folder/vanilla/world" "$run_folder/test/world" \
    --areas "$run_folder/areas.json" ${extra_args[@]+"${extra_args[@]}"} || status=$?
  if [ "$status" -ge 2 ]; then echo "kept: $run_folder" >&2
  elif [ "$KEEP" -eq 1 ]; then echo "kept: $run_folder"
  else rm -rf "${RUNS:?}/$(basename "$run_folder")"; fi
  return "$status"
}

main() {
  parse_arguments "$@"
  find_python
  check_java
  mkdir -p "$RUNS"
  if [ -z "$VANILLA_JAR" ]; then
    VANILLA_JAR="$("$PYTHON" -I "$HERE/fetch_vanilla.py" --version "$VANILLA_VERSION" | tail -n 1)" || die "could not fetch the vanilla jar"
  fi
  TEST_JAR="$JAR_UNDER_TEST"
  [ "$SELF_CHECK" -eq 1 ] && TEST_JAR="$VANILLA_JAR"
  local worst=0 status mode modes=("$MODE")
  [ "$MODE" = all ] && modes=(contraptions worldgen)
  for mode in "${modes[@]}"; do
    run_mode "$mode"; status=$?
    [ "$status" -gt "$worst" ] && worst=$status
  done
  exit "$worst"
}

main "$@"
