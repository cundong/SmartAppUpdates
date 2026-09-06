#!/usr/bin/env python3
"""Check files eligible for commit, never scan or print private Git metadata."""
from pathlib import Path
import re
import hashlib
import subprocess

root = Path(__file__).resolve().parents[1]
files = subprocess.check_output(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], cwd=root).split(b'\0')
approved_apks = {
    'Apks/淘宝v10.65.10.apk': 'a4dbcc28fafc204047c5d935f9aaa8a223e902a51971467b906b714f5b71de6d',
    'Apks/淘宝v10.65.20.apk': '24f8c5784857626f9fc784c9a8b8221779865ba01ddb15ef9670fcf14a4e77b4',
}
failures = []
for raw in set(files):
    if not raw:
        continue
    name = raw.decode('utf-8')
    path = root / name
    if not path.is_file():
        continue  # deletions from the legacy checkout are expected
    if name in approved_apks:
        if hashlib.sha256(path.read_bytes()).hexdigest() != approved_apks[name]:
            failures.append('Approved APK digest mismatch: ' + name)
        continue
    if (path.suffix.lower() in {'.apk', '.aar', '.aab', '.so', '.dylib', '.o', '.keystore', '.jks', '.p12', '.pfx'}
            or any(part in {'.idea', '.gradle', '.cxx', 'build'} for part in path.relative_to(root).parts)
            or path.name in {'.DS_Store', 'local.properties', '.env'}):
        failures.append('Non-source file eligible for commit: ' + name)
    if path.suffix.lower() in {'.jar', '.png'}:
        continue
    try:
        text = path.read_text(encoding='utf-8')
    except UnicodeDecodeError:
        continue
    if re.search(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----', text):
        failures.append('Private key marker: ' + name)
if failures:
    raise SystemExit('\n'.join(sorted(failures)))
print('Repository hygiene passed (approved APK digests verified; other binaries and private keys excluded).')
