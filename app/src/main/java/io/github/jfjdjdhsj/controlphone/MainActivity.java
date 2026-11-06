package io.github.jfjdjdhsj.controlphone;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.security.MessageDigest;
import java.util.Locale;

/**
 * Module UI: reports whether the target app is present, at the supported version,
 * and still signed with the original certificate. The signature check matters
 * because Mi Fitness verifies the caller's signing certificate, so a repacked
 * build of the target app can never connect to the band regardless of this hook.
 */
public class MainActivity extends Activity {

    private static final String TARGET_PACKAGE = "com.techbao.controlphone";
    private static final String TARGET_VERSION = "1.6.3";
    private static final String LSPOSED_PACKAGE = "org.lsposed.manager";

    /** SHA-256 of the official signing certificate (CN=TechBao, O=EasyTech). */
    private static final String OFFICIAL_CERT_SHA256 =
            "e3a8aea2e82ab5143dd6bccaae954ccf941c01375bf759e8b99db5a363da0caa";

    private LinearLayout envCard;
    private LinearLayout policyCard;
    private TextView chip;
    private Button openAppButton;

    private String targetVersion;
    private String targetCertHash;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        envCard = findViewById(R.id.card_env);
        policyCard = findViewById(R.id.card_policy);
        chip = findViewById(R.id.tv_chip);
        openAppButton = findViewById(R.id.btn_open_app);

        applyWindowInsets();

        openAppButton.setOnClickListener(v -> openTarget());
        findViewById(R.id.btn_open_lsposed).setOnClickListener(v -> openLsposed());
        findViewById(R.id.btn_copy).setOnClickListener(v -> copyDiagnostics());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void applyWindowInsets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            InsetsHelper.apply(this);
        }
    }

    /** Kept separate so the API 30 types are only resolved when actually used. */
    private static final class InsetsHelper {
        static void apply(Activity activity) {
            activity.getWindow().setDecorFitsSystemWindows(false);
            ScrollView root = activity.findViewById(R.id.root);
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }
    }

    private void refresh() {
        PackageInfo info = null;
        try {
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? PackageManager.GET_SIGNING_CERTIFICATES
                    : PackageManager.GET_SIGNATURES;
            info = getPackageManager().getPackageInfo(TARGET_PACKAGE, flags);
        } catch (PackageManager.NameNotFoundException ignored) {
            // Target not installed: rows fall back to the missing state below.
        }

        targetVersion = info == null ? null : info.versionName;
        targetCertHash = info == null ? null : signingCertSha256(info);

        boolean installed = info != null;
        boolean official = OFFICIAL_CERT_SHA256.equalsIgnoreCase(targetCertHash);
        boolean supported = official && TARGET_VERSION.equals(targetVersion);

        envCard.removeAllViews();
        addRow(envCard, getString(R.string.row_target),
                getString(installed ? R.string.value_installed : R.string.value_not_installed),
                getColor(installed ? R.color.ok : R.color.bad));
        addRow(envCard, getString(R.string.row_version),
                targetVersion == null ? "—" : targetVersion,
                TARGET_VERSION.equals(targetVersion) ? getColor(R.color.text_primary) : getColor(R.color.warn));
        addRow(envCard, getString(R.string.row_signature),
                targetCertHash == null ? getString(R.string.value_unknown)
                        : getString(official ? R.string.value_official : R.string.value_unofficial),
                targetCertHash == null ? getColor(R.color.text_secondary)
                        : getColor(official ? R.color.ok : R.color.bad));
        addRow(envCard, getString(R.string.row_compat),
                getString(supported ? R.string.value_compat_ok : R.string.value_compat_mismatch),
                getColor(supported ? R.color.ok : R.color.warn));

        policyCard.removeAllViews();
        addRow(policyCard, getString(R.string.row_mode),
                getString(R.string.value_mode), getColor(R.color.text_primary));
        addRow(policyCard, getString(R.string.row_license),
                getString(R.string.value_license), getColor(R.color.accent));
        addRow(policyCard, getString(R.string.row_entry),
                getString(R.string.value_entry), getColor(R.color.text_primary));
        addRow(policyCard, getString(R.string.row_xiaomi),
                getString(R.string.value_xiaomi), getColor(R.color.ok));

        if (!installed) {
            setChip(R.string.chip_missing, getColor(R.color.bad));
        } else if (supported) {
            setChip(R.string.chip_ready, getColor(R.color.ok));
        } else {
            setChip(R.string.chip_warning, getColor(R.color.warn));
        }

        openAppButton.setEnabled(installed);
        openAppButton.setAlpha(installed ? 1f : 0.45f);
    }

    private void setChip(int textRes, int color) {
        chip.setText(textRes);
        chip.setTextColor(color);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(99));
        bg.setColor((color & 0x00FFFFFF) | 0x1A000000);
        bg.setStroke(dp(1), (color & 0x00FFFFFF) | 0x4D000000);
        chip.setBackground(bg);
    }

    private void addRow(LinearLayout card, CharSequence label, CharSequence value, int valueColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        name.setTextColor(getColor(R.color.text_secondary));
        row.addView(name, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView content = new TextView(this);
        content.setText(value);
        content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        content.setTextColor(valueColor);
        content.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        content.setGravity(Gravity.END);
        row.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        card.addView(row);
    }

    private void openTarget() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(TARGET_PACKAGE);
        if (intent == null) {
            toast(R.string.toast_no_target);
            return;
        }
        startActivity(intent);
    }

    private void openLsposed() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(LSPOSED_PACKAGE);
        if (intent == null) {
            toast(R.string.toast_no_lsposed);
            return;
        }
        startActivity(intent);
    }

    private void copyDiagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("module: ").append(moduleVersion()).append('\n');
        sb.append("target: ").append(TARGET_PACKAGE).append('\n');
        sb.append("targetVersion: ").append(targetVersion == null ? "not installed" : targetVersion).append('\n');
        sb.append("targetCertSha256: ").append(targetCertHash == null ? "unknown" : targetCertHash).append('\n');
        sb.append("officialCert: ").append(OFFICIAL_CERT_SHA256.equalsIgnoreCase(targetCertHash)).append('\n');
        sb.append("android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("abi: ").append(Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "unknown").append('\n');
        sb.append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL);

        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("ControlPhoneUnlock", sb.toString()));
            toast(R.string.toast_copied);
        }
    }

    private String moduleVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "unknown";
        }
    }

    private static String signingCertSha256(PackageInfo info) {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            SigningInfo signingInfo = info.signingInfo;
            if (signingInfo == null) {
                return null;
            }
            signatures = signingInfo.hasMultipleSigners()
                    ? signingInfo.getApkContentsSigners()
                    : signingInfo.getSigningCertificateHistory();
        } else {
            signatures = info.signatures;
        }
        if (signatures == null || signatures.length == 0) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray());
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format(Locale.US, "%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void toast(int res) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
