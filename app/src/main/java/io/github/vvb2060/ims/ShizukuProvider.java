package io.github.vvb2060.ims;

import static io.github.vvb2060.ims.PrivilegedProcess.TAG;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.system.Os;
import android.util.Log;

import org.lsposed.hiddenapibypass.LSPass;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;

public class ShizukuProvider extends rikka.shizuku.ShizukuProvider {
    static {
        LSPass.setHiddenApiExemptions("");
    }

    private boolean skip = false;

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        var sdkUid = getSdkSandboxUid(Os.getuid());
        var callingUid = Binder.getCallingUid();
        if (callingUid != sdkUid && callingUid != Process.SHELL_UID) {
            return new Bundle();
        }

        if (METHOD_SEND_BINDER.equals(method)) {
            Shizuku.addBinderReceivedListener(() -> {
                if (!skip && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    startInstrument(getContext());
                }
            });
        } else if (METHOD_GET_BINDER.equals(method) && callingUid == sdkUid && extras != null) {
            skip = true;
            Shizuku.addBinderReceivedListener(() -> {
                var binder = extras.getBinder("binder");
                if (binder != null && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    startShellPermissionDelegate(binder, sdkUid);
                }
            });
        }
        return super.call(method, arg, extras);
    }

    private static void startShellPermissionDelegate(IBinder binder, int sdkUid) {
        try {
            var activity = getService(Context.ACTIVITY_SERVICE);
            var am = getActivityManager(new ShizukuBinderWrapper(activity));
            var startMethod = findMethod(am.getClass(), "startDelegateShellPermissionIdentity", 2);
            if (startMethod == null) {
                throw new NoSuchMethodException("startDelegateShellPermissionIdentity");
            }
            startMethod.invoke(am, sdkUid, null);
            var data = Parcel.obtain();
            binder.transact(1, data, null, 0);
            data.recycle();
            var stopMethod = findMethod(am.getClass(), "stopDelegateShellPermissionIdentity", 0);
            if (stopMethod == null) {
                throw new NoSuchMethodException("stopDelegateShellPermissionIdentity");
            }
            stopMethod.invoke(am);
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    private static void startInstrument(Context context) {
        try {
            var binder = getService(Context.ACTIVITY_SERVICE);
            var am = getActivityManager(new ShizukuBinderWrapper(binder));
            var name = new ComponentName(context, PrivilegedProcess.class);
            var flags = getActivityManagerFlag("INSTR_FLAG_DISABLE_HIDDEN_API_CHECKS")
                    | getActivityManagerFlag("INSTR_FLAG_INSTRUMENT_SDK_SANDBOX");
            var connection = newUiAutomationConnection();
            startInstrumentation(am, name, flags, connection);
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    private static int getSdkSandboxUid(int uid) {
        try {
            var method = Process.class.getDeclaredMethod("toSdkSandboxUid", int.class);
            method.setAccessible(true);
            return (int) method.invoke(null, uid);
        } catch (Exception e) {
            try {
                var field = Process.class.getDeclaredField("FIRST_SDK_SANDBOX_UID");
                field.setAccessible(true);
                var firstSdk = field.getInt(null);
                return uid + (firstSdk - Process.FIRST_APPLICATION_UID);
            } catch (Exception ignored) {
                return uid;
            }
        }
    }

    private static IBinder getService(String name) throws Exception {
        var clazz = Class.forName("android.os.ServiceManager");
        var method = clazz.getMethod("getService", String.class);
        return (IBinder) method.invoke(null, name);
    }

    private static Object getActivityManager(IBinder binder) throws Exception {
        var clazz = Class.forName("android.app.IActivityManager$Stub");
        var method = clazz.getMethod("asInterface", IBinder.class);
        return method.invoke(null, binder);
    }

    private static int getActivityManagerFlag(String name) {
        try {
            var field = ActivityManager.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(null);
        } catch (Exception e) {
            return 0;
        }
    }

    private static Object newUiAutomationConnection() {
        try {
            var clazz = Class.forName("android.app.UiAutomationConnection");
            return clazz.getConstructor().newInstance();
        } catch (Exception e) {
            return null;
        }
    }

    private static void startInstrumentation(Object am, ComponentName name,
                                             int flags, Object connection) throws Exception {
        var method = findMethod(am.getClass(), "startInstrumentation", 8);
        if (method != null) {
            method.invoke(am, name, null, flags, new Bundle(), null, connection, 0, null);
            return;
        }
        method = findMethod(am.getClass(), "startInstrumentation", 7);
        if (method != null) {
            method.invoke(am, name, null, flags, new Bundle(), null, connection, 0);
            return;
        }
        throw new NoSuchMethodException("startInstrumentation");
    }

    private static java.lang.reflect.Method findMethod(Class<?> clazz, String name, int count) {
        for (var method : clazz.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == count) {
                return method;
            }
        }
        return null;
    }
}
