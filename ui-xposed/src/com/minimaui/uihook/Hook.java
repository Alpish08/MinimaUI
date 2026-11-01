package com.minimaui.uihook;

import android.view.View;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * The LSPosed entry point: hooks the volume dialog of SystemUI and hands the work to {@link VolumeStyle}. SystemUI colours the slider's
 * fill with the accent colour and its icons with the dark panel colour from code, which a resource overlay cannot change, hence this
 * hook. It is written against the SystemUI of this ROM (Android 13, VolumeDialogImpl) and does nothing, apart from a log line, where a
 * class or field is not there; no exception ever reaches SystemUI.
 */
public class Hook implements IXposedHookLoadPackage {
    private static final String TAG = "MinimaUIHook: ";
    private static final String SYSTEMUI = "com.android.systemui";
    private static final String IMPL = "com.android.systemui.volume.VolumeDialogImpl";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!SYSTEMUI.equals(lpparam.packageName)) {
            return;
        }
        ClassLoader loader = lpparam.classLoader;
        try {
            Class<?> impl = loader.loadClass(IMPL);
            hook(impl, "initDialog", new After() {
                @Override
                void run(Object thisObject, Object[] args) throws Throwable {
                    VolumeStyle.styleDialog(thisObject);
                }
            });
            hook(impl, "initRow", new After() {
                @Override
                void run(Object thisObject, Object[] args) throws Throwable {
                    VolumeStyle.styleSlider(thisObject, args[0]);
                }
            });
            hook(impl, "updateRowsH", new After() {
                @Override
                void run(Object thisObject, Object[] args) throws Throwable {
                    VolumeStyle.styleRows(thisObject);
                }
            });
            hook(impl, "updateVolumeRowTintH", new After() {
                @Override
                void run(Object thisObject, Object[] args) throws Throwable {
                    VolumeStyle.tintRow(args[0]);
                }
            });
            // SystemUI calls this on every frame of the ringer drawer's animation; the ringer piece's background follows it.
            hook(impl, "updateBackgroundForDrawerClosedAmount", new After() {
                @Override
                void run(Object thisObject, Object[] args) throws Throwable {
                    Object container = VolumeStyle.get(thisObject, "mRingerAndDrawerContainer");
                    if (container instanceof View) {
                        ((View) container).invalidate();
                    }
                }
            });
            XposedBridge.log(TAG + "volume dialog hooked");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "volume dialog not hooked: " + t);
            return;
        }
        // The ringer drawer's icons are coloured with (selected: the dark panel colour, deselected: the accent) while it opens and closes.
        // Both are made light, so a selected icon stays readable on the dark selection circle.
        try {
            Class<?> animator = loader.loadClass(IMPL + "$$ExternalSyntheticLambda7");
            XposedBridge.hookAllConstructors(animator, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args != null && param.args.length == 3 && param.args[1] instanceof Integer) {
                        param.args[1] = VolumeStyle.LIGHT;
                        param.args[2] = VolumeStyle.LIGHT;
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "ringer icon colours not hooked: " + t);
        }
    }

    private abstract static class After extends XC_MethodHook {
        abstract void run(Object thisObject, Object[] args) throws Throwable;

        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            try {
                run(param.thisObject, param.args);
            } catch (Throwable t) {
                XposedBridge.log(TAG + param.method + ": " + t);
            }
        }
    }

    private static void hook(Class<?> owner, String method, XC_MethodHook callback) {
        try {
            XposedBridge.hookAllMethods(owner, method, callback);
        } catch (Throwable t) {
            XposedBridge.log(TAG + method + " not hooked: " + t);
        }
    }
}
