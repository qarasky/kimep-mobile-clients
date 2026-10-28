#!/usr/bin/env python3
"""
Hand transcription of the AY27-28 academic calendar -> AY27-28.json.

KIMEP published AY27-28 (https://www.kimep.kz/current-students/files/2026/10/AY27-28-detailed-ENG.pdf)
as a rotated, stamped scan with no text layer, so it can't be parsed like the other years.
Each cell below is copied from that scan exactly as printed. Every "Weekday, Month DD, YYYY"
cell is checked against the real calendar, which catches most misreadings.

Re-run (python3 tools/calendar_manual/make_AY27-28.py) after correcting a cell. parse_calendar.py
ignores this file as soon as a readable PDF for the year is available.
"""
import datetime
import importlib.util
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("pc", os.path.join(HERE, "..", "parse_calendar.py"))
pc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pc)

# Row titles reuse the wording of the previous year's PDF where the row is the same.
# Columns: Fall, Spring, Summer 1, Summer 2. "-" and "N/A" mean the semester has no such date.
ROWS = [
    ("Advising starts", "Monday, June 28, 2027", "Monday, November 01, 2027", "Monday, March 27, 2028", "Monday, March 27, 2028"),
    ("Registration for 4th year undergraduate students, Honored students, students with special needs, all Master and Doctoral students begins",
     "Monday, July 19, 2027", "Monday, November 29, 2027", "Monday, April 24, 2028", "Monday, April 24, 2028"),
    ("Registration for 3rd year undergraduate students begins",
     "Thursday, July 22, 2027", "Thursday, December 02, 2027", "Thursday, April 27, 2028", "Thursday, April 27, 2028"),
    ("Registration for freshmen",
     "Monday, July 26, 2027", "Monday, December 06, 2027", "Tuesday, May 02, 2028", "Tuesday, May 02, 2028"),
    ("Registration for 2nd year undergraduate students begins",
     "Thursday, July 29, 2027", "Thursday, December 09, 2027", "Thursday, May 04, 2028", "Thursday, May 04, 2028"),
    ("Registration for 1st year undergraduate students, as well as for non-degree students who have submitted an original document confirming completion of secondary or higher education",
     "Monday, August 02, 2027", "Monday, December 13, 2027", "Wednesday, May 10, 2028", "Wednesday, May 10, 2028"),
    ("Registration for non-degree students who have not submitted original educational documents (100% of tuition)",
     "Monday, August 09, 2027", "Friday, December 17, 2027", "Monday, May 15, 2028", "Monday, May 15, 2028"),
    ("Registration ends", "Tuesday, August 24, 2027", "Sunday, January 09, 2028", "Sunday, May 28, 2028", "Sunday, July 16, 2028"),
    ("Orientation sessions for freshmen", "Tuesday, August 24, 2027", "Saturday, January 15, 2028", "N/A", "N/A"),
    ("Classes start", "Wednesday, August 25, 2027", "Monday, January 10, 2028", "Monday, May 29, 2028", "Monday, July 17, 2028"),
    ("Internship starts", "Wednesday, August 25, 2027", "Monday, January 10, 2028", "Monday, May 29, 2028", "Monday, July 17, 2028"),
    ("Add/Drop period", "08/25/2027 - 08/31/2027", "01/10/2028 - 01/16/2028", "05/29/2028 - 05/30/2028", "07/17/2028 - 07/18/2028"),
    ("Late Registration period for all students (requires instructors' approval)",
     "09/1/2027 - 09/7/2027", "01/17/2028 - 01/23/2028", "05/31/2028 - 06/1/2028", "-"),
    ('Withdraw from a Course with 80% Tuition Refund with a "W" grade (online)',
     "09/1/2027 - 09/7/2027", "01/17/2028 - 01/23/2028", "-", "-"),
    ('Withdraw from a Course with 60% Tuition Refund with a "W" grade (online)',
     "09/8/2027 - 09/14/2027", "01/24/2028 - 01/30/2028", "-", "-"),
    ('Withdraw from a Course with 40% Tuition Refund with a "W" grade (online)',
     "09/15/2027 - 09/21/2027", "01/31/2028 - 02/6/2028", "-", "-"),
    ('Withdraw from a Course with 20% Tuition Refund with a "W" grade (online)',
     "09/22/2027 - 09/28/2027", "02/7/2028 - 02/13/2028", "-", "-"),
    ("Second Payment Deadline - Friday of 5th week (second 1/3 of total tuition is due)",
     "Friday, September 24, 2027", "Friday, February 11, 2028", "-", "-"),
    ('From this date there are no refunds for withdrawals from courses with a "W" grade (online)',
     "Wednesday, September 29, 2027", "Monday, February 14, 2028", "-", "-"),
    ("First Progress Submission deadline", "Monday, October 11, 2027", "Monday, February 28, 2028", "Monday, June 26, 2028", "N/A"),
    ('"I" grades from previous semester convert into "F"s (7\'th week of classes)',
     "10/4/2027 - 10/11/2027", "02/21/2028 - 02/28/2028", "-", "-"),
    ("Study Break", "10/11/2027 - 10/17/2027", "02/28/2028 - 03/5/2028", "-", "-"),
    ("Third Payment Deadline - Friday 9'th week (final 1/3 of total tuition is due)",
     "Friday, October 22, 2027", "Friday, March 10, 2028", "-", "-"),
    ("Second Progress Submission deadline", "Monday, November 22, 2027", "Monday, April 10, 2028", "N/A", "N/A"),
    ("Administrative Withdrawal from courses of students with overdue tuition balance (Friday 13'th week)",
     "Friday, November 19, 2027", "Friday, April 07, 2028", "-", "-"),
    ("Last day to reinstate after Administrative withdraw from courses due to the tuition balanse overdue (Friday 15'th week)",
     "Friday, December 03, 2027", "Friday, April 21, 2028", "-", "-"),
    ('Last Day to Withdraw from a Courses with a "W" grade (online)',
     "Tuesday, December 14, 2027", "Thursday, May 04, 2028", "Thursday, July 06, 2028", "Thursday, August 03, 2028"),
    ("Internship ends", "Tuesday, December 14, 2027", "Thursday, May 04, 2028", "Friday, July 07, 2028", "Friday, August 04, 2028"),
    ("Classes End", "Tuesday, December 14, 2027", "Thursday, May 04, 2028", "Friday, July 07, 2028", "Friday, August 04, 2028"),
    ("Final Assessments Starts, Examination Period and Final Attestation",
     "15 and 17-26 December 2027", "6, 8 and 10-19 May, 2028", "Friday, July 07, 2028", "Friday, August 04, 2028"),
    ("Semester Ends", "Sunday, December 26, 2027", "Friday, May 19, 2028", "Friday, July 07, 2028", "Friday, August 04, 2028"),
    ("Change Grade Forms due (2 weeks after semester ends, for Fall - 1st week of Spring classes)",
     "Monday, January 17, 2028", "Monday, June 05, 2028", "Friday, July 21, 2028", "Friday, August 18, 2028"),
    ("Graduation Ceremony", "-", "Saturday, May 27, 2028", "-", "-"),
]

