import argparse
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

MODULE = Path(__file__).resolve().parents[1] / 'prepare_fixtures.py'
spec = importlib.util.spec_from_file_location('fixtures', MODULE)
fixtures = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixtures)


class FixtureTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        (self.root / 'Apks').mkdir()
        (self.root / 'scripts').mkdir()
        (self.root / 'scripts/prepare_fixtures.py').write_text('generator-v1')
        (self.root / 'build.gradle').write_text('build-v1')
        config = {'packageName': 'com.example.fixture'}
        for role, data, version in [('old', b'old fixture', 1), ('new', b'new fixture', 2)]:
            path = self.root / 'Apks' / (role + '.apk')
            path.write_bytes(data)
            config[role] = dict(file=path.name, sha256=fixtures.sha256(path),
                                versionName=str(version), versionCode=version)
        (self.root / 'Apks/fixtures.json').write_text(json.dumps(config))
        self.worker = self.root / 'fake-java'
        self.worker.write_text('''#!/usr/bin/env python3
from pathlib import Path
import sys
root = Path(__file__).parent
with (root / 'calls').open('a') as calls:
    calls.write(sys.argv[-4] + '\\n')
if (root / 'fail').exists():
    raise SystemExit(70)
operation, old, new, patch = sys.argv[-4:]
if operation == 'diff':
    Path(patch).write_bytes(Path(new).read_bytes())
else:
    Path(new).write_bytes(b'bad output' if (root / 'corrupt').exists() else Path(patch).read_bytes())
''')
        self.worker.chmod(0o755)
        self.library = self.root / 'native'
        self.library.mkdir()
        (self.library / 'library.so').write_bytes(b'compiled-v1')
        self.args = argparse.Namespace(java=str(self.worker), classpath=str(self.root / 'classes'),
                library_path=str(self.library), output=self.root / 'output',
                timeout_seconds=2, lock_timeout_seconds=2)

    def prepare(self):
        return fixtures.prepare(self.args, self.root)

    def calls(self):
        path = self.root / 'calls'
        return path.read_text().splitlines() if path.exists() else []

    def test_cache_hit_does_not_regenerate(self):
        first = self.prepare()
        self.assertEqual(first, self.prepare())
        self.assertEqual(['diff', 'patch'], self.calls())

    def test_bad_original_is_rejected_before_worker(self):
        (self.root / 'Apks/old.apk').write_bytes(b'wrong input')
        with self.assertRaisesRegex(ValueError, 'SHA-256 mismatch'):
            self.prepare()
        self.assertEqual([], self.calls())
        self.assertFalse((self.args.output / 'current').exists())

    def test_missing_input_is_rejected(self):
        (self.root / 'Apks/new.apk').unlink()
        with self.assertRaisesRegex(ValueError, 'Missing local APK'):
            self.prepare()

    def test_generator_build_and_binary_changes_invalidate(self):
        self.prepare()
        for filename in ('scripts/prepare_fixtures.py', 'build.gradle', 'native/library.so'):
            with self.subTest(filename=filename):
                before = len(self.calls())
                path = self.root / filename
                path.write_bytes(path.read_bytes() + b' changed')
                self.prepare()
                self.assertEqual(before + 2, len(self.calls()))

    def test_corrupt_cache_and_metadata_regenerate(self):
        self.prepare()
        for filename, data in [('assets/update.patch', 'corrupt'), ('verified.json', 'null'),
                               (fixtures.JAVA_METADATA, 'wrong constants')]:
            with self.subTest(filename=filename):
                before = len(self.calls())
                (self.args.output / 'current' / filename).write_text(data)
                self.prepare()
                self.assertEqual(before + 2, len(self.calls()))

    def test_failed_generation_preserves_previous_snapshot(self):
        self.prepare()
        previous = (self.args.output / 'current').resolve()
        (self.root / 'build.gradle').write_text('changed')
        (self.root / 'fail').touch()
        with self.assertRaises(subprocess.CalledProcessError):
            self.prepare()
        self.assertEqual(previous, (self.args.output / 'current').resolve())
        self.assertEqual([], list((self.args.output / 'generations').glob('.prepare-*')))

    def test_wrong_roundtrip_is_never_published(self):
        (self.root / 'corrupt').touch()
        with self.assertRaisesRegex(ValueError, 'does not match'):
            self.prepare()
        self.assertFalse((self.args.output / 'current').exists())
        self.assertEqual([], list((self.args.output / 'generations').iterdir()))

    def test_failed_publication_keeps_previous_generation(self):
        self.prepare()
        previous = (self.args.output / 'current').resolve()
        (self.root / 'build.gradle').write_text('new build')
        with patch.object(fixtures.os, 'replace', side_effect=OSError('injected publication failure')):
            with self.assertRaises(OSError):
                self.prepare()
        self.assertEqual(previous, (self.args.output / 'current').resolve())
        self.assertEqual(1, len(list((self.args.output / 'generations').iterdir())))

    def test_lock_has_timeout_and_releases_on_exception(self):
        with self.assertRaisesRegex(ValueError, 'test'):
            with fixtures.output_lock(self.args.output, 1):
                with self.assertRaises(TimeoutError):
                    with fixtures.output_lock(self.args.output, 0.01):
                        self.fail('second lock acquired')
                raise ValueError('test')
        with fixtures.output_lock(self.args.output, 0.01):
            pass

    def test_concurrent_processes_generate_only_once(self):
        code = '''import argparse, importlib.util, pathlib, sys
spec=importlib.util.spec_from_file_location('fixtures',sys.argv[1]);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
r=pathlib.Path(sys.argv[2]);a=argparse.Namespace(java=str(r/'fake-java'),classpath=str(r/'classes'),library_path=str(r/'native'),output=r/'output',timeout_seconds=2,lock_timeout_seconds=3)
m.prepare(a,r)
'''
        command = [sys.executable, '-c', code, str(MODULE), str(self.root)]
        processes = [subprocess.Popen(command, stdout=subprocess.DEVNULL) for _ in range(2)]
        try:
            for process in processes:
                self.assertEqual(0, process.wait(timeout=10))
        finally:
            for process in processes:
                if process.poll() is None:
                    process.kill()
                    process.wait()
        self.assertEqual(['diff', 'patch'], self.calls())

    def test_timeout_terminates_process_group(self):
        pidfile = self.root / 'child.pid'
        program = '''import subprocess, sys, time
from pathlib import Path
child=subprocess.Popen([sys.executable,'-c','import time; time.sleep(60)'])
Path(sys.argv[1]).write_text(str(child.pid))
time.sleep(60)
'''
        with self.assertRaises(subprocess.TimeoutExpired):
            fixtures.run_process([sys.executable, '-c', program, str(pidfile)], 0.5)
        self.assertTrue(pidfile.exists())
        child_pid = int(pidfile.read_text())
        deadline = time.monotonic() + 3
        while True:
            result = subprocess.run(['ps', '-p', str(child_pid), '-o', 'stat='], capture_output=True, text=True)
            status = result.stdout.strip()
            if not status or status.startswith('Z'):
                break
            if time.monotonic() >= deadline:
                self.fail('descendant survived timeout: ' + status)
            time.sleep(0.05)


if __name__ == '__main__':
    unittest.main()
