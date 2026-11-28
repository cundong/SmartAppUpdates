#!/usr/bin/env python3
"""Check files eligible for commit, never scan or print private Git metadata."""
from pathlib import Path
import re
import subprocess

root = Path(__file__).resolve().parents[1]


def git_paths(*args):
    output = subprocess.check_output(['git', *args], cwd=root)
    return {name.decode('utf-8') for name in output.split(b'\0') if name}


magic = b'BSDIFF40'
files = git_paths('ls-files', '-z', '--cached', '--others', '--exclude-standard')
failures = []
# A staged patch can still be committed after the working copy is replaced or removed.
staged = git_paths('diff', '--cached', '--no-renames', '--name-only', '--diff-filter=AMT', '-z')
for name in sorted(staged & git_paths('diff', '--name-only', '-z')):
    with subprocess.Popen(['git', 'cat-file', 'blob', ':' + name], cwd=root,
                          stdout=subprocess.PIPE) as blob:
        with blob.stdout as stream:
            if stream.read(len(magic)) == magic:
                failures.append('Staged BSDIFF40 binary patch: ' + name)

for name in sorted(files):
    path = root / name
    if not path.is_file():
        continue  # deletions from the legacy checkout are expected
    if (path.suffix.lower() in {'.apk', '.aar', '.aab', '.so', '.dylib', '.o', '.keystore', '.jks', '.p12', '.pfx'}
            or any(part in {'.idea', '.gradle', '.cxx', 'build'} for part in path.relative_to(root).parts)
            or path.name in {'.DS_Store', 'local.properties', '.env'}):
        failures.append('Non-source file eligible for commit: ' + name)
        continue
    with path.open('rb') as stream:
        if stream.read(len(magic)) == magic:
            failures.append('BSDIFF40 binary patch eligible for commit: ' + name)
            continue
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
print('Repository hygiene passed (APKs, BSDIFF40 patches, generated files and private keys excluded).')
