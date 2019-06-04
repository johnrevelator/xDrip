package com.eveningoutpost.dexdrip.insulin.watlaa;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;
import android.preference.PreferenceManager;

import com.eveningoutpost.dexdrip.ImportedLibraries.usbserial.util.HexDump;
import com.eveningoutpost.dexdrip.Models.JoH;
import com.eveningoutpost.dexdrip.Models.PenData;
import com.eveningoutpost.dexdrip.Models.UserError;
import com.eveningoutpost.dexdrip.Services.JamBaseBluetoothSequencer;
import com.eveningoutpost.dexdrip.UtilityModels.Inevitable;
import com.eveningoutpost.dexdrip.UtilityModels.PersistentStore;
import com.eveningoutpost.dexdrip.UtilityModels.Pref;
import com.eveningoutpost.dexdrip.insulin.shared.ProcessPenData;
import com.eveningoutpost.dexdrip.insulin.watlaa.messages.BatteryRx;
import com.eveningoutpost.dexdrip.insulin.watlaa.messages.RecordRx;
import com.eveningoutpost.dexdrip.insulin.watlaa.messages.TimeRx;
import com.eveningoutpost.dexdrip.insulin.watlaa.messages.UnitTx;
import com.eveningoutpost.dexdrip.insulin.watlaa.messages.WatlaaEvent;
import com.eveningoutpost.dexdrip.utils.bt.Helper;
import com.eveningoutpost.dexdrip.utils.framework.WakeLockTrampoline;
import com.eveningoutpost.dexdrip.utils.time.SlidingWindowConstraint;
import com.polidea.rxandroidble.RxBleDeviceServices;
import com.polidea.rxandroidble.exceptions.BleGattCharacteristicException;
import com.polidea.rxandroidble.exceptions.BleGattException;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import static android.bluetooth.BluetoothDevice.BOND_BONDED;
import static android.bluetooth.BluetoothDevice.BOND_NONE;
import static com.eveningoutpost.dexdrip.ImportedLibraries.usbserial.util.HexDump.dumpHexString;
import static com.eveningoutpost.dexdrip.Models.JoH.emptyString;
import static com.eveningoutpost.dexdrip.Models.JoH.hourMinuteString;
import static com.eveningoutpost.dexdrip.Models.JoH.msSince;
import static com.eveningoutpost.dexdrip.Models.JoH.quietratelimit;
import static com.eveningoutpost.dexdrip.Services.JamBaseBluetoothSequencer.BaseState.CLOSE;
import static com.eveningoutpost.dexdrip.Services.JamBaseBluetoothSequencer.BaseState.INIT;
import static com.eveningoutpost.dexdrip.UtilityModels.Constants.INPEN_SERVICE_FAILOVER_ID;
import static com.eveningoutpost.dexdrip.UtilityModels.Constants.MINUTE_IN_MS;
import static com.eveningoutpost.dexdrip.UtilityModels.Constants.SECOND_IN_MS;
import static com.eveningoutpost.dexdrip.insulin.watlaa.Watlaa.STORE_WATLAA_BATTERY;
import static com.eveningoutpost.dexdrip.insulin.watlaa.WatlaaService.WatlaaState.GET_BATTERY;

/**
 * jamorham
 * <p>
 * InPen connection and data transfer service
 */

public class WatlaaService extends JamBaseBluetoothSequencer {

    private static final boolean D = false;
    private static final int MAX_BACKLOG = 80;
    private final ConcurrentLinkedQueue<byte[]> records = new ConcurrentLinkedQueue<>();

    private static TimeRx currentPenTime = null; // TODO hashmap for multiple pens?
    private static TimeRx currentPenAttachTime = null; // TODO hashmap for multiple pens?
    private int lastIndex = -1; // TODO hashmap for multiple pens? Use storage object for each pen??
    private int gotIndex = -2; // TODO hashmap for multiple pens? Use storage object for each pen??
    protected String TAG = this.getClass().getSimpleName();

    private static volatile String lastState = "None";
    private static volatile String lastError = null;
    private static volatile long lastStateUpdated = -1;
    private static volatile long lastReceivedData = -1;
    private static volatile boolean needsAuthentication = false;
    private static int lastBattery = -1;
    private static PenData lastPenData = null;
    private static boolean infosLoaded = false;
    private static boolean gotAll = false;
    private static ConcurrentHashMap<UUID, Object> staticCharacteristics;
    private static PendingIntent serviceFailoverIntent;
    private static long failover_time;

