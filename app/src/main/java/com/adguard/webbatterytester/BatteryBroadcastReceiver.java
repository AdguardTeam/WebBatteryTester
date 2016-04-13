package com.adguard.webbatterytester;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;

/**
 * Created by Revertron on 05.04.2016.
 */
class BatteryBroadcastReceiver extends BroadcastReceiver {

    private int temperature = 0;
    private int voltage = 0;
    private float batteryPercent = 0f;

    @Override
    public void onReceive(Context context, Intent intent) {
        int health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, 0);
        String healthType = getHealthString(health);
        int iconSmallResource = intent.getIntExtra(BatteryManager.EXTRA_ICON_SMALL, 0);
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        String plugType = getPlugTypeString(plugged);
        boolean present = intent.getExtras().getBoolean(BatteryManager.EXTRA_PRESENT);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 0);
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
        String statusType = getStatusString(status);
        String technology = intent.getExtras().getString(BatteryManager.EXTRA_TECHNOLOGY);
        temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10;
        voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
        batteryPercent = level * 100 / (float) scale;
    }

    public float getBatteryPercent() {
        return batteryPercent;
    }

    public int getVoltage() {
        return voltage;
    }

    public int getTemperature() {
        return temperature;
    }

    public void setStartValues(int temperature, int voltage, float batteryPercent) {
        this.temperature = temperature;
        this.voltage = voltage;
        this.batteryPercent = batteryPercent;
    }

    private String getPlugTypeString(int plugged) {
        String plugType = "Unknown";

        switch (plugged) {
            case BatteryManager.BATTERY_PLUGGED_AC:
                plugType = "AC";
                break;
            case BatteryManager.BATTERY_PLUGGED_USB:
                plugType = "USB";
                break;
        }
        return plugType;
    }

    private String getHealthString(int health) {
        String healthString = "Unknown";
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_DEAD:
                healthString = "Dead";
                break;
            case BatteryManager.BATTERY_HEALTH_GOOD:
                healthString = "Good Condition";
                break;
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                healthString = "Over Voltage";
                break;
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:
                healthString = "Over Heat";
                break;
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
                healthString = "Failure";
                break;
        }
        return healthString;
    }

    private String getStatusString(int status) {
        String statusString = "Unknown";

        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
                statusString = "Charging";
                break;
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
                statusString = "Discharging";
                break;
            case BatteryManager.BATTERY_STATUS_FULL:
                statusString = "Full";
                break;
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                statusString = "Not Charging";
                break;
        }
        return statusString;
    }
}