# KIMEP is closed: (title, note, [raw date cell], column index of that cell)
CLOSED = [
    ("Republic Day", "(make-up classes are required for Monday classes scheduled on October 25, 2027)", "Monday, October 25, 2027"),
    ("Independence Day", "no make-ups required", "Thursday, December 16, 2027"),
    ("New Year's Day", "", "1-2 January, 2028"),
    ("Christmas", "", "Friday, January 07, 2028"),
    ("International Women's Day", "(make-up classes are required for Wednesday classes scheduled on March 8, 2028)", "Wednesday, March 08, 2028"),
    ("Constitution Day", "(make-up classes are required for Wednesday classes scheduled on March 15, 2028)", "Wednesday, March 15, 2028"),
    ("Nauryz Day", "no make-ups required", "21-23 March, 2028"),
    ("Kazakhstan People's Unity Day", "no make-ups required", "Monday, May 01, 2028"),
    ("Kurban Ait", "", "Friday, May 05, 2028"),
    ("Defender of the Fatherland Day", "", "Sunday, May 07, 2028"),
    ("Victory Day", "", "Tuesday, May 09, 2028"),
    ("Capital Day", "(make-up classes are required for Thursday classes scheduled on July 6, 2028)", "Thursday, July 06, 2028"),
]

WEEKDAY = re.compile(r"^(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday), (\w+) (\d{1,2}), (\d{4})$")


def check_weekday(cell):
    m = WEEKDAY.match(cell)
    if not m:
        return
    start, end, _ = pc.normalize_date(cell)
    day = datetime.date.fromisoformat(start)
    if day.strftime("%A") != m.group(1):
        sys.exit(f"weekday mismatch: {cell!r} is really a {day.strftime('%A')}")


def event(title, cell):
    start, end, display = pc.normalize_date(cell)
    if display is None:
        return None
    check_weekday(cell)
    return {"title": title, "start": start, "end": end, "display": display}


def main():
    semesters = [
        ("F", "Fall 2027"), ("S", "Spring 2028"), ("SU1", "Summer 1 2028"), ("SU2", "Summer 2 2028"),
    ]
    out = {"id": "2027-2028", "title": "Academic Calendar 2027-2028", "source": "manual", "semesters": [], "closed": []}
    for i, (sid, name) in enumerate(semesters):
        events = [e for row in ROWS if (e := event(row[0], row[1 + i]))]
        out["semesters"].append({"id": sid, "name": name, "events": events})

    for title, note, cell in CLOSED:
        start, end, display = pc.normalize_date(cell)
        check_weekday(cell)
        out["closed"].append({"title": title, "note": note, "dates": [{"start": start, "end": end, "display": display}]})

    # Sanity: the progress deadlines and Study Break should line up the way they do every year.
    for sem in out["semesters"][:2]:
        by = {e["title"]: e for e in sem["events"]}
        deadline = datetime.date.fromisoformat(by["First Progress Submission deadline"]["start"])
        brk = datetime.date.fromisoformat(by["Study Break"]["start"])
        assert deadline.weekday() == 0 and brk == deadline, (sem["id"], deadline, brk)
        second = datetime.date.fromisoformat(by["Second Progress Submission deadline"]["start"])
        assert second.weekday() == 0 and second > deadline

    path = os.path.join(HERE, "AY27-28.json")
    with open(path, "w") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
    print("wrote", path, [len(s["events"]) for s in out["semesters"]], "events;", len(out["closed"]), "closures")


if __name__ == "__main__":
    main()