    {

        mState = new WatlaaState().setLI(I);
        I.backgroundStepDelay = 0;
        I.autoConnect = true;
        I.autoReConnect = true; // TODO control these two from preference?
        I.playSounds = true;
        I.connectTimeoutMinutes = 25;
        I.reconnectConstraint = new SlidingWindowConstraint(30, MINUTE_IN_MS, "max_reconnections");
        //I.resetWhenAlreadyConnected = true;
    }


    static class WatlaaState extends JamBaseBluetoothSequencer.BaseState {

        static final String GET_BATTERY = "Get Battery";


        {
            sequence.clear();

            sequence.add(INIT);
            sequence.add(CONNECT_NOW);
            sequence.add(DISCOVER); // automatically executed
            sequence.add(GET_BATTERY);
            sequence.add(SLEEP);

        }
    }


    @Override
    protected synchronized boolean automata() {
        extendWakeLock(1000);
        if (D) UserError.Log.d(TAG, "Automata called in watlaa");
        msg(I.state);

        switch (I.state) {

            case INIT:
                // connect by default
                changeNextState();
                break;
            case GET_BATTERY:
                UserError.Log.d("WatlaaLog", "Getting battery");

                getBattery();
                break;
            default:
                if (shouldServiceRun()) {
                    if (msSince(lastReceivedData) < MINUTE_IN_MS) {
                        Inevitable.task("watlaa-set-failover", 1000, this::setFailOverTimer);
                    }
                    return super.automata();
                } else {
                    UserError.Log.d(TAG, "Service should be shut down so stopping automata");
                }
        }
        return true;
    }



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        final PowerManager.WakeLock wl = JoH.getWakeLock("watlaa service", 60000);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        preferences.edit().putString("watlaa_batery","10").apply();
        CurrentTimeService.INSTANCE.startServer(this);

        if (!EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().register(this);
        try {
            WatlaaEntry.started_at = JoH.tsl();
            UserError.Log.d(TAG, "WAKE UP WAKE UP WAKE UP");
            if (shouldServiceRun()) {

                final String mac = Watlaa.getMac(); // load from settings class
                if (emptyString(mac)) {
                    // if mac not set then start up a scan and do nothing else
                    new FindNearby().scan();
                } else {

                    setAddress(mac);
                    commonServiceStart();
                    if (intent != null) {
                        final String function = intent.getStringExtra("function");
                        if (function != null) {
                            switch (function) {

                                case "failover":
                                    changeState(CLOSE);
                                    break;
                                case "reset":
                                    JoH.static_toast_long("Searching for Watlaa");
                                    Watlaa.setMac("");
                                    WatlaaEntry.startWithRefresh();
                                    CurrentTimeService.INSTANCE.restartServer(this);
                                    break;
                                case "refresh":
                                    currentPenAttachTime = null;
                                    currentPenTime = null;
                                    CurrentTimeService.INSTANCE.restartServer(this);
                                    changeState(INIT);
                                    break;


                                case "prototype":
                                    //     changeState(PROTOTYPE);
                                    break;
                            }
                        }
                    }
                }
                setFailOverTimer();
                return START_STICKY;
            } else {
                UserError.Log.d(TAG, "Service is NOT set be active - shutting down");
                stopSelf();
                return START_NOT_STICKY;
            }
        } finally {
            JoH.releaseWakeLock(wl);
        }
    }

