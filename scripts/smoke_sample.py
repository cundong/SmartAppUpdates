"""Install the public fixture v1 and verify a Sample-driven update to v2 on an emulator."""
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1] / 'ApkPatchLibrarySample'
APP = 'com.cundong.apkpatch.example'
FIXTURE = 'com.cundong.smartappupdates.fixture'


def adb(*args):
    return subprocess.check_output(['adb', *args], timeout=45)


def tap_button(resource_ids, packages):
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        adb('shell', 'uiautomator', 'dump', '/data/local/tmp/smartappupdates-ui.xml')
        tree = ET.fromstring(adb('shell', 'cat', '/data/local/tmp/smartappupdates-ui.xml'))
        for node in tree.iter('node'):
            if (node.get('package') in packages and node.get('resource-id') in resource_ids
                    and node.get('enabled') == 'true'):
                left, top, right, bottom = map(int, re.findall(r'\d+', node.attrib['bounds']))
                adb('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))
                return
        time.sleep(1)
    raise SystemExit(f'{resource_ids} did not appear in {packages}')


def main():
    if adb('shell', 'getprop', 'ro.kernel.qemu').strip() != b'1':
        raise SystemExit('Select a test emulator with ANDROID_SERIAL.')
    variants = ROOT / 'fixture/build/outputs/apk'
    baseline, = (variants / 'baseline/debug').glob('*.apk')
    updated, = (variants / 'updated/debug').glob('*.apk')
    adb('install', '-r', '-d', str(baseline))
    adb('install', '-r', str(ROOT / 'app/build/outputs/apk/debug/app-debug.apk'))
    adb('shell', 'appops', 'set', APP, 'REQUEST_INSTALL_PACKAGES', 'allow')
    adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    adb('shell', 'wm', 'dismiss-keyguard')
    adb('shell', 'am', 'start', '-W', '-n', APP + '/.MainActivity')
    tap_button({APP + ':id/start_btn'}, {APP})
    tap_button({'android:id/button1', 'com.android.packageinstaller:id/button1'},
               {'com.android.packageinstaller', 'com.google.android.packageinstaller',
                'com.android.permissioncontroller', 'com.google.android.permissioncontroller'})
    expected = updated.read_bytes()
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        path = adb('shell', 'pm', 'path', FIXTURE).decode().strip()
        if path.startswith('package:') and adb('exec-out', 'cat', path[len('package:'):]) == expected:
            print('Installed v2 matches the target APK byte for byte.')
            return
        time.sleep(0.5)
    raise SystemExit('Installed fixture does not match the target APK')


if __name__ == '__main__':
    main()
