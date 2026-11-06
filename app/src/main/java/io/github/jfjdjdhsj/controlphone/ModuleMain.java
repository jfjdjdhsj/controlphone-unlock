package io.github.jfjdjdhsj.controlphone;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * LSPosed module for com.techbao.controlphone 1.6.3.
 *
 * <p>Two things happen here:</p>
 * <ol>
 *   <li>{@code q0.s.g()} is the single source of truth for the license state, so
 *   replacing its result unlocks every feature gate without touching the APK.
 *   The official signature stays intact, which the Xiaomi wearable SDK requires.</li>
 *   <li>The "我的" tab gets an extra entry at the bottom that opens the module's
 *   download page. The UI is Jetpack Compose, so nothing can be inserted into the
 *   composition; instead a plain view is attached to the window decor and shown
 *   only while {@code MineScreen} composes.</li>
 * </ol>
 */
public class ModuleMain extends XposedModule {

    private static final String TAG = "ControlPhoneUnlock";
    private static final String TARGET_PACKAGE = "com.techbao.controlphone";
    private static final String DOWNLOAD_URL =
            "https://github.com/jfjdjdhsj/controlphone-unlock/releases/latest";

    /** Height of the app's bottom navigation bar (MainActivity.kt:1096, 58.dp). */
    private static final int BOTTOM_BAR_DP = 58;
    private static final int ENTRY_MARGIN_DP = 8;
    private static final String ENTRY_TAG = "controlphone_unlock_download";

