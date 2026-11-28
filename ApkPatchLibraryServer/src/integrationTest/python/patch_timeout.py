"""A real CLI timeout after the native decoder has started writing the output."""
import bz2
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time


def word(value):
    return value.to_bytes(8, 'little')


def check_timeout(java, jar, directory):
    old = directory / 'old'
    patch = directory / 'update.patch'
    old.write_bytes(b'old')
    # Emit a marker first, then copy the first byte of old repeatedly. Small control
    # tuples keep the decoder busy without needing a large input or output file.
    marker = b'streaming-output-'
    copies = 1 << 20
    rewind_one = word((1 << 63) + 1)
    controls = bz2.compress(word(0) + word(len(marker)) + word(0)
                            + (word(1) + word(0) + rewind_one) * copies, compresslevel=1)
    diff = bz2.compress(bytes(copies))
    patch.write_bytes(b'BSDIFF40' + word(len(controls)) + word(len(diff)) + word(len(marker) + copies)
                      + controls + diff + bz2.compress(marker))

    output_dir = directory / 'out'
    output_dir.mkdir()
    # Keep previous results and other simultaneous invocations outside this run's cleanup.
    neighbor = output_dir / '.apk-patcher-neighbor'
    neighbor.mkdir()
    sentinel = neighbor / 'keep'
    sentinel.write_bytes(b'keep')
    command = [java, '-jar', jar, '--timeout-seconds', '5', 'patch', old, output_dir / 'new', patch]
    with subprocess.Popen(command) as cli:
        # Pause only this CLI's worker after observing decoded bytes. This makes timeout
        # coverage independent of disk throughput and exercises forced worker termination.
        worker = None
        paused = False
        deadline = time.monotonic() + 10
        while cli.poll() is None and not paused and time.monotonic() < deadline:
            if worker is None:
                children = subprocess.run(['pgrep', '-P', str(cli.pid)], capture_output=True)
                if children.returncode == 1:
                    time.sleep(0.01)
                    continue
                children.check_returncode()
                worker = int(children.stdout.strip())
            for candidate in output_dir.rglob('*'):
                if not candidate.is_file():
                    continue
                with candidate.open('rb') as stream:
                    if stream.read(len(marker)) != marker:
                        continue
                os.kill(worker, signal.SIGSTOP)
                paused = True
                break
            if not paused:
                time.sleep(0.01)
        exit_code = cli.wait(timeout=12)
    if not paused or exit_code != 124:
        raise AssertionError(f'Expected timeout during native write, paused={paused}, exit={exit_code}')
    remaining = sorted(p.relative_to(output_dir).as_posix() for p in output_dir.rglob('*'))
    if remaining != [neighbor.name, neighbor.name + '/keep'] or sentinel.read_bytes() != b'keep':
        raise AssertionError(f'Unexpected files after timeout: {remaining}')
    print('Real decoder timeout passed: exit 124, no output or residue, neighbor preserved.')


if __name__ == '__main__':
    with tempfile.TemporaryDirectory() as tmp:
        check_timeout(sys.argv[1], sys.argv[2], Path(tmp))
