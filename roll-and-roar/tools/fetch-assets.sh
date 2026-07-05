#!/usr/bin/env bash
#
# Roll & Roar — asset acquisition script (build spec §4.3)
#
# Downloads every asset pack in the verified manifest (§4.1) from
# OpenGameArt, verifies the archives, extracts them into assets/raw/,
# and records each pack's "License(s):" line VERBATIM into CREDITS.md
# (between the AUTO-VERIFIED markers).
#
# The Arks dino pack lives on itch.io whose download URLs are
# session-generated; the script attempts a polite fetch and otherwise
# prints the manual-download instruction and continues.
#
# Usage:  ./tools/fetch-assets.sh
# Requires: curl, unzip. Re-runnable; already-downloaded packs are skipped.

set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RAW="$ROOT/assets/raw"
CREDITS="$ROOT/CREDITS.md"
LOG="$RAW/fetch-log.txt"
UA="Mozilla/5.0 (X11; Linux x86_64) roll-and-roar-asset-fetcher (polite; one-time download of free-licensed packs)"

mkdir -p "$RAW"
: > "$LOG"

PASS=0
FAIL=0
MANUAL=0
VERIFIED_BLOCK=""

log() { echo "$*" | tee -a "$LOG"; }

# ---------------------------------------------------------------------------
# Manifest: slug|folder|filename-hint|expected-content-regex
# slug           = opengameart.org/content/<slug>
# folder         = extraction folder under assets/raw/
# filename-hint  = prefer an archive whose name matches this (empty = first archive)
# expected       = regex a listing entry must match for verification
# ---------------------------------------------------------------------------
MANIFEST=$(cat <<'EOF'
pixel-adventure-1|pixel-adventure-1||\.png$
pixel-adventure-2|pixel-adventure-2||\.png$
treasure-hunters-demo|treasure-hunters||\.png$
pirate-bomb|pirate-bomb||\.png$
kings-and-pigs|kings-and-pigs||\.png$
sunnyland-forest|sunnyland-forest||\.png$
sunnyland-tall-forest-environment|sunnyland-tall-forest|tall_forest|\.png$
underwater-diving-pack|underwater-diving|underwater|\.png$
2d-platformer-snow-pack|snow-pack|Snow|\.png$
sand-desert-16x16-tileset|sand-desert||\.png$
16x16-desert-tilesets|desert-tilesets||\.png$
5-chiptunes-action|music-chiptunes-action||\.(wav|ogg|mp3)$
4-chiptunes-adventure|music-chiptunes-adventure||\.(wav|ogg|mp3)$
boss-battle-music|music-boss-battle||\.(wav|ogg|mp3)$
512-sound-effects-8-bit-style|sfx-512||\.(wav|ogg|mp3)$
jrpg-pack-1-exploration|music-jrpg-exploration||\.(wav|ogg|mp3)$
EOF
)

strip_tags() { sed -e 's/<[^>]*>/ /g' -e 's/&nbsp;/ /g' -e 's/&amp;/\&/g' | tr -s ' '; }

fetch_oga_pack() {
  local slug="$1" folder="$2" hint="$3" expected="$4"
  local page_url="https://opengameart.org/content/$slug"
  local dest_dir="$RAW/$folder"
  local page href file_url fname archive licence

  log ""
  log "=== $slug ==="

  page=$(curl -fsSL -A "$UA" --max-time 60 "$page_url") || {
    log "FAIL: could not fetch page $page_url (network policy or dead URL)"
    FAIL=$((FAIL + 1))
    return 1
  }

  # --- licence line, verbatim ---
  licence=$(echo "$page" | grep -A 6 -i 'license(s)' | strip_tags \
    | grep -oiE '(CC0|CC-BY-SA[ 0-9.]*|CC-BY[ 0-9.]*|OGA-BY[ 0-9.]*|GPL[ 0-9.]*|Public Domain)[^,<]*' \
    | head -5 | paste -sd ', ' -)
  [ -z "$licence" ] && licence="UNKNOWN — read $page_url manually"
  log "Licence(s) on page: $licence"

  # --- pick the archive link under File(s): ---
  local links
  links=$(echo "$page" | grep -oE 'href="[^"]*sites/default/files/[^"]*\.(zip|rar|7z)"' \
    | sed -e 's/^href="//' -e 's/"$//' | sort -u)
  if [ -z "$links" ]; then
    log "FAIL: no archive link found on $page_url"
    FAIL=$((FAIL + 1))
    return 1
  fi
  if [ -n "$hint" ]; then
    href=$(echo "$links" | grep -i -- "$hint" | head -1)
  fi
  [ -z "${href:-}" ] && href=$(echo "$links" | head -1)
  case "$href" in
    http*) file_url="$href" ;;
    *) file_url="https://opengameart.org$href" ;;
  esac
  fname=$(basename "${file_url%%\?*}" | sed 's/%20/ /g')
  archive="$RAW/$fname"

  # --- download (skip if already present and non-empty) ---
  if [ -s "$archive" ]; then
    log "Already downloaded: $fname"
  else
    log "Downloading: $file_url"
    curl -fSL -A "$UA" --max-time 300 --retry 3 -o "$archive" "$file_url" || {
      log "FAIL: download failed for $file_url"
      rm -f "$archive"
      FAIL=$((FAIL + 1))
      return 1
    }
  fi

  # --- verify: non-zero, unzips cleanly, expected content present ---
  if [ ! -s "$archive" ]; then
    log "FAIL: $fname is empty"
    FAIL=$((FAIL + 1))
    return 1
  fi
  case "$fname" in
    *.zip)
      if ! unzip -tq "$archive" >/dev/null 2>&1; then
        log "FAIL: $fname does not unzip cleanly"
        FAIL=$((FAIL + 1))
        return 1
      fi
      if ! unzip -l "$archive" | grep -qiE "$expected"; then
        log "FAIL: $fname contains no files matching /$expected/"
        FAIL=$((FAIL + 1))
        return 1
      fi
      mkdir -p "$dest_dir"
      unzip -oq "$archive" -d "$dest_dir"
      ;;
    *)
      log "WARN: $fname is not a zip — extract manually into $dest_dir"
      mkdir -p "$dest_dir"
      ;;
  esac

  local size
  size=$(du -h "$archive" | cut -f1)
  log "OK: $fname ($size) -> assets/raw/$folder/"
  PASS=$((PASS + 1))
  VERIFIED_BLOCK="$VERIFIED_BLOCK
