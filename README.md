# FrameLab Camera

A beginner-friendly native Android Camera2 prototype with:
- Auto and Manual modes
- Manual ISO and shutter-speed controls
- Rear-camera selection
- Aperture choices exposed by the selected camera
- JPEG capture saved to `Pictures/FrameLab` in the Android Gallery

## Build in Android Studio

1. Install Android Studio.
2. Open this project folder.
3. Let Gradle sync.
4. Connect your Galaxy S26 Ultra with USB debugging enabled, or use Android Studio's device pairing.
5. Press Run.

## Important camera limitations

Phone camera hardware differs by lens. ISO and shutter ranges are read from Camera2 camera characteristics. Aperture control is only enabled when the selected camera reports more than one supported aperture. Many phone lenses have a fixed physical aperture, so the app cannot force an unsupported f-number.

This is a starter project and has not been compiled or tested on a physical Galaxy S26 Ultra in this environment. Some camera combinations on Samsung firmware might require device-specific adjustments. If Gradle reports a build error, share the exact message to fix it.
## Build an APK using a phone only

This project includes a GitHub Actions workflow at `.github/workflows/android.yml`.
GitHub Actions builds a debug APK in the cloud and stores it as a downloadable artifact.

1. On your phone, sign in at github.com and create a new repository.
2. Upload the project files and folders from this ZIP into the repository, keeping their folder paths. Include the hidden `.github` folder.
3. Open the repository's Actions tab and run `Build FrameLab Camera APK` if it has not started automatically.
4. When the workflow finishes successfully, open the run and download the `FrameLabCamera-debug-apk` artifact.
5. Extract the downloaded artifact ZIP to get `app-debug.apk`.
6. Open the APK on your phone and follow Android's install prompts.

The APK is unsigned for public distribution but is suitable for personal testing. This source has not been compiled or tested on a physical phone yet. Camera controls vary by device and lens.
