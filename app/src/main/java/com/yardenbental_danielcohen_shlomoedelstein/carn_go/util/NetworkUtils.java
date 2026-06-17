package com.yardenbental_danielcohen_shlomoedelstein.carn_go.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.widget.Toast;

public final class NetworkUtils {

    private NetworkUtils() {}

    public static boolean isConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    /** Returns true if connected; otherwise shows a toast and returns false. */
    public static boolean checkAndToast(Context context) {
        if (isConnected(context)) return true;
        Toast.makeText(context, context.getString(com.yardenbental_danielcohen_shlomoedelstein.carn_go.R.string.no_internet), Toast.LENGTH_SHORT).show();
        return false;
    }
}
