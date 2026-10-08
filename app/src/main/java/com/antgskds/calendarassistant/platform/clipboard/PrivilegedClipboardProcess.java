package com.antgskds.calendarassistant.platform.clipboard;

import android.content.ClipData;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog;
import org.json.JSONObject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Runs ONLY via app_process in the existing Root/Shizuku process launcher.
 * Follows scrcpy's Shell IClipboard approach, independently implemented for Will do:
 * https://github.com/Genymobile/scrcpy/blob/v2.7/server/src/main/java/com/genymobile/scrcpy/wrappers/ClipboardManager.java
 * Text travels through a private process pipe, never logcat, intents or shell arguments.
 */
public final class PrivilegedClipboardProcess {
    private static final String SHELL_PACKAGE = "com.android.shell";
    private static final String LISTENER = "android.content.IOnPrimaryClipChangedListener";
    private final int userId;
    private final int deviceId;
    private final Object clipboard;
    private final Method readMethod;

    private PrivilegedClipboardProcess(int userId, int deviceId) throws Exception {
        this.userId = userId;
        this.deviceId = deviceId;
        IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "clipboard");
        if (binder == null) throw new IllegalStateException("ClipboardServiceUnavailable");
        clipboard = Class.forName("android.content.IClipboard$Stub")
                .getMethod("asInterface", IBinder.class).invoke(null, binder);
        readMethod = findMethod("getPrimaryClip");
    }

    // Framework signatures differ by Android version and vendor. Do not hardcode Binder codes.
    private Method findMethod(String name) throws NoSuchMethodException {
        for (Method method : clipboard.getClass().getMethods()) {
            if (!method.getName().equals(name)) continue;
            boolean supported = true;
            for (Class<?> type : method.getParameterTypes()) {
                supported &= type == String.class || type == int.class || type == boolean.class
                        || type.getName().equals(LISTENER);
            }
            if (supported) { method.setAccessible(true); return method; }
        }
        throw new NoSuchMethodException(name);
    }

    private Object[] arguments(Method method, Object listener) {
        Class<?>[] types = method.getParameterTypes();
        Object[] values = new Object[types.length];
        int strings = 0;
        int ints = 0;
        for (int i = 0; i < types.length; ++i) {
            if (types[i] == String.class) values[i] = strings++ == 0 ? SHELL_PACKAGE : null;
            else if (types[i] == int.class) values[i] = ints++ == 0 ? userId : deviceId;
            else if (types[i] == boolean.class) values[i] = true; // vendor userOperate
            else values[i] = listener;
        }
        return values;
    }

    private void read() {
        try {
            ClipData clip = (ClipData) readMethod.invoke(clipboard, arguments(readMethod, null));
            JSONObject event = event("clip");
            if (clip == null) event.put("result", "null_clip");
            else {
                event.put("item_count", clip.getItemCount());
                event.put("clipboard_time_ms", clip.getDescription().getTimestamp());
                CharSequence text = clip.getItemCount() > 0 ? clip.getItemAt(0).getText() : null;
                if (text == null) event.put("result", "no_text");
                else if (text.length() > ConfigCatalog.CLIPBOARD_PROCESS_MAX_TEXT_CHARS)
                    event.put("result", "text_too_large");
                else event.put("result", "text").put("text", text.toString());
            }
            emit(event);
        } catch (Exception error) { emitError(error); }
    }

    private void watch() throws Exception {
        if (Looper.getMainLooper() == null) Looper.prepareMainLooper();
        Handler handler = new Handler(Looper.getMainLooper());
        Binder callback = new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                if (code == IBinder.FIRST_CALL_TRANSACTION) {
                    data.enforceInterface(LISTENER);
                    handler.post(PrivilegedClipboardProcess.this::read);
                    if (reply != null) reply.writeNoException();
                    return true;
                }
                return super.onTransact(code, data, reply, flags);
            }
        };
        Class<?> listenerType = Class.forName(LISTENER);
        IInterface listener = (IInterface) Proxy.newProxyInstance(listenerType.getClassLoader(),
                new Class<?>[]{listenerType}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "asBinder": return callback;
                        case "dispatchPrimaryClipChanged": handler.post(this::read); return null;
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        case "toString": return "WillDoClipboardListener";
                        default: return null;
                    }
                });
        callback.attachInterface(listener, LISTENER);
        Method add = findMethod("addPrimaryClipChangedListener");
        add.invoke(clipboard, arguments(add, listener));
        emit(event("ready").put("result", "listening"));
        Looper.loop();
    }

    private static JSONObject event(String type) throws Exception {
        return new JSONObject().put("type", type).put("uid", Process.myUid());
    }

    private static synchronized void emit(JSONObject event) {
        System.out.println(event.toString());
        System.out.flush();
    }

    private static void emitError(Exception error) {
        Throwable cause = error instanceof InvocationTargetException ? error.getCause() : error;
        try { emit(event("error").put("result", "error").put("error_type", cause.getClass().getSimpleName())); }
        catch (Exception ignored) { }
    }

    public static void main(String[] args) {
        // Exit when the app-side pipe closes; do not leave a privileged daemon behind.
        Thread owner = new Thread(() -> {
            try { while (System.in.read() != -1) { } } catch (Exception ignored) { }
            System.exit(0);
        }, "clipboard-owner");
        owner.setDaemon(true);
        owner.start();
        try {
            if (Process.myUid() != 0 && Process.myUid() != 2000) throw new SecurityException("NotPrivileged");
            PrivilegedClipboardProcess process = new PrivilegedClipboardProcess(Integer.parseInt(args[1]), Integer.parseInt(args[2]));
            if ("read".equals(args[0])) process.read();
            else if ("watch".equals(args[0])) process.watch();
            else throw new IllegalArgumentException("UnknownMode");
        } catch (Exception error) { emitError(error); }
    }
}
