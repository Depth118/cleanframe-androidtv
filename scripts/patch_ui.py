"""Adapt an upstream CleanFrame app/ copy for Android TV, in place.

Usage: python3 -I scripts/patch_ui.py <home-dir> <android-device.js>
"""
import shutil
import sys
from pathlib import Path

home, device = Path(sys.argv[1]), Path(sys.argv[2])
shutil.copyfile(device, home / "device.js")

# Exact upstream strings -> Android TV equivalents. Every one must match.
MAIN = [
    ("appId: 'IkWsFHtOY9.Stremio'", "appId: 'com.stremio.one'"),
    ("appId: 'org.tizen.netflix-app'", "appId: 'com.netflix.ninja'"),
    ("appId: 'MCmYXNxgcu.DisneyPlus'", "appId: 'com.disney.disneyplus'"),
    ("appId: '9Ur5IzDKqV.TizenYouTube'", "appId: 'com.google.android.youtube.tv'"),
    ("appId: 'com.samsung.tv.aria-video'", "appId: 'com.apple.atve.androidtv.appletv'"),
    ("appId: 'org.tizen.primevideo'", "appId: 'com.amazon.amazonvideo.livingroom'"),
    ("appId: 'mWK87Xghap.ABCiview'", "appId: 'au.net.abc.iview'"),
    ("appId: 'OyE7OuxlUi.SBSOnDemand'", "appId: 'com.sbs.ondemand.tv'"),
    ("appId: 'org.tizen.apple.apple-music'", "appId: 'com.apple.android.music'"),
    ("line: 'Open Samsung Home?'", "line: 'Open Android TV Home?'"),
    ("['samsung', 'Samsung Home']", "['samsung', 'Android TV Home']"),
    ("label: 'Samsung', value: 'Home and TV settings'", "label: 'Android TV', value: 'Home and TV settings'"),
    ("samsung: 'Samsung' }", "samsung: 'Android TV' }"),
    ("label: 'Samsung Home', description: 'Open Samsung’s Home screen'",
     "label: 'Android TV Home', description: 'Open the built-in Android TV Home screen'"),
    ("perform('Samsung Home', FrameDevice.home)", "perform('Android TV Home', FrameDevice.home)"),
    ("art: ['Art Mode', 'art']", "art: ['Screensaver', 'art']"),
    ("perform('Art Mode', FrameDevice.art)", "perform('Screensaver', FrameDevice.art)"),
]

HTML = [
    # Pin the viewport: without this, Android WebView shrinks-to-fit the 1920px stage and the
    # layout viewport grows to 1920 CSS px on a 2x-density TV, so CleanFrame never scales down.
    ('<meta name="viewport" content="width=device-width, initial-scale=1">',
     '<meta name="viewport" content="width=device-width, initial-scale=1, minimum-scale=1, maximum-scale=1, user-scalable=no">'),
]


def apply(path, pairs):
    src = path.read_text(encoding="utf-8")
    missing = [old for old, _ in pairs if old not in src]
    if missing:
        sys.exit("patch_ui: upstream " + path.name + " changed; not found:\n  " + "\n  ".join(missing))
    for old, new in pairs:
        src = src.replace(old, new)
    path.write_text(src, encoding="utf-8")


apply(home / "main.js", MAIN)
apply(home / "index.html", HTML)

# Tizen packaging files are not used on Android.
for name in ("config.xml",):
    (home / name).unlink(missing_ok=True)
print("patched", home)
