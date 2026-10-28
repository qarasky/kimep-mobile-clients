#!/usr/bin/env python3
"""
Parse KIMEP academic-calendar PDFs into structured JSON for the Android app.

Input : the *-detailed-ENG.pdf files (KIMEP publishes KAZ/RUS/ENG variants). They are
        downloaded automatically on first run into calendar/ (they are not committed).
Output: kimep-android/app/src/main/assets/calendar.json

Why the coordinate approach: the calendar is a 4-column table (Fall / Spring /
Summer1 / Summer2) with event labels that wrap over multiple lines. pdftotext's
-bbox-layout gives word bounding boxes, so we can (a) group words into rows by y,
(b) find the four column centres from the header row, and (c) assign each value
cluster to the nearest column. This adapts to the layout differences between the
2025-26 and 2026-27 editions.

Usage:
    python3 tools/parse_calendar.py
"""
import html
import json
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
CAL_DIR = os.path.join(ROOT, "calendar")
OUT = os.path.join(ROOT, "kimep-android", "app", "src", "main", "assets", "calendar.json")

MONTHS = {
    "january": 1, "february": 2, "march": 3, "april": 4, "may": 5, "june": 6,
    "july": 7, "august": 8, "september": 9, "october": 10, "november": 11,
    "december": 12,
}

# The calendar page (https://www.kimep.kz/current-students/kz/calendar/) links the KAZ
# PDFs; the ENG ones live next to them. The upload folder changes when a file is
# replaced, so check the page if a download 404s.
SOURCES = {
    "AY27-28-detailed-ENG.pdf":
        "https://www.kimep.kz/current-students/files/2026/10/AY27-28-detailed-ENG.pdf",
    "AY26-27-detailed-ENG.pdf":
        "https://www.kimep.kz/current-students/files/2026/10/AY26-27-detailed-ENG.pdf",
    "AY25-26-detailed-ENG.pdf":
        "https://www.kimep.kz/current-students/files/2025/04/AY25-26-detailed-ENG.pdf",
}

# Years KIMEP published only as a scanned image (no text layer). They are transcribed
# by hand into tools/calendar_manual/<year id>.json and merged in when the PDF can't be read.
MANUAL_DIR = os.path.join(HERE, "calendar_manual")

HEADER_TOKENS = ["Fall", "Spring", "Summer1", "Summer2"]
WORD_RE = re.compile(
    r'<word xMin="([\d.]+)" yMin="([\d.]+)" xMax="([\d.]+)" yMax="([\d.]+)">(.*?)</word>'
)


def words_from_pdf(pdf_path):
    xml = subprocess.run(
        ["pdftotext", "-bbox-layout", pdf_path, "-"],
        capture_output=True, text=True, check=True,
    ).stdout
    out = []
    for m in WORD_RE.finditer(xml):
        xmin, ymin, xmax, ymax, text = m.groups()
        text = html.unescape(text)
        if text.strip():
            out.append((float(xmin), float(ymin), float(xmax), text))
    return out


def group_rows(words, tol=3.0):
    rows, current, current_y = [], [], None
    for w in sorted(words, key=lambda w: (w[1], w[0])):
        if current_y is None or abs(w[1] - current_y) <= tol:
            current.append(w)
            current_y = w[1] if current_y is None else current_y
        else:
            rows.append(sorted(current, key=lambda w: w[0]))
            current, current_y = [w], w[1]
    if current:
        rows.append(sorted(current, key=lambda w: w[0]))
    return rows


def clusters(words, gap=18.0):
    cs, cur = [], []
    for w in sorted(words, key=lambda w: w[0]):
        if cur and w[0] - cur[-1][2] > gap:
            cs.append(cur)
            cur = []
        cur.append(w)
    if cur:
        cs.append(cur)
    return cs


def iso(year, month, day):
    return f"{year:04d}-{month:02d}-{day:02d}"


