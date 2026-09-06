#!/usr/bin/env python3
"""Build verified Taobao Sample inputs with bounded workers and atomic publication."""
import argparse
from contextlib import contextmanager
import fcntl
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import tempfile
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
SCHEMA = 2


def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


@contextmanager
def output_lock(out, timeout):
    """Keep the lock inode: unlinking it could allow a second independent lock."""
    out.mkdir(parents=True, exist_ok=True)
    with (out / '.prepare.lock').open('a') as lock:
        deadline = time.monotonic() + timeout
        while True:
            try:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                break
            except BlockingIOError:
                if time.monotonic() >= deadline:
                    raise TimeoutError('Another fixture preparation holds the output lock')
                time.sleep(min(0.05, max(0, deadline - time.monotonic())))
        try:
            yield
        finally:
            fcntl.flock(lock, fcntl.LOCK_UN)


def run_process(command, timeout):
    """Isolate CLI and its JVM worker in one process group; reap on any exit path."""
    process = subprocess.Popen(command, start_new_session=True)
    try:
        result = process.wait(timeout=timeout)
        if result:
            raise subprocess.CalledProcessError(result, command)
    finally:
        # Killing the group also catches descendants left behind by a failed CLI.
        try:
            os.killpg(process.pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
        try:
            process.wait(timeout=2)
        except subprocess.TimeoutExpired:
            pass
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        process.wait()


def validate_inputs(root):
    config = json.loads((root / 'Apks/fixtures.json').read_text())
    if not isinstance(config, dict) or not isinstance(config.get('packageName'), str):
        raise ValueError('Invalid fixture manifest')
    paths = {}
    for role in ('old', 'new'):
        spec = config[role]
        name = spec['file']
        if not isinstance(name, str) or Path(name).name != name or not name.endswith('.apk'):
            raise ValueError('Fixture must name an APK inside Apks/')
        if not isinstance(spec['sha256'], str) or not re.fullmatch('[a-f0-9]{64}', spec['sha256']):
            raise ValueError('Invalid SHA-256 in fixture manifest')
        if (not isinstance(spec.get('versionName'), str)
                or type(spec.get('versionCode')) is not int or spec['versionCode'] < 1):
            raise ValueError('Invalid APK version metadata')
        path = root / 'Apks' / name
        if not path.is_file():
            raise ValueError('Missing local APK: ' + str(path))
        if sha256(path) != spec['sha256']:
            raise ValueError(role + ' APK SHA-256 mismatch')
        paths[role] = path
    if config['old']['versionCode'] >= config['new']['versionCode']:
        raise ValueError('New APK versionCode must be greater than old APK versionCode')
    return config, paths


def source_fingerprint(root, args):
    """Include generator, build settings, tool identity and actual compiled inputs."""
    digest = hashlib.sha256(('fixture-schema=' + str(SCHEMA)).encode())
    files = {root / 'Apks/fixtures.json', root / 'scripts/prepare_fixtures.py',
             root / 'build.gradle', root / 'settings.gradle', root / 'gradle.properties',
             root / 'gradle/wrapper/gradle-wrapper.properties',
             root / 'ApkPatchLibraryServer/build.gradle', root / 'ApkPatchLibrary/build.gradle'}
    for subdir in ('ApkPatchLibrary/src/main/cpp', 'ApkPatchLibraryServer/jni',
                   'ApkPatchLibraryServer/src/com'):
        files.update((root / subdir).rglob('*'))
    for path in sorted(files):
        if path.is_file():
            digest.update(str(path.relative_to(root)).encode())
            digest.update(bytes.fromhex(sha256(path)))
    for item in [args.java, args.library_path, *args.classpath.split(os.pathsep)]:
        path = Path(item).resolve()
        digest.update(str(path).encode())
        for file in sorted(path.rglob('*')) if path.is_dir() else [path]:
            if file.is_file():
                digest.update(str(file).encode())
                digest.update(bytes.fromhex(sha256(file)))
    return digest.hexdigest()


def metadata_source(config, patch_sha):
    values = {'OLD_APK_SHA256': config['old']['sha256'], 'NEW_APK_SHA256': config['new']['sha256'],
              'PATCH_APK_SHA256': patch_sha, 'PACKAGE_NAME': config['packageName'],
              'NEW_VERSION_NAME': config['new']['versionName']}
    code = 'package com.cundong.apkpatch.example;\n\n// Generated by prepare_fixtures.py. Do not edit.\nfinal class FixtureMetadata {\n'
    code += ''.join('    static final String ' + key + ' = ' + json.dumps(value) + ';\n' for key, value in values.items())
    return code + '    static final long NEW_VERSION_CODE = ' + str(config['new']['versionCode']) + 'L;\n}\n'


JAVA_METADATA = 'java/com/cundong/apkpatch/example/FixtureMetadata.java'


def cached_result(out, fingerprint, config):
    try:
        current = out / 'current'
        if not current.is_symlink():
            return None
        generation = current.resolve(strict=True)
        if generation.parent != (out / 'generations').resolve():
            return None
        cached = json.loads((generation / 'verified.json').read_text())
        if not isinstance(cached, dict):
            return None
        if (cached.get('schema') != SCHEMA or cached.get('fingerprint') != fingerprint
                or cached.get('roundTripVerified') is not True
                or cached.get('oldSha256') != config['old']['sha256']
                or cached.get('newSha256') != config['new']['sha256']
                or not isinstance(cached.get('patchSha256'), str)):
            return None
        if (sha256(generation / 'assets/old.apk') != config['old']['sha256']
                or sha256(generation / 'assets/update.patch') != cached['patchSha256']
                or (generation / JAVA_METADATA).read_text() != metadata_source(config, cached['patchSha256'])):
            return None
        return cached
    except (OSError, ValueError, KeyError, TypeError):
        return None


def publish(out, stage):
    """The one atomic symlink replacement publishes assets AND metadata together."""
    generation = out / 'generations' / ('verified-' + uuid.uuid4().hex)
    stage.rename(generation)
    pointer = out / ('.current-' + uuid.uuid4().hex)
    try:
        pointer.symlink_to(generation.relative_to(out), target_is_directory=True)
        os.replace(pointer, out / 'current')
    except BaseException:
        pointer.unlink(missing_ok=True)
        shutil.rmtree(generation)
        raise


def refresh_aliases(out):
    # Compatibility paths for humans; Gradle consumes current/ directly.
    for name in ('assets', 'java', 'verified.json'):
        alias = out / name
        target = Path('current') / name
        if alias.is_symlink() and os.readlink(alias) == str(target):
            continue
        if alias.is_dir() and not alias.is_symlink():
            shutil.rmtree(alias)  # legacy generated directory, never an input APK directory
        elif alias.exists() or alias.is_symlink():
            alias.unlink()
        alias.symlink_to(target, target_is_directory=name != 'verified.json')


def prepare(args, root=None):
    root = root or ROOT
    out = args.output.resolve()
    with output_lock(out, args.lock_timeout_seconds):
        config, paths = validate_inputs(root)
        fingerprint = source_fingerprint(root, args)
        cached = cached_result(out, fingerprint, config)
        if cached is not None:
            refresh_aliases(out)
            print('Verified cached Taobao fixtures: ' + json.dumps(cached), flush=True)
            return cached
        (out / 'generations').mkdir(exist_ok=True)
        java = [args.java, '-Djava.library.path=' + str(Path(args.library_path).resolve()),
                '-cp', args.classpath, 'com.cundong.cli.ApkPatchCli',
                '--timeout-seconds', str(args.timeout_seconds)]
        with tempfile.TemporaryDirectory(prefix='.prepare-', dir=out / 'generations') as temporary:
            stage = Path(temporary)
            (stage / 'assets').mkdir()
            old = stage / 'assets/old.apk'
            expected = stage / 'expected.apk'
            patch = stage / 'assets/update.patch'
            rebuilt = stage / 'rebuilt.apk'
            # Work on private copies so edits to the originals cannot race generation.
            for role, destination in [('old', old), ('new', expected)]:
                shutil.copyfile(paths[role], destination)
                if sha256(destination) != config[role]['sha256']:
                    raise ValueError(role + ' APK changed while copying')
            print('Generating Taobao delta from verified input copies...', flush=True)
            run_process(java + ['diff', str(old), str(expected), str(patch)], args.timeout_seconds + 5)
            run_process(java + ['patch', str(old), str(rebuilt), str(patch)], args.timeout_seconds + 5)
            if sha256(rebuilt) != config['new']['sha256']:
                raise ValueError('Rebuilt APK does not match the new Taobao APK')
            with rebuilt.open('rb') as left, expected.open('rb') as right:
                while True:
                    block = left.read(1024 * 1024)
                    if block != right.read(1024 * 1024):
                        raise ValueError('Round-trip byte comparison failed')
                    if not block:
                        break
            cached = {'schema': SCHEMA, 'fingerprint': fingerprint, 'patchSha256': sha256(patch),
                      'oldSha256': config['old']['sha256'], 'newSha256': config['new']['sha256'],
                      'patchBytes': patch.stat().st_size, 'newBytes': expected.stat().st_size,
                      'roundTripVerified': True}
            generated_java = stage / JAVA_METADATA
            generated_java.parent.mkdir(parents=True)
            generated_java.write_text(metadata_source(config, cached['patchSha256']))
            (stage / 'verified.json').write_text(json.dumps(cached, indent=2) + '\n')
            expected.unlink()
            rebuilt.unlink()
            publish(out, stage)
        refresh_aliases(out)
        print('Verified local Taobao fixtures: ' + json.dumps(cached), flush=True)
        return cached


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', default=shutil.which('java') or 'java')
    parser.add_argument('--classpath', default=str(ROOT / 'ApkPatchLibraryServer/build/classes/java/main'))
    parser.add_argument('--library-path', default=str(ROOT / 'ApkPatchLibraryServer/build/native'))
    parser.add_argument('--output', type=Path, default=ROOT / 'ApkPatchLibrarySample/app/build/generated/fixtures')
    parser.add_argument('--timeout-seconds', type=int, default=900)
    parser.add_argument('--lock-timeout-seconds', type=float, default=900)
    args = parser.parse_args()
    if not 1 <= args.timeout_seconds <= 86400:
        parser.error('--timeout-seconds must be between 1 and 86400')
    if not math.isfinite(args.lock_timeout_seconds) or args.lock_timeout_seconds < 0:
        parser.error('--lock-timeout-seconds must be finite and nonnegative')
    def cancelled(signum, frame):
        raise SystemExit(128 + signum)
    signal.signal(signal.SIGTERM, cancelled)
    prepare(args)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, KeyError, TypeError, subprocess.SubprocessError) as error:
        raise SystemExit('Fixture preparation failed: ' + str(error))
