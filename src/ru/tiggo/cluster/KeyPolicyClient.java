package ru.tiggo.cluster;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.view.KeyEvent;

/**
 * Клиент службы Desay «key_policy» (android.view.IKeyPolicy в фреймворке прошивки).
 * Кнопки руля обходят accessibility-фильтр: их раздаёт KeyPolicyImpl.notifyForDispatchKeyEvent
 * зарегистрированным callback'ам по очереди, пока кто-то не вернёт true.
 * Своих классов IKeyPolicy в SDK нет, поэтому Parcel собираем вручную по байткоду прокси:
 *   IKeyPolicy:        1 = registerKeyCallBack(IKeyPolicyCallBack, String name, int, String)
 *                      2 = unRegisterKeyCallBack(String name, String)
 *   IKeyPolicyCallBack: 1 = boolean callbackEvent(KeyEvent, String slot)
 */
final class KeyPolicyClient extends Binder {
    interface Listener {
        /** true — нажатие обработано, штатный обработчик его не получит. */
        boolean onKey(KeyEvent event, String slot);
    }

    private static final String POLICY = "android.view.IKeyPolicy";
    private static final String CALLBACK = "android.view.IKeyPolicyCallBack";
    private static final int TX_REGISTER = IBinder.FIRST_CALL_TRANSACTION;
    private static final int TX_UNREGISTER = IBinder.FIRST_CALL_TRANSACTION + 1;
    private static final int TX_CALLBACK_EVENT = IBinder.FIRST_CALL_TRANSACTION;

    private final String name;
    private final String tag;
    private final Listener listener;

    KeyPolicyClient(String name, String tag, Listener listener) {
        this.name = name;
        this.tag = tag;
        this.listener = listener;
        attachInterface(null, CALLBACK);
    }

    boolean register() {
        IBinder service = keyPolicyService();
        if (service == null) return false;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(POLICY);
            data.writeStrongBinder(this);
            data.writeString(name);
            data.writeInt(0);
            data.writeString(tag);
            service.transact(TX_REGISTER, data, reply, 0);
            reply.readException();
            return true;
        } catch (RemoteException | RuntimeException e) {
            EventLog.i("key_policy: регистрация «" + name + "» не удалась: " + e);
            return false;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    void unregister() {
        IBinder service = keyPolicyService();
        if (service == null) return;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(POLICY);
            data.writeString(name);
            data.writeString(tag);
            service.transact(TX_UNREGISTER, data, reply, 0);
            reply.readException();
        } catch (RemoteException | RuntimeException ignored) {
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (code != TX_CALLBACK_EVENT) return super.onTransact(code, data, reply, flags);
        data.enforceInterface(CALLBACK);
        KeyEvent event = data.readInt() != 0 ? KeyEvent.CREATOR.createFromParcel(data) : null;
        String slot = data.readString();
        boolean handled = false;
        if (event != null) {
            try {
                handled = listener.onKey(event, slot);
            } catch (RuntimeException e) {
                EventLog.i("Ошибка обработки кнопки: " + e);
            }
        }
        reply.writeNoException();
        reply.writeInt(handled ? 1 : 0);
        return true;
    }

    /** android.os.ServiceManager скрыт от SDK, но на Android 9 доступен через рефлексию. */
    private static IBinder keyPolicyService() {
        try {
            return (IBinder) Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class).invoke(null, "key_policy");
        } catch (ReflectiveOperationException e) {
            EventLog.i("key_policy: служба недоступна: " + e);
            return null;
        }
    }
}