    private void commonServiceStart() {
        I.playSounds = false;
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onWatlaaEvent(WatlaaEvent watlaaEvent) {
        switch (watlaaEvent.getType()) {
            case WatlaaEvent.CALLIBRATION:
                UserError.Log.d(TAG,watlaaEvent.getHeight().toString());
                UserError.Log.d(TAG,watlaaEvent.getLow().toString());
                sendHighCallibration(watlaaEvent.getHeight().toString());
                sendLowCallibration(watlaaEvent.getLow().toString());
                break;
            case WatlaaEvent.UNITS:
                UserError.Log.d(TAG,watlaaEvent.getValue());

                sendUnits(watlaaEvent.getValue());
                break;

        }
    }



    @Override
    public void onCreate() {
        super.onCreate();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        CurrentTimeService.INSTANCE.stopServer();
        if (EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this);
        WatlaaEntry.started_at = -1;
    }


    ///// Methods


    // TODO make sure we don't get stuck on a bum record
    private boolean checkMissingIndex() {
        final long missingIndex = PenData.getMissingIndex(I.address);
        if (missingIndex != -1) {
            UserError.Log.d(TAG, "Index: " + missingIndex + " is missing");
            //getRecords((int) missingIndex, (int) missingIndex);
            return true;
        }
        return false;
    }


    private void getBattery() {
        if (JoH.pratelimit("watlaa-battery-poll-" + I.address, 40000)) {
            I.connection.readCharacteristic(Constants.BATTERY_LEVEL_CHARACTERISTIC).subscribe(
                    batteryValue -> {
                        UserError.Log.d("WatlaaLog", new String(batteryValue) + " battery");
                        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putString("watlaa_batery",new String(batteryValue)).apply();

                        UserError.Log.d(TAG, new String(batteryValue));
                        UserError.Log.d("MyLog", new String(batteryValue));

                        final BatteryRx battery = new BatteryRx().fromBytes(batteryValue);
                        if (battery != null) {
                            lastBattery = battery.getBatteryPercent();
                            PersistentStore.setLong(STORE_WATLAA_BATTERY + I.address, lastBattery);
                            UserError.Log.d(TAG, "GetBattery result: " + battery.getBatteryPercent());
                            changeNextState();
                        } else {
                            UserError.Log.e(TAG, "Invalid GetBattery result: " + dumpHexString(batteryValue));
                            changeNextState();
                        }

                    }, throwable -> {
                        UserError.Log.e(TAG, "Could not read after Battery status: " + throwable);
                        changeNextState();
                    });

        } else {
            UserError.Log.d(TAG, "Skipping battery read");
            if (lastBattery == -1) {
                lastBattery = (int) PersistentStore.getLong(STORE_WATLAA_BATTERY + I.address);
                if (lastBattery == 0) {
                    lastBattery = -1;
                } else {
                    UserError.Log.d(TAG, "Loaded battery from store: " + lastBattery);
                }

            }
            changeNextState();
        }

    }


   private void sendUnits(String value) {
        if (JoH.pratelimit("watlaa-units-get-" + I.address, 40000)) {
            I.connection.writeCharacteristic(Constants.UNIT_CHARACTERISTIC, new UnitTx(value.getBytes()).getBytes()).subscribe(
                    valueResponse -> {
                        UserError.Log.d(TAG, "Sent Units ok: ");
                    }, throwable -> {
                        UserError.Log.e(TAG, "Could not write units " + throwable);
                    });
        }

    }

    private void sendLowCallibration(String value) {
        if (JoH.pratelimit("watlaa-units-get-" + I.address, 40000)) {
            I.connection.writeCharacteristic(Constants.CALLIBRATION_SLATE_CHARACTERISTIC, new UnitTx(value.getBytes()).getBytes()).subscribe(
                    valueResponse -> {
                        UserError.Log.d(TAG, "Sent Units ok: ");
                    }, throwable -> {
                        UserError.Log.e(TAG, "Could not write units " + throwable);
                    });
        }

    }

    private void sendHighCallibration(String value) {
        if (JoH.pratelimit("watlaa-units-get-" + I.address, 40000)) {
            I.connection.writeCharacteristic(Constants.CALLIBRATION_INTERCEPT_CHARACTERISTIC , new UnitTx(value.getBytes()).getBytes()).subscribe(
                    valueResponse -> {
                        UserError.Log.d(TAG, "Sent Units ok: ");
                    }, throwable -> {
                        UserError.Log.e(TAG, "Could not write units " + throwable);
                    });
        }

    }


    private void getRecords() {

        if (checkMissingIndex()) return; // processing from there instead

        if (lastIndex < 0) {
            UserError.Log.e(TAG, "Cannot get records as index is not defined");
            return;

        }
        final long highest = PenData.getHighestIndex(I.address);
        int firstIndex = highest > 0 ? (int) highest + 1 : 1;
        if (firstIndex > lastIndex) {
            UserError.Log.e(TAG, "First index is greater than last index: " + firstIndex + " " + lastIndex);
            return;
        }

        final int count = lastIndex - firstIndex;
        if (count > MAX_BACKLOG) {
            firstIndex = lastIndex - MAX_BACKLOG;
            UserError.Log.d(TAG, "Restricting first index to: " + firstIndex);
        }

        // getRecords(firstIndex, lastIndex);
    }


    private synchronized void processRecordsQueue() {
        boolean newRecord = false;
        while (!records.isEmpty()) {
            final byte[] record = records.poll();
            if (record != null) {
                final RecordRx recordRx = new RecordRx(currentPenTime).fromBytes(record);
                if (recordRx != null) {
                    /*UserError.Log.d(TAG, "RECORD RECORD: " + recordRx.toS());
                    final PenData penData = PenData.create(I.address, ID_INPEN, recordRx.index, recordRx.units, recordRx.getRealTimeStamp(), recordRx.temperature, record);
                    if (penData == null) {
                        UserError.Log.wtf(TAG, "Error creating PenData record from " + HexDump.dumpHexString(record));
                    } else {
                        penData.battery = recordRx.battery;
                        penData.flags = recordRx.flags;
                        UserError.Log.d(TAG, "Saving Record index: " + penData.index);
                        penData.save();
                        newRecord = true;
                        gotIndex = (int) penData.index;
                        gotAll = lastIndex == gotIndex;
                        if (Watlaa.soundsEnabled() && JoH.ratelimit("pen_data_in", 1)) {
                            JoH.playResourceAudio(R.raw.bt_meter_data_in);
                        }
                        lastPenData = penData;
                    }*/
                } else {
                    UserError.Log.e(TAG, "Error creating record from: " + HexDump.dumpHexString(record));
                }
            }
        }
        if (newRecord) {
            Inevitable.task("process-inpen-data", 1000, ProcessPenData::process);
        }
    }


    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void bondAsRequired(final boolean wait) {
        final BluetoothDevice device = I.bleDevice.getBluetoothDevice();
        final int bondState = device.getBondState();
        if (bondState == BOND_NONE) {
            final boolean bondResultCode = device.createBond();
            UserError.Log.d(TAG, "Attempted create bond: result: " + bondResultCode);
        } else {
            UserError.Log.d(TAG, "Device is already in bonding state: " + Helper.bondStateToString(bondState));
        }

        if (wait) {
            for (int c = 0; c < 10; c++) {
                if (device.getBondState() == BOND_BONDED) {
                    UserError.Log.d(TAG, "Bond created!");
                    changeNextState();
                    break;
                } else {
                    UserError.Log.d(TAG, "Sleeping waiting for bond: " + c);
                    JoH.threadSleep(1000);
                }
            }
        }
    }

    public void tryGattRefresh() {
        if (JoH.ratelimit("inpen-gatt-refresh", 60)) {
            if (Pref.getBoolean("use_gatt_refresh", true)) {
                try {
                    if (I.connection != null)
                        UserError.Log.d(TAG, "Trying gatt refresh queue");
                    I.connection.queue((new GattRefreshOperation(0))).timeout(2, TimeUnit.SECONDS).subscribe(
                            readValue -> {
                                UserError.Log.d(TAG, "Refresh OK: " + readValue);
                            }, throwable -> {
                                UserError.Log.d(TAG, "Refresh exception: " + throwable);
                            });
                } catch (NullPointerException e) {
                    UserError.Log.d(TAG, "Probably harmless gatt refresh exception: " + e);
                } catch (Exception e) {
                    UserError.Log.d(TAG, "Got exception trying gatt refresh: " + e);
                }
            } else {
                UserError.Log.d(TAG, "Gatt refresh rate limited");
            }
        }
    }


    ///////////////

    static final List<UUID> huntCharacterstics = new ArrayList<>();

    static {
        huntCharacterstics.add(Constants.BATTERY_LEVEL_CHARACTERISTIC); // specimen TODO improve
    }

    @Override
    protected void onServicesDiscovered(RxBleDeviceServices services) {
        boolean found = false;
        super.onServicesDiscovered(services);
        for (BluetoothGattService service : services.getBluetoothGattServices()) {
            if (D) UserError.Log.d(TAG, "Service: " + getUUIDName(service.getUuid()));
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                if (D)
                    UserError.Log.d(TAG, "-- Character: " + getUUIDName(characteristic.getUuid()));

                for (final UUID check : huntCharacterstics) {
                    if (characteristic.getUuid().equals(check)) {
                        I.readCharacteristic = check;
                        found = true;
                    }
                }
            }
        }
        if (found) {
            I.isDiscoveryComplete = true;
            I.discoverOnce = true;
            changeState(mState.next());
        } else {
            UserError.Log.e(TAG, "Could not find characteristic during service discovery. This is very unusual");
            tryGattRefresh();
        }
    }

