#!/usr/bin/env python3
"""Fail CI when device tests are missing, skipped, or failed, including installation errors.

With --class, only that one test class is required. A filtered run exists to record screenshots, and
demanding the whole suite there would fail for the wrong reason - while still refusing to accept a
filtered run in which nothing actually ran.

With --exclude-class, a class is left out of a full-suite check entirely. The expanded-posture
screenshots skip themselves on a 411dp phone and are run deliberately afterwards on a resized display,
so the ordinary run must not demand them - and must not read their skip as a failure.
"""
import argparse
import pathlib
import re
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--class', dest='only', default=None, help='require only this fully qualified test class')
parser.add_argument('--exclude-class', dest='skip', action='append', default=[],
                    help='leave this fully qualified test class out of the check')
args = parser.parse_args()

excluded = set(args.skip)
sources = list(pathlib.Path('app/src/androidTest').rglob('*.kt'))
if args.only:
    simple = args.only.rsplit('.', 1)[-1] + '.kt'
    sources = [s for s in sources if s.name == simple]
    assert sources, f'No source file for {args.only}'
sources = [s for s in sources if s.stem not in {c.rsplit('.', 1)[-1] for c in excluded}]
assert sources, 'No test sources left to check'
expected = set()
for source in sources:
    expected.update(re.findall(r'@Test\s+fun\s+(\w+)', source.read_text()))

reports = list(pathlib.Path('app/build/outputs/androidTest-results/connected').rglob('TEST-*.xml'))
assert reports, 'No device test reports were produced'
passed = set()
for report in reports:
    tree = ET.parse(report)
    for case in tree.iter('testcase'):
        if case.attrib.get('classname') in excluded:
            continue
        assert case.find('failure') is None and case.find('error') is None and case.find('skipped') is None, f"Device test did not pass: {case.attrib}"
        if args.only is None or case.attrib.get('classname') == args.only:
            passed.add(case.attrib['name'])
assert expected and expected <= passed, f'Missing device tests: {expected - passed}'
print(f'Verified {len(expected)} Android device tests passed')