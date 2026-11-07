package chat.buku.skate3;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.WindowInsets;
import org.libsdl.app.SDLActivity;
import org.libsdl.app.SDLControllerManager;

import java.io.File;

public class Skate3Activity extends SDLActivity {
    private static final String INPUT_TAG = "Skate3Input";
    private volatile TouchControllerView touchController;
    private AlertDialog exitMenu;

    @Override
    protected String[] getLibraries() {
        return new String[] { "c++_shared", "rexruntime", "skate3" };
    }

    // Called by the native settings overlay.
    public boolean showsHiddenTouchButton() {
        TouchControllerView controller = touchController;
        return controller != null && controller.showsHiddenToggle();
    }

    public void setShowHiddenTouchButton(boolean show) {
        runOnUiThread(() -> {
            if (!isFinishing() && touchController != null) {
                touchController.setShowHiddenToggle(show);
            }
        });
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (mBrokenLibraries) return;
        File gameDirectory = GameStorage.launchDirectory(this);
        if (gameDirectory == null) {
            finish();
            return;
        }
        nativeSetenv("SKATE3_GAME_DATA_DIR", gameDirectory.getAbsolutePath());
        String files = getFilesDir().getAbsolutePath();
        nativeSetenv("XDG_DATA_HOME", files);
        nativeSetenv("HOME", files);
        nativeSetenv("SKATE3_INTERNAL_FILES_DIR", files);
        nativeSetenv("SKATE3_NATIVE_LIBRARY_DIR", getApplicationInfo().nativeLibraryDir);
        CustomGpuDriver.Driver driver = CustomGpuDriver.installed(this);
        if (driver != null && driver.enabled) {
            nativeSetenv("SKATE3_VULKAN_DRIVER_DIR",
                         driver.directory.toAbsolutePath().toString() + File.separator);
            nativeSetenv("SKATE3_VULKAN_DRIVER_NAME", driver.libraryName);
            Log.i("Skate3GpuDriver", "Selected custom driver: " + driver.label());
        } else {
            nativeSetenv("SKATE3_VULKAN_DRIVER_DIR", "");
            nativeSetenv("SKATE3_VULKAN_DRIVER_NAME", "");
            Log.i("Skate3GpuDriver", "Selected system driver");
        }
        touchController = new TouchControllerView(this, this::onBackPressed);
        mLayout.addView(touchController, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    protected void onPause() {
        if (touchController != null) touchController.clearInput();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) getWindow().getInsetsController().hide(WindowInsets.Type.statusBars());
    }

    @Override
    protected void onDestroy() {
        if (exitMenu != null) exitMenu.dismiss();
        if (touchController != null) touchController.disconnect();
        super.onDestroy();
        // SDL expects a fresh process when the game is launched again.
        if (isFinishing()) System.exit(0);
    }

    @Override
    public void onBackPressed() {
        if (isFinishing() || mBrokenLibraries || mSDLThread == null) return;
        if (touchController != null) touchController.clearInput();
        // The game's Escape action opens settings or backs out one level.
        onNativeKeyDown(KeyEvent.KEYCODE_ESCAPE);
        onNativeKeyUp(KeyEvent.KEYCODE_ESCAPE);
    }

    public void confirmQuitApp() {
        runOnUiThread(this::showQuitConfirmation);
    }

    private void showQuitConfirmation() {
        if (isFinishing() || (exitMenu != null && exitMenu.isShowing())) return;
        if (touchController != null) touchController.clearInput();
        exitMenu = new AlertDialog.Builder(this)
            .setTitle(LauncherStrings.text(this, "Quit app"))
            .setMessage(LauncherStrings.text(this, "Save any progress you want to keep before leaving."))
            .setPositiveButton(LauncherStrings.text(this, "Quit app"),
                               (dialog, which) -> finishAffinity())
            .setNegativeButton(LauncherStrings.text(this, "Keep playing"), null)
            .show();
    }

    private static int getAllSources(int deviceId, int eventSource) {
        InputDevice device = InputDevice.getDevice(deviceId);
        return device == null ? eventSource : eventSource | device.getSources();
    }

    private static boolean isControllerEvent(int deviceId, int eventSource) {
        int sources = getAllSources(deviceId, eventSource);
        return (sources & (InputDevice.SOURCE_GAMEPAD |
                           InputDevice.SOURCE_JOYSTICK |
                           InputDevice.SOURCE_DPAD)) != 0;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) onBackPressed();
            return true;
        }
        int deviceId = event.getDeviceId();
        if (isControllerEvent(deviceId, event.getSource())) {
            boolean handled = false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                handled = SDLControllerManager.onNativePadDown(deviceId, event.getKeyCode());
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                handled = SDLControllerManager.onNativePadUp(deviceId, event.getKeyCode());
            }
            Log.i(INPUT_TAG, "key action=" + event.getAction() +
                    " code=" + event.getKeyCode() + " device=" + deviceId +
                    " eventSources=0x" + Integer.toHexString(event.getSource()) +
                    " allSources=0x" + Integer.toHexString(getAllSources(deviceId, event.getSource())) +
                    " handled=" + handled);
            if (handled) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (isControllerEvent(event.getDeviceId(), event.getSource()) &&
                SDLControllerManager.handleJoystickMotionEvent(event)) {
            return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }

}
