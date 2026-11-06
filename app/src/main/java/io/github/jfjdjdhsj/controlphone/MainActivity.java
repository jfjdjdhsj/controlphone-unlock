package io.github.jfjdjdhsj.controlphone;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.DynamicColors;

import java.security.MessageDigest;
import java.util.Locale;

/**
 * Module UI, Material 3. Reports whether the target app is present, at the
 * supported version, and still signed with the original certificate. The
 * signature check matters because Mi Fitness verifies the caller's signing
 * certificate, so a repacked build can never connect to the band.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "ControlPhoneUnlock";
    private static final String TARGET_PACKAGE = "com.techbao.controlphone";
    private static final String TARGET_VERSION = "1.6.3";
    private static final String LSPOSED_PACKAGE = "org.lsposed.manager";

    /** SHA-256 of the official signing certificate (CN=TechBao, O=EasyTech). */
    private static final String OFFICIAL_CERT_SHA256 =
            "e3a8aea2e82ab5143dd6bccaae954ccf941c01375bf759e8b99db5a363da0caa";

    private LinearLayout envRows;
    private LinearLayout policyRows;
    private Chip statusChip;
    private TextView heroTitle;
    private TextView heroSupport;
    private MaterialButton openAppButton;

    private String targetVersion;
    private String targetCertHash;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivityIfAvailable(this);
        setContentView(R.layout.activity_main);

        envRows = findViewById(R.id.rows_env);
        policyRows = findViewById(R.id.rows_policy);
        statusChip = findViewById(R.id.chip_status);
        heroTitle = findViewById(R.id.tv_hero_title);
        heroSupport = findViewById(R.id.tv_hero_support);
        openAppButton = findViewById(R.id.btn_open_app);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setSubtitle(getString(R.string.app_subtitle) + " · v" + moduleVersion());

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
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
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

        Log.i(TAG, "target installed=" + installed + " version=" + targetVersion
                + " cert=" + targetCertHash + " official=" + official + " supported=" + supported);

        envRows.removeAllViews();
        addRow(envRows, getString(R.string.row_target),
                getString(installed ? R.string.value_installed : R.string.value_not_installed),
                getColor(installed ? R.color.status_ok : R.color.status_bad));
        addRow(envRows, getString(R.string.row_version),
                targetVersion == null ? "—" : targetVersion,
                TARGET_VERSION.equals(targetVersion)
                        ? getColor(R.color.md_on_surface) : getColor(R.color.status_warn));
        addRow(envRows, getString(R.string.row_signature),
                targetCertHash == null ? getString(R.string.value_unknown)
                        : getString(official ? R.string.value_official : R.string.value_unofficial),
                targetCertHash == null ? getColor(R.color.md_on_surface_variant)
                        : getColor(official ? R.color.status_ok : R.color.status_bad));
        addRow(envRows, getString(R.string.row_compat),
                getString(supported ? R.string.value_compat_ok : R.string.value_compat_mismatch),
                getColor(supported ? R.color.status_ok : R.color.status_warn));

        policyRows.removeAllViews();
        addRow(policyRows, getString(R.string.row_mode),
                getString(R.string.value_mode), getColor(R.color.md_on_surface));
        addRow(policyRows, getString(R.string.row_license),
                getString(R.string.value_license), getColor(R.color.md_primary));
        addRow(policyRows, getString(R.string.row_entry),
                getString(R.string.value_entry), getColor(R.color.md_on_surface));
        addRow(policyRows, getString(R.string.row_xiaomi),
                getString(R.string.value_xiaomi), getColor(R.color.status_ok));

        if (!installed) {
            heroTitle.setText(R.string.hero_missing_title);
            heroSupport.setText(R.string.hero_missing_support);
            setChip(R.string.status_missing, getColor(R.color.status_bad));
        } else if (supported) {
            heroTitle.setText(R.string.hero_ready_title);
            heroSupport.setText(R.string.hero_ready_support);
            setChip(R.string.status_ready, getColor(R.color.status_ok));
        } else {
            heroTitle.setText(R.string.hero_warn_title);
            heroSupport.setText(R.string.hero_warn_support);
            setChip(R.string.status_warning, getColor(R.color.status_warn));
        }

        openAppButton.setEnabled(installed);
    }

    private void setChip(int textRes, int color) {
        statusChip.setText(textRes);
        statusChip.setTextColor(color);
        statusChip.setChipBackgroundColor(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x24)));
        statusChip.setChipStrokeColor(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x66)));
        statusChip.setChipStrokeWidth(dp(1));
    }

    private void addRow(LinearLayout container, CharSequence label, CharSequence value, int valueColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        TextView name = new TextView(this);
        TextViewCompat.setTextAppearance(name, R.style.TextAppearance_ControlPhone_Label);
        name.setText(label);
        row.addView(name, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView content = new TextView(this);
        TextViewCompat.setTextAppearance(content, R.style.TextAppearance_ControlPhone_Value);
        content.setText(value);
        content.setTextColor(valueColor);
        content.setGravity(Gravity.END);
        row.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        container.addView(row);
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
