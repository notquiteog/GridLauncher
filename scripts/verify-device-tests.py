#!/usr/bin/env python3
"""Fail CI when device tests are missing, skipped, or failed, including installation errors."""
import pathlib
import re
import xml.etree.ElementTree as ET
expected = set()
for source in pathlib.Path('app/src/androidTest').rglob('*.kt'):
    expected.update(re.findall(r'@Test\s+fun\s+(\w+)', source.read_text()))
reports = list(pathlib.Path('app/build/outputs/androidTest-results/connected').rglob('TEST-*.xml'))
assert reports, 'No device test reports were produced'
passed = set()
for report in reports:
    tree = ET.parse(report)
    for case in tree.iter('testcase'):
        assert case.find('failure') is None and case.find('error') is None and case.find('skipped') is None, f"Device test did not pass: {case.attrib}"
        passed.add(case.attrib['name'])
assert expected and expected <= passed, f'Missing device tests: {expected - passed}'
print(f'Verified {len(expected)} Android device tests passed')