    private static volatile View downloadEntry;

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!TARGET_PACKAGE.equals(param.getPackageName())) {
            return;
        }

        final ClassLoader cl = param.getClassLoader();
        hookLicense(cl);
        hookMineScreen(cl);
    }

    private void hookLicense(ClassLoader cl) {
        try {
            final Class<?> statusClass = Class.forName("q0.t", false, cl);
            final Class<?> licenseClass = Class.forName("q0.u", false, cl);
            final Class<?> managerClass = Class.forName("q0.s", false, cl);

            final Object licensed = statusClass.getField("k").get(null);
            final Constructor<?> ctor = licenseClass.getConstructor(
                    statusClass, String.class, Long.class, Long.class);
            final Method g = managerClass.getDeclaredMethod("g");

            hook(g).intercept(chain -> ctor.newInstance(licensed, "已永久激活", null, null));
            log(Log.INFO, TAG, "hooked q0.s.g() -> LICENSED");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "license hook failed, target version may have changed", t);
        }
    }

    /**
     * MainActivity itself is not obfuscated, but its composables are: o() is
     * MineScreen, j() is HomeScreen, u() is the shortcut screen. Showing the entry
     * on Mine and hiding it on the other two keeps it out of the way.
     */
    private void hookMineScreen(ClassLoader cl) {
        try {
            Class<?> activityClass = Class.forName("com.techbao.controlphone.MainActivity", false, cl);
            Class<?> composerClass = Class.forName("androidx.compose.runtime.Composer", false, cl);

            Method onCreate = activityClass.getDeclaredMethod("onCreate", Bundle.class);
            hook(onCreate).intercept(chain -> {
                Object result = chain.proceed();
                attachEntry((Activity) chain.getThisObject());
                return result;
            });

            Method onDestroy = activityClass.getDeclaredMethod("onDestroy");
            hook(onDestroy).intercept(chain -> {
                Object result = chain.proceed();
                detachEntry((Activity) chain.getThisObject());
                return result;
            });

            Method mine = activityClass.getDeclaredMethod("o", composerClass, int.class);
            hook(mine).intercept(chain -> {
                Object result = chain.proceed();
                setEntryVisible(true);
                return result;
            });

            Method home = activityClass.getDeclaredMethod("j", composerClass, int.class);
            hook(home).intercept(chain -> {
                Object result = chain.proceed();
                setEntryVisible(false);
                return result;
            });

            Method shortcuts = activityClass.getDeclaredMethod("u", composerClass, int.class);
            hook(shortcuts).intercept(chain -> {
                Object result = chain.proceed();
                setEntryVisible(false);
                return result;
            });

            log(Log.INFO, TAG, "hooked MineScreen entry");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "MineScreen hook failed, entry not added", t);
        }
    }

    private static void attachEntry(Activity activity) {
        if (activity == null || !(activity.getWindow().getDecorView() instanceof ViewGroup)) {
            return;
        }
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        View existing = decor.findViewWithTag(ENTRY_TAG);
        if (existing != null) {
            decor.removeView(existing);
        }

        View entry = buildEntry(activity);
        entry.setTag(ENTRY_TAG);
        entry.setVisibility(View.GONE);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM;
        lp.leftMargin = dp(activity, 16);
        lp.rightMargin = dp(activity, 16);
        lp.bottomMargin = dp(activity, BOTTOM_BAR_DP + ENTRY_MARGIN_DP) + navBarInset(activity);

        decor.addView(entry, lp);
        downloadEntry = entry;
        Log.i(TAG, "download entry attached, bottomMargin=" + lp.bottomMargin);
    }

    private static void detachEntry(Activity activity) {
        downloadEntry = null;
        if (activity == null || !(activity.getWindow().getDecorView() instanceof ViewGroup)) {
            return;
        }
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        View existing = decor.findViewWithTag(ENTRY_TAG);
        if (existing != null) {
            decor.removeView(existing);
        }
    }

    private static void setEntryVisible(boolean visible) {
        View entry = downloadEntry;
        if (entry == null) {
            return;
        }
        entry.post(() -> {
            int target = visible ? View.VISIBLE : View.GONE;
            if (entry.getVisibility() != target) {
                entry.setVisibility(target);
                Log.i(TAG, "download entry " + (visible ? "shown" : "hidden"));
            }
        });
    }

    private static View buildEntry(Activity activity) {
        boolean night = (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

        int surface = night ? 0xFF1F2228 : 0xFFFFFFFF;
        int stroke = night ? 0x33FFFFFF : 0x14000000;
        int titleColor = night ? 0xFFE8EAF0 : 0xFF1B1B1F;
        int subColor = night ? 0xFF9AA1AC : 0xFF5F6368;
        int accent = night ? 0xFFA8C7FF : 0xFF2F5BD8;
        int accentBg = night ? 0x332F5BD8 : 0x1A2F5BD8;

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        card.setElevation(dp(activity, 6));
        card.setClickable(true);
        card.setFocusable(true);

        GradientDrawable background = new GradientDrawable();
        background.setColor(surface);
        background.setCornerRadius(dp(activity, 16));
        background.setStroke(dp(activity, 1), stroke);
        card.setBackground(background);

        TextView icon = new TextView(activity);
        icon.setText("↓");
        icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        icon.setTextColor(accent);
        icon.setGravity(Gravity.CENTER);
        GradientDrawable iconBackground = new GradientDrawable();
        iconBackground.setShape(GradientDrawable.OVAL);
        iconBackground.setColor(accentBg);
        icon.setBackground(iconBackground);
        card.addView(icon, new LinearLayout.LayoutParams(dp(activity, 36), dp(activity, 36)));

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textsParams.leftMargin = dp(activity, 12);

        TextView title = new TextView(activity);
        title.setText("解锁模块");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTextColor(titleColor);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        TextView subtitle = new TextView(activity);
        subtitle.setText("下载最新版本 · GitHub");
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        subtitle.setTextColor(subColor);
        subtitle.setPadding(0, dp(activity, 2), 0, 0);

        texts.addView(title);
        texts.addView(subtitle);
        card.addView(texts, textsParams);

        TextView arrow = new TextView(activity);
        arrow.setText("›");
        arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        arrow.setTextColor(subColor);
        card.addView(arrow);

        card.setOnClickListener(v -> openDownloadPage(activity));
        return card;
    }

    private static void openDownloadPage(Activity activity) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(DOWNLOAD_URL));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            Log.i(TAG, "opened " + DOWNLOAD_URL);
        } catch (Throwable t) {
            Toast.makeText(activity, "无法打开浏览器", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "failed to open download page", t);
        }
    }

    private static int navBarInset(Activity activity) {
        View decor = activity.getWindow().getDecorView();
        WindowInsets insets = decor.getRootWindowInsets();
        if (insets != null) {
            return insets.getSystemWindowInsetBottom();
        }
        int id = activity.getResources().getIdentifier(
                "navigation_bar_height", "dimen", "android");
        return id > 0 ? activity.getResources().getDimensionPixelSize(id) : 0;
    }

    private static int dp(Activity activity, float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, activity.getResources().getDisplayMetrics()));
    }
}