def normalize_date(text):
    raw = (text or "").strip()
    if not raw or raw in {"-", "–", "—", "NA", "N/A", "n/a"}:
        return None, None, None

    t = raw.replace("\u2013", "-").replace("\u2014", "-")

    us = re.findall(r"(\d{1,2})/(\d{1,2})/(\d{4})", t)
    if us:
        dates = [iso(int(y), int(mo), int(da)) for mo, da, y in us]
        return dates[0], dates[-1], raw

    month = next((n for name, n in MONTHS.items()
                  if re.search(r"\b" + name, t, re.IGNORECASE)), None)
    if month is None:
        return None, None, raw

    ym = re.search(r"(\d{4})", t)
    if not ym:
        return None, None, raw
    year = int(ym.group(1))
    days = [int(n) for n in re.findall(r"\b(\d{1,2})\b", t) if 1 <= int(n) <= 31]
    if not days:
        return None, None, raw
    return iso(year, month, min(days)), iso(year, month, max(days)), raw


def split_holiday(first_line, extra_lines):
    """"Independence Day   NO Make ups required" -> ("Independence Day", "NO Make ups required")."""
    title_words, note_words = [], []
    prev_xmax, in_note = None, False
    for w in first_line:
        if prev_xmax is not None and (w[0] - prev_xmax) > 15:
            in_note = True
        (note_words if in_note else title_words).append(w[3])
        prev_xmax = w[2]
    for line in extra_lines:
        note_words.extend(w[3] for w in line)
    return " ".join(title_words).strip(" ."), " ".join(note_words).strip()


def build_year(pdf_path, year_id):
    rows = group_rows(words_from_pdf(pdf_path))

    header_row = None
    closed_y = None
    advising_y = None
    for row in rows:
        texts = [w[3] for w in row]
        if header_row is None and all(
            any(t.startswith(tok) for t in texts) for tok in HEADER_TOKENS
        ):
            header_row = row
        if closed_y is None and any("KIMEP" in t for t in texts) and any(
            "closed" in t.lower() for t in texts
        ):
            closed_y = row[0][1]
        if advising_y is None and any(t == "Advising" for t in texts):
            advising_y = row[0][1]

    if header_row is None or advising_y is None:
        raise RuntimeError(f"layout not recognised: {pdf_path}")

    centers = []
    for tok in HEADER_TOKENS:
        w = next(w for w in header_row if w[3].startswith(tok))
        centers.append((w[0] + w[2]) / 2)
    label_max = centers[0] - 45.0

    start_year = int(year_id.split("-")[0])
    end_year = int(year_id.split("-")[1])
    semester_defs = [
        ("F", f"Fall {start_year}"),
        ("S", f"Spring {end_year}"),
        ("SU1", f"Summer 1 {end_year}"),
        ("SU2", f"Summer 2 {end_year}"),
    ]
    semesters = {sid: {"id": sid, "name": name, "events": []} for sid, name in semester_defs}
    closed = []

    label_lines = []
    holiday_mode = False

    for row in rows:
        y = row[0][1]
        if y < advising_y - 1:
            continue

        joined = " ".join(w[3] for w in row)
        # footnote block starts with two-or-more asterisks at the line start
        if re.match(r"^\*{2,}", joined.strip()) or "Ресми" in joined or "Formally" in joined:
            break

        if closed_y is not None and y > closed_y + 1:
            holiday_mode = True

        if closed_y is not None and abs(y - closed_y) < 1:
            # the "KIMEP is closed" heading is not part of the first holiday's label
            label_lines = []
            continue

        label_words = [w for w in row if w[0] < label_max]
        col_words = [w for w in row if w[0] >= label_max]

        if not col_words:
            label_lines.append(label_words)
            continue

        cols = ["", "", "", ""]
        for cl in clusters(col_words):
            span = cl[-1][2] - cl[0][0]
            if span > 190:          # a long sentence spanning the table, not a cell
                continue
            center = (cl[0][0] + cl[-1][2]) / 2
            idx = min(range(4), key=lambda i: abs(centers[i] - center))
            cols[idx] = " ".join(w[3] for w in cl).strip()

        lines = [l for l in label_lines if l] + ([label_words] if label_words else [])
        label_lines = []

        if holiday_mode:
            if not lines:
                continue
            title, note = split_holiday(lines[0], lines[1:])
            if not title or title.lower().startswith("kimep"):
                continue
            dates = []
            for c in cols:
                s, e, d = normalize_date(c)
                if d:
                    dates.append({"start": s, "end": e, "display": d})
            if dates or note:
                closed.append({"title": title, "note": note, "dates": dates})
        else:
            label = " ".join(w[3] for line in lines for w in line).strip()
            if not label:
                continue
            for i, c in enumerate(cols):
                if not c:
                    continue
                start, end, display = normalize_date(c)
                if display is None:
                    continue
                semesters[semester_defs[i][0]]["events"].append({
                    "title": label,
                    "start": start,
                    "end": end,
                    "display": display,
                })

    # de-duplicate (same event can repeat a value in adjacent rows)
    for sem in semesters.values():
        seen, unique = set(), []
        for e in sem["events"]:
            key = (e["title"], e["display"])
            if key not in seen:
                seen.add(key)
                unique.append(e)
        sem["events"] = unique

    return {
        "id": year_id,
        "title": f"Academic Calendar {year_id}",
        "semesters": [semesters[sid] for sid, _ in semester_defs],
        "closed": closed,
    }


