package com.adguard.webbatterytester;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;

public class Utils {

    public static final String TAG = "Utils";

    /**
     * Formats traffic value.
     *
     * @param context Context
     * @param data    long traffic value
     * @return formatted value, ie 123.45 Mb.
     */
    public static String formatTrafficWithDecimal(Context context, long data) {
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
    public static boolean copyFileFromAssets(Context context, String filename, String filepath) {
        AssetManager am = context.getAssets();
        InputStream is = null;
        OutputStream os = null;

        Log.i("copyFileFromAssets", "copy '" + filename + "' to '" + filepath + "'");

        try {
            try {
                is = am.open(filename);

                File file = new File(filepath);
                file.createNewFile();           // create new file
                os = new FileOutputStream(file);

                byte[] buffer = new byte[1024];
                int length;
                while ((length = is.read(buffer)) > 0)
                    os.write(buffer, 0, length);
            } finally {
                if (os != null) {
                    os.flush();
                    os.close();
                }
                if (is != null)
                    is.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public static String readOneLine(String filePath, int lineNumber) {
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

    public static String execute(String command) {
        StringBuilder output = new StringBuilder();
        try {
            Process p = Runtime.getRuntime().exec(command);
            p.waitFor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return output.toString();
    }

    public static String readCalculatedDrain() {
        try {
            Process p = Runtime.getRuntime().exec("dumpsys batterystats");
            p.waitFor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

            boolean found = false;
            String line;
            while ((line = reader.readLine()) != null) {
                //Log.d(TAG, "Line: " + line);
                if (!found && line.contains("Estimated power use")) {
                    found = true;
                } else if (found) {
                    String[] buf = line.split(", ");
                    int pos = buf[1].indexOf(": ");
                    return buf[1].substring(pos + 2);
                }
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String readCalculateDrainByRoot() {
        String result = null;
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("dumpsys batterystats\n");
            os.flush();
            Thread.sleep(1000);
            //p.waitFor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

            boolean found = false;
            String line;
            while ((line = reader.readLine()) != null) {
                if (!found && line.contains("Estimated power use")) {
                    found = true;
                } else if (found) {
                    String[] buf = line.split(", ");
                    int pos = buf[1].indexOf(": ");
                    result = buf[1].substring(pos + 2);
                    break;
                }
            }
            //os.writeBytes("exit\n");
            //os.flush();
            p.destroy();
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public static float readCalculateDrainByRootFloat() {
        final String s = readCalculateDrainByRoot();
        if (s == null) {
            return 0f;
        }
        return Float.parseFloat(s.replace(',', '.'));
    }

    public static long readCpuTime() {
        String line = readOneLine("/proc/stat", 0);
        if (line != null) {
            line = line.substring(5);
            final String[] columns = line.split(" ");
            return Long.parseLong(columns[0]) + Long.parseLong(columns[1]) + Long.parseLong(columns[2]);
        }
        return 0;
    }

    public static long readSentReceivedData() {
        String line = readOneLine("/proc/net/netstat", 3);
        if (line != null) {
            line = line.substring(7);
            final String[] columns = line.split(" ");
            return Long.parseLong(columns[6]) + Long.parseLong(columns[7]);
        }
        return 0;
    }

    public static long readRemoteData() {
        try {
            final FileReader fileReader = new FileReader("/proc/net/dev");
            BufferedReader reader = new BufferedReader(fileReader);
            String line;
            long traffic = 0;
            while ((line = reader.readLine()) != null) {
                //Log.d(TAG, "Line read: " + line);
                if (line.contains("rmnet_ipa")) {
                    //Log.d(TAG, "Ignoring line: " + line);
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
                    final String[] columns = line.split(" ");
                    final long received = Long.parseLong(columns[0]);
                    final long transmitted = Long.parseLong(columns[8]);
                    traffic += received + transmitted;
                    //Log.d(TAG, "Traffic: Received: " + received + " Transmitted: " + traffic + " Sum: " + traffic);
                }
            }
            reader.close();
            return traffic;
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static String loadDomains(Context context) {
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
}
