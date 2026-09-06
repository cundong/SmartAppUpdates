#!/usr/bin/env python3
"""Check files eligible for commit, never scan or print private Git metadata."""
from pathlib import Path
import re
import subprocess

root = Path(__file__).resolve().parents[1]
files = subprocess.check_output(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], cwd=root).split(b'\0')
failures = []
for raw in set(files):
    if not raw:
        continue
    name = raw.decode('utf-8')
    path = root / name
    if not path.is_file():
        continue  # deletions from the legacy checkout are expected
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
print('Repository hygiene passed (APKs, generated files and private keys excluded).')
