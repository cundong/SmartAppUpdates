"""Check Taobao reconstruction and the system confirmation screen on a selected phone."""
import json
import os
from pathlib import Path
import re
import time

from smoke_sample import APP, ROOT, adb, tap_button, tap_node, ui_nodes


INSTALLERS = {'com.miui.packageinstaller', 'com.android.packageinstaller',
              'com.google.android.packageinstaller', 'com.android.permissioncontroller',
              'com.google.android.permissioncontroller'}


def active_sessions():
    """Map each active session ID to its own fields, excluding all historical sessions."""
    dump = adb('shell', 'dumpsys', 'package', 'installs').decode()
    active = dump.partition('Historical install sessions:')[0]
    parts = re.split(r'(?m)^\s*Active Session (\d+):\s*$', active)
    return dict(zip(parts[1::2], parts[2::2]))


def start_case(case):
    nodes = {n.get('resource-id'): n for n in ui_nodes() if n.get('package') == APP}
    for field, expected in [('fixture_name', case['displayName']),
                            ('old_version', case['old']['versionName']),
                            ('new_version', case['new']['versionName'])]:
        node = nodes.get(APP + ':id/' + field)
        if node is None or node.get('text') != expected:
            raise SystemExit('Unlock the phone and build the demo with -PsampleFixture=taobao-10.59.')
    button = nodes.get(APP + ':id/start_btn')
    if button is None or button.get('enabled') != 'true':
        raise SystemExit('The demo is busy. Finish its previous operation and retry.')
    tap_node(button)


def wait_for_confirmation(package, previous):
    required = {'installerPackageName=' + APP, 'appPackageName=' + package, 'mCommitted=true'}
    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        nodes = ui_nodes()
        packages = {n.get('package') for n in nodes}
        if packages & INSTALLERS:
            for session, description in active_sessions().items():
                if session not in previous and required.issubset(description.split()):
                    print(f'System confirmation opened for {package} (session {session}).', flush=True)
                    return nodes
        if 'com.android.settings' in packages:
            raise SystemExit('Allow this demo to install unknown apps on the phone, then rerun.')
        time.sleep(0.5)
    raise SystemExit('No new committed Taobao session with its system confirmation in the foreground.')


def main():
    if not os.environ.get('ANDROID_SERIAL'):
        raise SystemExit('Set ANDROID_SERIAL to the test phone serial from adb devices.')
    case = json.loads((ROOT.parent / 'Apks/taobao-10.59/fixtures.json').read_text())
    sample = ROOT / 'app/build/outputs/apk/debug/app-debug.apk'
    if not sample.is_file():
        raise SystemExit('Run ./gradlew -PsampleFixture=taobao-10.59 :app:assembleDebug first.')
    adb('install', '-r', str(sample))
    adb('shell', 'am', 'start', '-S', '-W', '-n', APP + '/.MainActivity')
    previous = set(active_sessions())
    start_case(case)
    prompt = wait_for_confirmation(case['packageName'], previous)

    # Return to the demo and read the digest calculated from its actual output file.
    cancel = next((n for n in prompt if n.get('package') in INSTALLERS
                   and n.get('text') in {'取消', 'Cancel', 'CANCEL'}), None)
    if cancel is None:
        adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    else:
        tap_node(cancel)
    tap_button({APP + ':id/details_btn'}, {APP})
    message = next((n.get('text', '') for n in ui_nodes() if n.get('package') == APP
                    and n.get('resource-id') == 'android:id/message'), '')
    if (case['new']['sha256'] not in message.splitlines()
            or not any(Path(line).name == 'rebuilt.apk' for line in message.splitlines())):
        raise SystemExit('The demo output path or SHA-256 did not match the Taobao case.')
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    print(f"Reconstructed {case['new']['versionName']}, SHA-256 {case['new']['sha256']}. "
          "Installer confirmation dismissed.")


if __name__ == '__main__':
    main()