    private boolean isErrorResponse(final Object throwable) {
        return throwable instanceof BleGattCharacteristicException && ((BleGattException) throwable).getStatus() == 1;
    }


    private void setFailOverTimer() {
        if (shouldServiceRun()) {
            if (quietratelimit("watlaa-failover-cooldown", 30)) {
                final long retry_in = MINUTE_IN_MS * 45;
                UserError.Log.d(TAG, "setFailOverTimer: Restarting in: " + (retry_in / SECOND_IN_MS) + " seconds");

                serviceFailoverIntent = WakeLockTrampoline.getPendingIntent(this.getClass(), INPEN_SERVICE_FAILOVER_ID, "failover");
                failover_time = JoH.wakeUpIntent(this, retry_in, serviceFailoverIntent);
            }
        } else {
            UserError.Log.d(TAG, "Not setting retry timer as service should not be running");
        }
    }

    private static boolean shouldServiceRun() {
        return WatlaaEntry.isEnabled();
    }

    private static void msg(final String msg) {
        lastState = msg + " " + hourMinuteString();
        lastStateUpdated = JoH.tsl();
    }

    private void err(final String msg) {
        lastError = msg + " " + hourMinuteString();
        UserError.Log.wtf(TAG, msg);
    }

/*
    // data for MegaStatus
    public static List<StatusItem> megaStatus() {
        final List<StatusItem> l = new ArrayList<>();
        if (lastError != null) {
            l.add(new StatusItem("Last Error", lastError, BAD));
        }
        if (isStarted()) {
            l.add(new StatusItem("Service Running", JoH.niceTimeScalar(msSince(WatlaaEntry.started_at))));

            l.add(new StatusItem("Brain State", lastState));

            if (needsAuthentication) {
                l.add(new StatusItem("Authentication", "Required", BAD));
            }

        } else {
            l.add(new StatusItem("Service Stopped", "Not running"));
        }

        if (lastReceivedData != -1) {
            l.add(new StatusItem("Last Connected", dateTimeText(lastReceivedData)));
        }

        // TODO remaining records!

        if (lastPenData != null) {
            l.add(new StatusItem("Last record", lastPenData.brief(), gotAll ? GOOD : NORMAL));
        }

        if (lastBattery != -1) {
            l.add(new StatusItem("Battery", lastBattery + "%"));
        }

        for (final UUID uuid : INFO_CHARACTERISTICS) {
            addStatusForCharacteristic(l, getCharactersticName(uuid.toString()), uuid);
        }

        if ((currentPenAttachTime != null) && (currentPenTime != null)) {
            l.add(new StatusItem("Epoch time", dateTimeText(currentPenTime.getPenEpoch())));
            l.add(new StatusItem("Attach time", dateTimeText(currentPenTime.fromPenTime(currentPenAttachTime.getPenTime()))));
        }
        //
        return l;
    }

    private static void addStatusForCharacteristic(List<StatusItem> l, String name, UUID characteristic) {
        String result = null;
        if (Arrays.asList(PRINTABLE_INFO_CHARACTERISTICS).contains(characteristic)) {
            result = getCharacteristicString(characteristic);
        } else if (Arrays.asList(HEXDUMP_INFO_CHARACTERISTICS).contains(characteristic)) {
            result = getCharacteristicHexString(characteristic);
        }
        if (result != null) {
            l.add(new StatusItem(name, result));
        }
    }*/

    private static String getCharacteristicString(final UUID uuid) {
        if (staticCharacteristics == null) return null;
        final Object objx = staticCharacteristics.get(uuid);
        if (objx != null) {
            if (objx instanceof byte[]) {
                return new String((byte[]) objx);
            }
        }
        return null;
    }

    private static String getCharacteristicHexString(final UUID uuid) {
        if (staticCharacteristics == null) return null;
        final Object objx = staticCharacteristics.get(uuid);
        if (objx != null) {
            if (objx instanceof byte[]) {
                return JoH.bytesToHex((byte[]) objx);
            }
        }
        return null;
    }


}
