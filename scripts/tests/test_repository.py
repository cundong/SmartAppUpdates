from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


class RepositoryCheckTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.script = self.root / 'scripts/check_repository.py'
        self.script.parent.mkdir()
        shutil.copyfile(Path(__file__).resolve().parents[1] / self.script.name, self.script)
        subprocess.run(['git', 'init', '-q'], cwd=self.root, check=True)

    def check(self):
        return subprocess.run([sys.executable, str(self.script)], cwd=self.root,
                              capture_output=True, text=True)

    def test_text_patch_and_ignored_binary_pass(self):
        (self.root / '.gitignore').write_text('update.patch\n')
        (self.root / 'update.patch').write_bytes(b'BSDIFF40' + bytes(24))
        (self.root / 'change.patch').write_text('diff --git a/test b/test\n+BSDIFF40\n')
        result = self.check()
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_binary_header_is_checked_regardless_of_extension(self):
        (self.root / 'sample.png').write_bytes(b'BSDIFF40' + bytes(24))
        result = self.check()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('BSDIFF40 binary patch eligible for commit: sample.png', result.stderr)

    def test_force_added_patch_is_checked_even_after_worktree_changes(self):
        path = self.root / 'update.patch'
        (self.root / '.gitignore').write_text(path.name + '\n')
        path.write_bytes(b'BSDIFF40' + bytes(24))
        subprocess.run(['git', 'add', '-f', path.name], cwd=self.root, check=True)
        for state in ('staged', 'replaced', 'deleted'):
            with self.subTest(state=state):
                if state == 'replaced':
                    path.write_text('ordinary text')
                elif state == 'deleted':
                    path.unlink()
                result = self.check()
                self.assertNotEqual(result.returncode, 0)
                self.assertIn('BSDIFF40', result.stderr)
                self.assertIn(path.name, result.stderr)
