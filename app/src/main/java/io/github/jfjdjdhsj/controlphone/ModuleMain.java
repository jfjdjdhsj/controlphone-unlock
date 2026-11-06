package io.github.jfjdjdhsj.controlphone;

import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * LSPosed module for com.techbao.controlphone 1.6.3.
 *
 * <p>The app computes its license state in {@code q0.s.g()}, which is the single
 * source of truth consumed by the UI and by every feature gate. Replacing its
 * result with a LICENSED status unlocks the app without touching the APK, so the
 * original signing certificate stays intact (required by the Xiaomi wearable
 * SDK, which verifies the caller's signature inside the Mi Fitness service).</p>
 */
public class ModuleMain extends XposedModule {

    private static final String TAG = "ControlPhoneUnlock";
    private static final String TARGET_PACKAGE = "com.techbao.controlphone";

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!TARGET_PACKAGE.equals(param.getPackageName())) {
            return;
        }

        final ClassLoader cl = param.getClassLoader();
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
            log(Log.ERROR, TAG, "hook failed, target version may have changed", t);
        }
    }
}
