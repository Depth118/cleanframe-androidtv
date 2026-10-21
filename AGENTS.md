# Agent guide: CleanFrame for Android TV

You're helping someone put CleanFrame, a calm home screen, on their own Android TV. They may not be technical. Explain what you're doing in plain words, and confirm with them before anything that changes the TV.

## Project map

- `UPSTREAM_REF`: the upstream CleanFrame commit the UI is built from.
- `scripts/sync-ui.sh`: fetches upstream `app/` into `android/app/src/main/assets/home/` (git-ignored) and runs `scripts/patch_ui.py`. Run it before every build on a fresh clone.
- `scripts/patch_ui.py`: exact-string patches to upstream `main.js` and `index.html`. If upstream changes and a patch no longer matches, the script stops. Update the patch; don't loosen it.
- `web/device.js`: the Android `FrameDevice` adapter. Device logic belongs here, not in upstream files.
- `android/`: a plain Java Gradle project with no dependencies. `MainActivity.java` hosts the WebView and the `window.AndroidTV` bridge.
- `scripts/install.sh <ip>` and `scripts/set-home.sh <ip> [--disable-others|--restore]`: device scripts.

UI code runs in the TV's WebView. Keep it ES5-compatible, the same as upstream.

## Setting up a TV, in order

1. **Tools.** Check for JDK 17, the Android SDK (platform 35, build-tools 34) and `adb`, and install whatever is missing. Write `android/local.properties` with `sdk.dir=...` if `ANDROID_HOME` isn't set.
2. **Build.** Run `scripts/sync-ui.sh`, then `cd android && ./gradlew assembleRelease`.
3. **Find the TV.** Check `adb devices`, then `adb mdns services`. If nothing shows up, ask for the TV's IP address and walk them through enabling Developer options and Network/Wireless debugging (see README). Confirm it's the right device with `getprop ro.product.model` before going further.
4. **Install.** Tell them first, then run `scripts/install.sh <ip>`. They may need to accept a prompt on the TV.
5. **Verify.** Take a screenshot (`adb shell screencap -p /sdcard/s.png && adb pull ...`) and look at it. The whole stage should be visible: clock, Home controls and the dock. If you only see the top-left corner, the viewport scaling is wrong. Test the remote with `adb shell input keyevent KEYCODE_DPAD_*`, `KEYCODE_DPAD_CENTER` and `KEYCODE_BACK`.
6. **Default Home.** Only do this if they ask. Run `scripts/set-home.sh <ip>`, and add `--disable-others` only after you've told them which launchers it will disable and how to undo it. Then check that Home from another app returns to CleanFrame.

For debugging, a debug build (`assembleDebug`) turns on WebView remote debugging. Forward `localabstract:webview_devtools_remote_<pid>` and use the DevTools protocol to inspect `window.ShoreQA.where()`.

## Rules for changing the TV

- Ask before installing, rebooting, setting the default Home, or disabling anything.
- Disable with `pm disable-user --user 0`. Never `pm uninstall`, and never touch `/system`.
- Before changing anything on the TV, save its state (package lists, current Home) in `.local/` (git-ignored) and give them the exact undo commands.
- Don't disable Google Play services, the Play Store, Settings, System UI, input methods, the TV input framework, Bluetooth or remote services, setup wizards, or anything you can't identify.
- Leave display, animation and accessibility settings alone unless they ask.
- Network debugging is the way back in if something breaks. Don't turn it off without telling them.
- Keep personal data (IP addresses, device lists, keystores) in `.local/`. Never commit it.

## Checking changes

- If you change upstream-facing patches, run `python3 -I scripts/patch_ui.py` on a clean upstream `app/` copy.
- Check UI changes in a desktop browser by serving the assets folder with a mock `window.AndroidTV`. The arrow keys, Enter and Escape act as the remote.
- `./gradlew assembleRelease` must pass.
