"""Exercise chunk boundaries and enforce a peak memory budget in a fresh decoder."""
import bz2
import hashlib
import os
from pathlib import Path
import sys
import tempfile

MIB = 1024 * 1024
OLD_SIZE = 128 * MIB


def offset(value):
    return (abs(value) | (1 << 63 if value < 0 else 0)).to_bytes(8, 'little')


def run(binary, directory):
    old, new, patch = (directory / name for name in ('old', 'new', 'update.patch'))
    # The diff starts three bytes before the old file and ends three bytes past EOF.
    # An extra segment also crosses the decoder's 64 KiB buffer boundary.
    extra = b'!' * 65537
    controls = bz2.compress(b''.join(map(offset, (0, 0, -3, OLD_SIZE + 6, len(extra), 0))))
    encoder = bz2.BZ2Compressor()
    chunk = bytes(range(256)) * 4096
    zeros = bytes(MIB)
    compressed_diff = bytearray()
    expected = hashlib.sha256(bytes(3))
    with old.open('wb') as stream:
        for _ in range(OLD_SIZE // MIB):
            stream.write(chunk)
            expected.update(chunk)
            compressed_diff.extend(encoder.compress(zeros))
    compressed_diff.extend(encoder.compress(bytes(6)) + encoder.flush())
    expected.update(bytes(3) + extra)
    header = b'BSDIFF40' + offset(len(controls)) + offset(len(compressed_diff)) + offset(OLD_SIZE + 6 + len(extra))
    patch.write_bytes(header + controls + compressed_diff + bz2.compress(extra))

    pid = os.posix_spawn(binary, [binary, str(old), str(new), str(patch)], os.environ)
    _, status, usage = os.wait4(pid, 0)
    if status != 0:
        raise SystemExit(f'Decoder failed with wait status {status}')
    actual = hashlib.sha256()
    with new.open('rb') as stream:
        for data in iter(lambda: stream.read(MIB), b''):
            actual.update(data)
    if actual.digest() != expected.digest():
        raise SystemExit('Large reconstruction digest mismatch')
    peak_bytes = usage.ru_maxrss * (1 if sys.platform == 'darwin' else 1024)
    print(f'{new.stat().st_size / MIB:.2f} MiB output, SHA-256 verified, peak RSS {peak_bytes / MIB:.2f} MiB', flush=True)
    if peak_bytes >= 64 * MIB:
        raise SystemExit('Decoder exceeded the 64 MiB RSS budget')


if __name__ == '__main__':
    with tempfile.TemporaryDirectory() as temporary:
        run(sys.argv[1], Path(temporary))
