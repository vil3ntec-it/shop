#!/bin/bash
# footer.sh — دو سطرِ پایانِ پیامِ کامیت‌هایی که ابزارهای این پوشه خودشان می‌سازند، از همین سیزن.
#
#   . "$OPS/footer.sh"; FOOTER=$(claude_footer) || exit 1
#
# CLAUDE_FOOTER = همان دو سطری که سیزن برای کامیت می‌گوید (یادآوریِ «Attribution» ِ همان سیزن)، دقیقاً:
#   Co-Authored-By: Claude … <noreply@anthropic.com>
#   Claude-Session: https://claude.ai/code/session_…
# نبودن یا بدشکل بودنش ⇒ خروج با پیامِ روشن (هرگز سیزنِ کهنه یا ساختگی نوشته نمی‌شود). در سیزنِ ابری
# (`CLAUDE_CODE_REMOTE_SESSION_ID` = `cse_…`) سطرِ Claude-Session باید همین سیزن باشد، وگرنه «کهنه» است.

claude_footer() {
  local f="${CLAUDE_FOOTER:-}" here=""
  case "${CLAUDE_CODE_REMOTE_SESSION_ID:-}" in cse_?*) here="session_${CLAUDE_CODE_REMOTE_SESSION_ID#cse_}" ;; esac
  if [ -z "$f" ]; then
    echo "FOOTER MISSING: CLAUDE_FOOTER خالی است — دو سطرِ پایانِ کامیتِ همین سیزن را export کنید (README-fa.md §۴)" >&2
    [ -n "$here" ] && echo "  Claude-Session ِ همین سیزن: https://claude.ai/code/$here" >&2
    return 1
  fi
  local lines co ss
  lines=$(printf '%s\n' "$f" | grep -c .)
  co=$(printf '%s\n' "$f" | grep -cE '^Co-Authored-By: Claude [^<>]+ <[^<>@ ]+@[^<>@ ]+>$')
  ss=$(printf '%s\n' "$f" | grep -oE '^Claude-Session: https://claude\.ai/code/session_[A-Za-z0-9]+$')
  if [ "$lines" != 2 ] || [ "$co" != 1 ] || [ -z "$ss" ]; then
    echo "FOOTER BAD: CLAUDE_FOOTER باید دقیقاً دو سطر باشد: «Co-Authored-By: Claude … <…@…>» و «Claude-Session: https://claude.ai/code/session_…»" >&2
    return 1
  fi
  if [ -n "$here" ] && [ "${ss##*/}" != "$here" ]; then
    echo "FOOTER STALE: CLAUDE_FOOTER سیزنِ ${ss##*/} را می‌نویسد، ولی این سیزن $here است" >&2
    return 1
  fi
  printf '%s' "$f"
}