def ensure_pdf(name):
    path = os.path.join(CAL_DIR, name)
    if os.path.exists(path):
        return path
    os.makedirs(CAL_DIR, exist_ok=True)
    print(f"downloading {name} …", file=sys.stderr)
    subprocess.run(["curl", "-sSL", "-f", "-o", path, SOURCES[name]], check=True)
    return path


def year_id_for(pdf_name):
    """AY26-27-detailed-ENG.pdf -> 2026-2027"""
    m = re.match(r"AY(\d\d)-(\d\d)-", pdf_name)
    return f"20{m.group(1)}-20{m.group(2)}"


def has_text_layer(pdf_path):
    return bool(words_from_pdf(pdf_path))


def dump_words(pdf_path):
    """TSV of the words used for parsing (x0, y, x1, text): fixtures for the Kotlin parser tests."""
    for x0, y, x1, text in words_from_pdf(pdf_path):
        print(f"{x0:.2f}\t{y:.2f}\t{x1:.2f}\t{text}")


def load_manual():
    years = {}
    if os.path.isdir(MANUAL_DIR):
        for name in sorted(os.listdir(MANUAL_DIR)):
            if name.endswith(".json"):
                with open(os.path.join(MANUAL_DIR, name)) as f:
                    year = json.load(f)
                years[year["id"]] = year
    return years


def main():
    if len(sys.argv) == 3 and sys.argv[1] == "--dump-words":
        dump_words(sys.argv[2])
        return

    manual = load_manual()
    years = {}
    for name in SOURCES:
        year_id = year_id_for(name)
        try:
            pdf = ensure_pdf(name)
        except subprocess.CalledProcessError:
            print(f"{year_id}: download failed", file=sys.stderr)
            continue
        if not has_text_layer(pdf):
            print(f"{year_id}: scanned PDF without a text layer", file=sys.stderr)
            continue
        year = build_year(pdf, year_id)
        year["source"] = "pdf"
        total = sum(len(s["events"]) for s in year["semesters"])
        print(f"{year_id}: {total} events, {len(year['closed'])} closures")
        years[year_id] = year

    for year_id, year in manual.items():
        if year_id in years:
            print(f"{year_id}: readable PDF available, ignoring the manual transcription")
            continue
        print(f"{year_id}: using the manual transcription")
        years[year_id] = year

    ordered = [years[k] for k in sorted(years, reverse=True)]
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w") as f:
        json.dump({"years": ordered}, f, ensure_ascii=False, indent=2)
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