| $slug | $licence | $page_url | $fname |"
  return 0
}

fetch_arks_dinos() {
  local page_url="https://arks.itch.io/dino-characters"
  local target="$RAW/DinoCharacters - Sprites 1_1.zip"

  log ""
  log "=== arks-dino-characters (itch.io) ==="
  if [ -s "$target" ]; then
    if unzip -tq "$target" >/dev/null 2>&1 && unzip -l "$target" | grep -qi '\.png$'; then
      mkdir -p "$RAW/dino-characters"
      unzip -oq "$target" -d "$RAW/dino-characters"
      log "OK: dino pack already present and verified -> assets/raw/dino-characters/"
      PASS=$((PASS + 1))
      VERIFIED_BLOCK="$VERIFIED_BLOCK
| dino-characters | Free, attribution required — credit \"Arks / @ArksDigital\" | $page_url | DinoCharacters - Sprites 1_1.zip |"
      return 0
    fi
    log "FAIL: dino zip present but not a valid zip with PNGs"
    FAIL=$((FAIL + 1))
    return 1
  fi

  # Polite attempt: itch pages rarely expose a direct file URL without a session.
  local direct
  direct=$(curl -fsSL -A "$UA" --max-time 60 "$page_url" 2>/dev/null \
    | grep -oE 'https://[^"]*\.zip' | head -1) || true
  if [ -n "${direct:-}" ]; then
    log "Found direct link, attempting: $direct"
    if curl -fSL -A "$UA" --max-time 300 -o "$target" "$direct" && unzip -tq "$target" >/dev/null 2>&1; then
      mkdir -p "$RAW/dino-characters"
      unzip -oq "$target" -d "$RAW/dino-characters"
      log "OK: dino pack downloaded -> assets/raw/dino-characters/"
      PASS=$((PASS + 1))
      return 0
    fi
    rm -f "$target"
  fi

  MANUAL=$((MANUAL + 1))
  log "MANUAL STEP REQUIRED:"
  log "  open https://arks.itch.io/dino-characters -> Download Now -> pay \$0 ->"
  log "  save 'DinoCharacters - Sprites 1_1.zip' into assets/raw/ - then re-run this script."
  return 0
}

# ---------------------------------------------------------------------------
# Run
# ---------------------------------------------------------------------------
log "Roll & Roar asset fetch — $(date -u +%Y-%m-%dT%H:%M:%SZ)"

while IFS='|' read -r slug folder hint expected; do
  [ -z "$slug" ] && continue
  href=""
  fetch_oga_pack "$slug" "$folder" "$hint" "$expected"
done <<EOF2
$MANIFEST
EOF2

fetch_arks_dinos

# --- write verified licences into CREDITS.md between markers ---
if [ -n "$VERIFIED_BLOCK" ] && [ -f "$CREDITS" ] && [ "$FAIL" -eq 0 ]; then
  TABLE="| Pack (slug) | Licence as scraped from page | Source | Archive |
|---|---|---|---|$VERIFIED_BLOCK"
  awk -v block="$TABLE" '
    /<!-- BEGIN AUTO-VERIFIED LICENSES -->/ { print; print block; skip=1; next }
    /<!-- END AUTO-VERIFIED LICENSES -->/   { skip=0 }
    !skip { print }
  ' "$CREDITS" > "$CREDITS.tmp" && mv "$CREDITS.tmp" "$CREDITS"
  log ""
  log "CREDITS.md auto-verified section updated. Cross-check it against the manifest table."
fi

log ""
log "==============================================="
log "Done. OK: $PASS   FAILED: $FAIL   MANUAL: $MANUAL"
log "Full log: assets/raw/fetch-log.txt"
[ "$FAIL" -gt 0 ] && log "Some packs FAILED — fix (network policy? dead URL?) and re-run; §4.4 substitution protocol applies to dead URLs."
[ "$MANUAL" -gt 0 ] && log "Manual dino step pending (see above)."
exit "$FAIL"
