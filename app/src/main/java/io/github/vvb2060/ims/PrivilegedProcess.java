package io.github.vvb2060.ims;

import static rikka.shizuku.ShizukuProvider.METHOD_GET_BINDER;

import android.annotation.SuppressLint;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.util.Log;

import org.lsposed.hiddenapibypass.LSPass;

public class PrivilegedProcess extends Instrumentation {
    static final String TAG = "vvb";

    static {
        LSPass.setHiddenApiExemptions("");
    }

    private static final String KEY_SHOW_WIFI_CALLING_ICON_IN_STATUS_BAR_BOOL =
            getCarrierConfigKey("KEY_SHOW_WIFI_CALLING_ICON_IN_STATUS_BAR_BOOL",
                    "show_wifi_calling_icon_in_status_bar_bool");
    private static final String KEY_WFC_SPN_FORMAT_IDX_INT =
            getCarrierConfigKey("KEY_WFC_SPN_FORMAT_IDX_INT", "wfc_spn_format_idx_int");

    @Override
    public void onCreate(Bundle arguments) {
        var binder = new Binder() {
            @Override
            protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                if (code == 1) {
                    try {
                        var context = getContext();
                        var persistent = canPersistent(context);
                        overrideConfig(context, persistent);
                    } catch (Exception e) {
                        Log.e(TAG, Log.getStackTraceString(e));
                    }
                    var handler = new Handler(Looper.getMainLooper());
                    handler.postDelayed(() -> finish(0, new Bundle()), 1000);
                    return true;
                }
                return super.onTransact(code, data, reply, flags);
            }
        };
        var extras = new Bundle();
        extras.putBinder("binder", binder);
        var cr = getContext().getContentResolver();
        cr.call(BuildConfig.APPLICATION_ID + ".shizuku", METHOD_GET_BINDER, null, extras);
    }

    @SuppressLint("PrivateApi")
    private static boolean canPersistent(Context context) {
        try {
            var gms = context.createPackageContext("com.android.phone",
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            var clazz = gms.getClassLoader().loadClass("com.android.phone.CarrierConfigLoader");
            try {
                clazz.getDeclaredMethod("isSystemApp");
            } catch (NoSuchMethodException e) {
                return true;
            }
            clazz.getDeclaredMethod("secureOverrideConfig", PersistableBundle.class, boolean.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressLint("MissingPermission")
    private static void overrideConfig(Context context, boolean persistent) {
        var cm = context.getSystemService(CarrierConfigManager.class);
        var sm = context.getSystemService(SubscriptionManager.class);
        var values = getConfig();
        values.putInt("vvb2060_config_version", BuildConfig.VERSION_CODE);
        var infos = sm.getActiveSubscriptionInfoList();
        if (infos == null) {
            return;
        }
        for (var info : infos) {
            if (info == null) {
                continue;
            }
            var subId = info.getSubscriptionId();
            var bundle = cm.getConfigForSubId(subId);
            var hasMarker = bundle != null && bundle.getInt("vvb2060_config_version", 0) != 0;
            if (info.isEmbedded()) {
                if (hasMarker) {
                    clearOverride(cm, subId, persistent);
                }
                continue;
            }
            if (bundle == null || bundle.getInt("vvb2060_config_version", 0) != BuildConfig.VERSION_CODE) {
                if (hasMarker) {
                    clearOverride(cm, subId, persistent);
                }
                invokeOverrideConfig(cm, subId, values, persistent);
            }
        }
    }

    private static void clearOverride(CarrierConfigManager cm, int subId, boolean persistent) {
        try {
            invokeOverrideConfig(cm, subId, null, persistent);
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
            if (persistent) {
                try {
                    invokeOverrideConfig(cm, subId, null, false);
                } catch (Exception ex) {
                    Log.e(TAG, Log.getStackTraceString(ex));
                }
            }
        }
    }

    private static PersistableBundle getConfig() {
        var bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_SUPPORTS_SS_OVER_UT_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VT_AVAILABLE_BOOL, true);

        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_CROSS_SIM_IMS_AVAILABLE_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_ENABLE_CROSS_SIM_CALLING_ON_OPPORTUNISTIC_DATA_BOOL, true);

        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_WFC_SUPPORTS_WIFI_ONLY_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_EDITABLE_WFC_MODE_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_EDITABLE_WFC_ROAMING_MODE_BOOL, true);
        bundle.putBoolean(KEY_SHOW_WIFI_CALLING_ICON_IN_STATUS_BAR_BOOL, true);
        bundle.putInt(KEY_WFC_SPN_FORMAT_IDX_INT, 6);

        bundle.putBoolean(CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL, false);
        bundle.putBoolean(CarrierConfigManager.KEY_HIDE_LTE_PLUS_DATA_ICON_BOOL, false);

        bundle.putBoolean(CarrierConfigManager.KEY_VONR_ENABLED_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_VONR_SETTING_VISIBILITY_BOOL, true);
        bundle.putIntArray(CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY,
                new int[]{CarrierConfigManager.CARRIER_NR_AVAILABILITY_NSA,
                        CarrierConfigManager.CARRIER_NR_AVAILABILITY_SA});
        bundle.putIntArray(CarrierConfigManager.KEY_5G_NR_SSRSRP_THRESHOLDS_INT_ARRAY,
                // Boundaries: [-140 dBm, -44 dBm]
                new int[]{
                        -128, /* SIGNAL_STRENGTH_POOR */
                        -118, /* SIGNAL_STRENGTH_MODERATE */
                        -108, /* SIGNAL_STRENGTH_GOOD */
                        -98,  /* SIGNAL_STRENGTH_GREAT */
                });
        return bundle;
    }

    private static void invokeOverrideConfig(CarrierConfigManager cm, int subId,
                                             PersistableBundle values, boolean persistent) {
        try {
            var method = CarrierConfigManager.class.getMethod("overrideConfig",
                    int.class, PersistableBundle.class, boolean.class);
            method.invoke(cm, subId, values, persistent);
            return;
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {
            var method = CarrierConfigManager.class.getMethod("overrideConfig",
                    int.class, PersistableBundle.class);
            method.invoke(cm, subId, values);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String getCarrierConfigKey(String fieldName, String fallback) {
        try {
            var field = CarrierConfigManager.class.getField(fieldName);
            var value = field.get(null);
            if (value instanceof String) {
                return (String) value;
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }
}
