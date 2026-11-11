package com.ansub.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.Map;

/** Filters only remote keys the user has explicitly mapped. No network or screen access. */
public final class RemoteButtonService extends AccessibilityService {
    static final String PREFS = "remote_button_mappings";
    static final String LEARN_TARGET = "learn_target";
    static final String MAPPING_PREFIX = "mapping.";
    static final String ACTION_CHANGED = "com.ansub.tvhome.MAPPINGS_CHANGED";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int learningKeyCode = Integer.MIN_VALUE;

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}

    static String mappingKey(int keyCode, int scanCode) {
        return MAPPING_PREFIX + keyCode + "." + scanCode;
    }

    static void ensureR646Defaults(SharedPreferences prefs) {
        if (prefs.getBoolean("defaults_seeded", false)) return;
        SharedPreferences.Editor edit = prefs.edit();
        putDefault(edit, prefs, 4062, "com.netflix.ninja");
        putDefault(edit, prefs, 4065, "com.google.android.youtube.tv");
        putDefault(edit, prefs, 4020, "com.disney.disneyplus");
        putDefault(edit, prefs, 4099, "com.amazon.amazonvideo.livingroom");
        edit.putBoolean("defaults_seeded", true).apply();
    }

    private static void putDefault(SharedPreferences.Editor edit, SharedPreferences prefs,
                                   int code, String packageName) {
        String prefix = MAPPING_PREFIX + code + ".";
        for (String key : prefs.getAll().keySet()) if (key.startsWith(prefix)) return;
        edit.putString(mappingKey(code, 0), packageName);
    }

    @Override public void onServiceConnected() {
        ensureR646Defaults(getSharedPreferences(PREFS, MODE_PRIVATE));
    }

    static boolean isProtectedKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                return true;
            default:
                return false;
        }
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        // Consume the matching key-up from a just-learned shortcut, then return to normal mode.
        if (learningKeyCode != Integer.MIN_VALUE) {
            if (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == learningKeyCode)
                learningKeyCode = Integer.MIN_VALUE;
            return true;
        }
        String learningPackage = prefs.getString(LEARN_TARGET, null);
        if (learningPackage != null) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0
                    && !isProtectedKey(event.getKeyCode())
                    && (event.getKeyCode() != KeyEvent.KEYCODE_UNKNOWN || event.getScanCode() != 0)) {
                int code = event.getKeyCode();
                int scan = event.getScanCode();
                SharedPreferences.Editor edit = prefs.edit();
                // A key learned again replaces its previous app assignment.
                for (String key : prefs.getAll().keySet()) {
                    if (!key.startsWith(MAPPING_PREFIX)) continue;
                    String[] parts = key.substring(MAPPING_PREFIX.length()).split("\\.");
                    if (parts.length > 0 && parts[0].equals(String.valueOf(code))) edit.remove(key);
                }
                edit.putString(mappingKey(code, scan), learningPackage).remove(LEARN_TARGET).apply();
                learningKeyCode = code;
                sendBroadcast(new Intent(ACTION_CHANGED).setPackage(getPackageName()));
            }
            // Keep ordinary navigation usable while waiting for the requested app shortcut.
            if (isProtectedKey(event.getKeyCode())) return false;
            return true;
        }

        String target = findTarget(prefs, event);
        if (target == null) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            final String packageName = target;
            // TCL firmware may dispatch its own shortcut after the accessibility key filter.
            handler.postDelayed(() -> launch(packageName), 400);
        }
        return true;
    }

    private String findTarget(SharedPreferences prefs, KeyEvent event) {
        Map<String, ?> values = prefs.getAll();
        String byCode = null;
        String codePrefix = MAPPING_PREFIX + event.getKeyCode() + ".";
        for (Map.Entry<String, ?> item : values.entrySet()) {
            if (!item.getKey().startsWith(MAPPING_PREFIX) || !(item.getValue() instanceof String)) continue;
            String key = item.getKey();
            if (key.equals(mappingKey(event.getKeyCode(), event.getScanCode()))) return (String) item.getValue();
            if (key.startsWith(codePrefix)) byCode = (String) item.getValue();
        }
        return byCode;
    }

    private void launch(String packageName) {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(packageName);
        if (intent == null) intent = pm.getLaunchIntentForPackage(packageName);
        if (intent == null) return;
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try { startActivity(intent); } catch (RuntimeException ignored) {}
    }
}
