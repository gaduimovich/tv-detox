package com.ansub.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** On-TV configuration for learning physical remote keys and choosing their target apps. */
public final class RemoteButtonSettingsActivity extends Activity {
    private SharedPreferences prefs;
    private LinearLayout content;
    private TextView serviceStatus;
    private TextView status;
    private Button enableService;
    private final BroadcastReceiver changed = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { refresh(); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(RemoteButtonService.PREFS, MODE_PRIVATE);
        RemoteButtonService.ensureR646Defaults(prefs);
        buildScreen();
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        registerReceiver(changed, new IntentFilter(RemoteButtonService.ACTION_CHANGED));
        refresh();
    }

    @Override protected void onPause() {
        try { unregisterReceiver(changed); } catch (IllegalArgumentException ignored) {}
        super.onPause();
    }

    private void buildScreen() {
        int pad = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(0xff101318);
        TextView title = new TextView(this);
        title.setText("Remote Buttons");
        title.setTextSize(28);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(0xffffffff);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView explanation = new TextView(this);
        explanation.setText("Choose which app each remote shortcut opens. Select Learn key, then press the physical button. Navigation, Home, Back, power, and volume keys are protected.");
        explanation.setTextSize(16);
        explanation.setTextColor(0xffd5d9df);
        explanation.setPadding(0, dp(10), 0, dp(12));
        root.addView(explanation, new LinearLayout.LayoutParams(-1, -2));
        serviceStatus = new TextView(this);
        serviceStatus.setTextSize(16);
        serviceStatus.setTextColor(0xffffd166);
        serviceStatus.setPadding(0, 0, 0, dp(8));
        root.addView(serviceStatus, new LinearLayout.LayoutParams(-1, -2));
        enableService = new Button(this);
        enableService.setText("Open Accessibility settings");
        styleButton(enableService);
        enableService.setOnClickListener(v -> openAccessibilitySettings());
        root.addView(enableService, new LinearLayout.LayoutParams(-2, -2));
        status = new TextView(this);
        status.setTextSize(16);
        status.setTextColor(0xffffd166);
        status.setPadding(0, 0, 0, dp(12));
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));
        Button add = new Button(this);
        add.setText("Add remote button");
        styleButton(add);
        add.setOnClickListener(v -> chooseApp(null, true));
        root.addView(add, new LinearLayout.LayoutParams(-2, -2));
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    private void refresh() {
        if (content == null) return;
        content.removeAllViews();
        boolean serviceEnabled = isServiceEnabled();
        serviceStatus.setText(serviceEnabled
                ? "Remote key service is on. You can learn and use button mappings."
                : "Turn on the remote key service in Accessibility before learning buttons.");
        enableService.setVisibility(serviceEnabled ? View.GONE : View.VISIBLE);
        if (prefs.getString(RemoteButtonService.LEARN_TARGET, null) != null) {
            status.setText("Waiting for a remote button… Press the physical shortcut now. Use Back to cancel.");
        } else if (RemoteButtonService.supportsR646Defaults()) {
            status.setText("Mappings are stored on this TV. R646 defaults are preloaded; learn keys to adapt them to another remote.");
        } else {
            status.setText("Mappings are stored on this TV. Add a remote button or learn keys for this TV’s remote.");
        }
        List<Mapping> mappings = mappings();
        Collections.sort(mappings, Comparator.comparingInt(m -> m.keyCode));
        if (mappings.isEmpty()) {
            TextView none = label("No buttons mapped yet.", 17);
            content.addView(none);
        }
        for (Mapping mapping : mappings) addMappingRow(mapping);
        if (prefs.getString(RemoteButtonService.LEARN_TARGET, null) != null) {
            Button cancel = new Button(this);
            cancel.setText("Cancel learning");
            styleButton(cancel);
            cancel.setOnClickListener(v -> {
                prefs.edit().remove(RemoteButtonService.LEARN_TARGET).apply();
                refresh();
            });
            content.addView(cancel, new LinearLayout.LayoutParams(-2, -2));
        }
    }

    private void addMappingRow(Mapping mapping) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        TextView name = label(keyLabel(mapping.keyCode, mapping.scanCode) + "  →  " + appLabel(mapping.packageName), 18);
        name.setTypeface(null, Typeface.BOLD);
        row.addView(name, new LinearLayout.LayoutParams(-1, -2));
        String detailText = "Android key " + mapping.keyCode;
        if (mapping.scanCode != 0) detailText += " · remote scan " + mapping.scanCode;
        TextView detail = label(detailText, 13);
        detail.setTextColor(0xffaeb7c2);
        row.addView(detail, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.START);
        Button app = new Button(this);
        app.setText("Change app");
        styleButton(app);
        app.setOnClickListener(v -> chooseApp(mapping, false));
        actions.addView(app);
        Button key = new Button(this);
        key.setText("Learn key");
        styleButton(key);
        key.setOnClickListener(v -> beginLearning(mapping.packageName));
        actions.addView(key);
        Button remove = new Button(this);
        remove.setText("Remove");
        styleButton(remove);
        remove.setOnClickListener(v -> showStyled(new AlertDialog.Builder(this)
                .setTitle("Remove this button?")
                .setMessage("The remote key will return to its normal TV behavior.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (d, w) -> {
                    prefs.edit().remove(mapping.storageKey).apply(); refresh();
                })));
        actions.addView(remove);
        row.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        View divider = new View(this);
        divider.setBackgroundColor(0xff303740);
        LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(-1, dp(1));
        line.topMargin = dp(4);
        row.addView(divider, line);
        content.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void chooseApp(Mapping mapping, boolean add) {
        List<AppChoice> apps = launchableApps();
        if (apps.isEmpty()) {
            showStyled(new AlertDialog.Builder(this).setMessage("No launchable apps were found.")
                    .setPositiveButton("OK", null));
            return;
        }
        String[] labels = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) labels[i] = apps.get(i).label;
        showStyled(new AlertDialog.Builder(this).setTitle("Choose an app")
                .setItems(labels, (dialog, which) -> {
                    String pkg = apps.get(which).packageName;
                    if (add) beginLearning(pkg);
                    else {
                        prefs.edit().putString(mapping.storageKey, pkg).apply();
                        refresh();
                    }
                }).setNegativeButton("Cancel", null));
    }

    private void beginLearning(String packageName) {
        if (!isServiceEnabled()) {
            showStyled(new AlertDialog.Builder(this)
                    .setTitle("Turn on the remote key service")
                    .setMessage("Enable TV Detox in Android Accessibility settings, then return here to learn a remote button.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Open settings", (dialog, which) -> openAccessibilitySettings()));
            return;
        }
        prefs.edit().putString(RemoteButtonService.LEARN_TARGET, packageName).apply();
        refresh();
        if (status != null) status.requestFocus();
    }

    private List<AppChoice> launchableApps() {
        TreeMap<String, AppChoice> choices = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        PackageManager pm = getPackageManager();
        for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || choicesContainsPackage(choices, pkg)) continue;
                CharSequence label;
                try { label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)); }
                catch (Exception e) { label = ri.loadLabel(pm); }
                choices.put(label.toString() + "\u0000" + pkg, new AppChoice(label.toString(), pkg));
            }
        }
        return new ArrayList<>(choices.values());
    }

    private boolean choicesContainsPackage(TreeMap<String, AppChoice> choices, String pkg) {
        for (AppChoice value : choices.values()) if (value.packageName.equals(pkg)) return true;
        return false;
    }

    private List<Mapping> mappings() {
        List<Mapping> out = new ArrayList<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (!entry.getKey().startsWith(RemoteButtonService.MAPPING_PREFIX) || !(entry.getValue() instanceof String)) continue;
            String[] parts = entry.getKey().substring(RemoteButtonService.MAPPING_PREFIX.length()).split("\\.");
            if (parts.length != 2) continue;
            try { out.add(new Mapping(entry.getKey(), Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), (String) entry.getValue())); }
            catch (NumberFormatException ignored) {}
        }
        return out;
    }

    private String keyLabel(int code, int scan) {
        switch (code) {
            case 4062: return "Netflix button";
            case 4065: return "YouTube button";
            case 4020: return "TCL TV button";
            case 4099: return "Prime Video button";
            default: break;
        }
        String name = KeyEventName.name(code);
        if (!name.equals("Unknown key")) return name;
        return "Remote key";
    }

    private String appLabel(String pkg) {
        try { return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
        catch (Exception e) { return pkg; }
    }

    private TextView label(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(0xffffffff);
        view.setPadding(0, dp(4), 0, dp(4));
        return view;
    }

    private void styleButton(Button button) {
        int[][] states = new int[][] {
                new int[] {android.R.attr.state_focused},
                new int[] {android.R.attr.state_pressed},
                new int[] {}
        };
        button.setBackgroundTintList(new ColorStateList(states,
                new int[] {0xff00d7ff, 0xffffc857, 0xff303944}));
        button.setTextColor(new ColorStateList(states,
                new int[] {0xff101318, 0xff101318, 0xffffffff}));
        button.setMinHeight(dp(54));
    }

    private AlertDialog showStyled(AlertDialog.Builder builder) {
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (positive != null) styleButton(positive);
            if (negative != null) styleButton(negative);
            if (neutral != null) styleButton(neutral);
        });
        dialog.show();
        return dialog;
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private boolean isServiceEnabled() {
        if (Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) return false;
        String services = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (services == null) return false;
        ComponentName component = new ComponentName(this, RemoteButtonService.class);
        for (String enabled : services.split(":")) {
            ComponentName parsed = ComponentName.unflattenFromString(enabled.trim());
            if (component.equals(parsed)) return true;
        }
        return false;
    }

    private void openAccessibilitySettings() {
        String[] actions = {
                "android.settings.ACCESSIBILITY_TV_OEM_LINK",
                Settings.ACTION_ACCESSIBILITY_SETTINGS
        };
        for (String action : actions) {
            Intent intent = new Intent(action);
            if (intent.resolveActivity(getPackageManager()) == null) continue;
            try {
                startActivity(intent);
                return;
            } catch (RuntimeException ignored) {
                // Try the generic Android settings action if the TV OEM link is unavailable.
            }
        }
        status.setText("Could not open Accessibility settings. Open TV Settings > Accessibility manually.");
    }

    private static final class Mapping {
        final String storageKey, packageName;
        final int keyCode, scanCode;
        Mapping(String storageKey, int keyCode, int scanCode, String packageName) {
            this.storageKey = storageKey; this.keyCode = keyCode; this.scanCode = scanCode; this.packageName = packageName;
        }
    }
    private static final class AppChoice {
        final String label, packageName;
        AppChoice(String label, String packageName) { this.label = label; this.packageName = packageName; }
    }
    private static final class KeyEventName {
        static String name(int code) {
            String s = android.view.KeyEvent.keyCodeToString(code);
            if (s == null || s.endsWith("UNKNOWN")) return "Unknown key";
            if (!s.startsWith("KEYCODE_")) return s;
            String name = s.substring("KEYCODE_".length());
            if (name.matches("[0-9]+")) return "Unknown key";
            return title(name);
        }
        static String title(String s) {
            StringBuilder out = new StringBuilder();
            for (String part : s.toLowerCase().split("_")) {
                if (out.length() > 0) out.append(' ');
                if (!part.isEmpty()) out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
            return out.toString();
        }
    }
}
