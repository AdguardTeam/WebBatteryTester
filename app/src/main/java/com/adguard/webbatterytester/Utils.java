package com.adguard.webbatterytester;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;
import android.util.Pair;

import com.google.common.net.InternetDomainName;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;

class Utils {

    private static final String TAG = "Utils";

    /**
     * Extracts host name from an URL
     *
     * @param url URL
     * @return host name
     */
    static String getHost(String url) {
        try {
            if (url == null || url.isEmpty()) {
                return null;
            }

            int firstIdx = url.indexOf("//");
            if (firstIdx == -1) {
                return null;
            }
            int nextSlashIdx = url.indexOf("/", firstIdx + 2);
            int startParamsIdx = url.indexOf("?", firstIdx + 2);
            int colonIdx = url.indexOf(":", firstIdx + 2);

            int lastIdx = nextSlashIdx;
            if (startParamsIdx > 0 && (startParamsIdx < nextSlashIdx || lastIdx == -1)) {
                lastIdx = startParamsIdx;
            }

            if (colonIdx > 0 && (colonIdx < lastIdx || lastIdx == -1)) {
                lastIdx = colonIdx;
            }

            String hostName = lastIdx == -1 ? url.substring(firstIdx + 2) : url.substring(firstIdx + 2, lastIdx);
            if (hostName.startsWith("www.")) {
                // Crop www
                hostName = hostName.substring("www.".length());
            }

            return hostName;
        } catch (Exception ex) {
            Log.e(TAG, "Cannot extract hostname from " + url, ex);
            return null;
        }
    }

    /**
     * Checks if request is third party or not
     *
     * @param url     URL
     * @param pageUrl Page URL
     * @return true if request is third party
     */
    static boolean isThirdPartyRequest(String url, String pageUrl) {

        String requestHost = getHost(url);
        String pageHost = getHost(pageUrl);

        //noinspection SimplifiableIfStatement
        if (pageHost == null || requestHost == null) {
            return true;
        }

        InternetDomainName requestDomain = InternetDomainName.from(requestHost).topPrivateDomain();
        InternetDomainName pageDomain = InternetDomainName.from(pageHost).topPrivateDomain();

        return !requestDomain.equals(pageDomain) &&
                // For instance, arstechnica.net shouldn't be considered a third party for arstechnica.com
                !requestDomain.parts().get(0).equals(pageDomain.parts().get(0));
    }

    /**
     * Formats traffic value.
     *
     * @param context Context
     * @param data    long traffic value
     * @return formatted value, ie 123.45 Mb.
     */
    static String formatTrafficWithDecimal(Context context, long data) {
        String trafficFormatted;

        if (data < 1024 * 1024) {
            final float v = (float) data / 1024;
            trafficFormatted = String.format(context.getString(R.string.traffStatsValueTextViewTextTemplateKb), v);
        } else if (data < 1024 * 1024 * 1024) {
            final float v = (float) data / (1024 * 1024);
            trafficFormatted = String.format(context.getString(R.string.traffStatsValueTextViewTextTemplateMb), v);
        } else {
            final float v = (float) data / (1024 * 1024 * 1024);
            trafficFormatted = String.format(context.getString(R.string.traffStatsValueTextViewTextTemplateGb), v);
        }

        return trafficFormatted;
    }

    // copy file with filename from assets to new filepath
    static boolean copyFileFromAssets(Context context, String filename, String filepath) {
        AssetManager am = context.getAssets();
        InputStream is = null;
        OutputStream os = null;

        Log.i("copyFileFromAssets", "copy '" + filename + "' to '" + filepath + "'");

        try {
            try {
                is = am.open(filename);

                File file = new File(filepath);
                if (!file.createNewFile()) {
                    throw new IOException("Cannot create file " + filepath);
                }
                os = new FileOutputStream(file);

                byte[] buffer = new byte[1024];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    os.write(buffer, 0, length);
                }
            } finally {
                if (os != null) {
                    os.flush();
                    os.close();
                }
                if (is != null)
                    is.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Cannot copy file from assets: " + filepath, e);
            return false;
        }

        return true;
    }

    /**
     * Reads CPU time from /proc/stat
     *
     * @return CPU time
     */
    static long readCpuTime() {
        String line = readOneLine("/proc/stat", 0);
        if (line != null) {
            line = line.substring(5);
            final String[] columns = line.split(" ");
            return Long.parseLong(columns[0]) + Long.parseLong(columns[1]) + Long.parseLong(columns[2]);
        }
        return 0;
    }

    /**
     * Reads data usage from /proc/net/dev
     *
     * @return Pair where first value is number of bytes received and second value is bytes sent
     */
    static Pair<Long, Long> readDataUsage() {
        try {
            final FileReader fileReader = new FileReader("/proc/net/dev");
            BufferedReader reader = new BufferedReader(fileReader);
            String line;
            long received = 0;
            long transmitted = 0;
            while ((line = reader.readLine()) != null) {
                if (line.contains("rmnet_ipa")) {
                    continue;
                }
                if (line.contains("wlan") || line.contains("rmnet")) {
                    int pos = line.indexOf(':');
                    line = line.substring(pos + 1).trim();
                    int length;
                    do {
                        length = line.length();
                        line = line.replace("  ", " ");
                    } while (line.length() < length);

                    //Log.d(TAG, "Line replaced:" + line);
                    String[] columns = line.split(" ");
                    received += Long.parseLong(columns[0]);
                    transmitted += Long.parseLong(columns[8]);
                }
            }
            reader.close();
            return new Pair<>(received, transmitted);
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        return new Pair<>((long) 0, (long) 0);
    }

    static String loadDomains(Context context) {
        try {
            final String dataPath = context.getFilesDir().getAbsolutePath();
            FileInputStream is = new FileInputStream(dataPath + "/test.txt");
            BufferedReader br = new BufferedReader(new InputStreamReader(is, Charset.forName("US-ASCII"))); // UTF-8
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }

            is.close();
            return sb.toString().trim();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private static String readOneLine(String filePath, int lineNumber) {
        try {
            final FileReader fileReader = new FileReader(filePath);
            BufferedReader reader = new BufferedReader(fileReader);
            String line;
            int currentLine = 0;
            while ((line = reader.readLine()) != null) {
                //Log.d("Line", line);
                if (currentLine == lineNumber) {
                    reader.close();
                    return line;
                }
                currentLine++;
            }
            reader.close();
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
