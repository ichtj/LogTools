package com.face.logtools;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.ActivityCompat;

/**
 * 定位辅助类
 * 在 AndroidManifest.xml 中声明权限
 * <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
 * <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
 *
 * 使用方式:
 * LocationHelper helper = new LocationHelper(this);
 *
 * // 连续定位
 * helper.startLocationUpdates(1000, 1, new LocationHelper.LocationCallback() {
 *     @Override
 *     public void onLocationReceived(Location location) {
 *         Log.d("GPS", "lat=" + location.getLatitude() + " lon=" + location.getLongitude());
 *     }
 *
 *     @Override
 *     public void onLocationFailed(String reason) {
 *         Log.w("GPS", "定位失败: " + reason);
 *     }
 * });
 *
 * // 单次定位
 * helper.requestSingleLocation(new LocationHelper.LocationCallback() {
 *     @Override
 *     public void onLocationReceived(Location location) {
 *         Log.d("GPS", "单次定位 lat=" + location.getLatitude() + " lon=" + location.getLongitude());
 *     }
 *
 *     @Override
 *     public void onLocationFailed(String reason) {
 *         Log.w("GPS", "单次定位失败: " + reason);
 *     }
 * });
 *
 * // 停止定位
 * helper.stopLocationUpdates();
 */
public class LocationHelper {

    public interface LocationCallback {
        void onLocationReceived(Location location);
        void onLocationFailed(String reason);
    }

    private static final String TAG = "LocationHelper";

    private LocationManager locationManager;
    private LocationListener locationListener;
    private Context context;

    public LocationHelper(Context context) {
        this.context = context;
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    /**
     * 开始连续定位
     * @param minTimeMs 最小时间间隔
     * @param minDistanceM 最小移动距离
     * @param callback 回调
     */
    public void startLocationUpdates(long minTimeMs, float minDistanceM, final LocationCallback callback) {
        if (!checkPermission(callback)) return;

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                callback.onLocationReceived(location);
            }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(String provider) {}
            @Override public void onProviderDisabled(String provider) {
                callback.onLocationFailed(provider + " disabled");
            }
        };

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTimeMs, minDistanceM, locationListener);
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minTimeMs, minDistanceM, locationListener);
            }
        } catch (Exception e) {
            callback.onLocationFailed(e.getMessage());
            Log.e(TAG, "requestLocationUpdates failed", e);
        }
    }

    /**
     * 单次定位
     */
    public void requestSingleLocation(final LocationCallback callback) {
        if (!checkPermission(callback)) return;

        LocationListener singleListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                callback.onLocationReceived(location);
                stopLocationUpdates();
            }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(String provider) {}
            @Override public void onProviderDisabled(String provider) {
                callback.onLocationFailed(provider + " disabled");
            }
        };

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, singleListener, null);
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, singleListener, null);
            } else {
                callback.onLocationFailed("No provider enabled");
            }
        } catch (Exception e) {
            callback.onLocationFailed(e.getMessage());
            Log.e(TAG, "requestSingleUpdate failed", e);
        }
    }

    /**
     * 停止定位
     */
    public void stopLocationUpdates() {
        if (locationListener != null) {
            locationManager.removeUpdates(locationListener);
            locationListener = null;
        }
    }

    private boolean checkPermission(LocationCallback callback) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (callback != null) callback.onLocationFailed("No location permission");
            return false;
        }
        return true;
    }
}
