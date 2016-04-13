package com.adguard.webbatterytester;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.BatteryManager;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements Runnable {

    private WebView webView;
    private Thread thread = null;
    private static int startVoltage = 0;
    private static int startTemperature = 0;
    private static float startBatteryPercent = 0f;
    private BatteryBroadcastReceiver batteryInfoReceiver = new BatteryBroadcastReceiver();
    private MyWebViewClient webViewClient;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_PROGRESS);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent intent = registerReceiver(batteryInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent != null && intent.hasExtra(BatteryManager.EXTRA_LEVEL)) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 0);
            startBatteryPercent = level * 100 / (float) scale;
            startTemperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10;
            startVoltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
            batteryInfoReceiver.setStartValues(startTemperature, startVoltage, startBatteryPercent);
        }

        // As we're using a Toolbar, we should retrieve it and set it to be our ActionBar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        progressBar = (ProgressBar) findViewById(R.id.progressPar);
        progressBar.setMax(1000);

        webView = (WebView) findViewById(R.id.webView);
        webViewClient = new MyWebViewClient();
        webView.setWebViewClient(webViewClient);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(false);
        webSettings.setLoadsImagesAutomatically(true);

        final String dataPath = getApplicationContext().getFilesDir().getAbsolutePath();
        Utils.copyFileFromAssets(this.getApplicationContext(), "test.txt", dataPath + "/test.txt");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.settings:
                showSettings();
                return true;
            case R.id.help:
                showHelp();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void showHelp() {
        startActivity(new Intent(this, HelpActivity.class));
    }

    private void showSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    public void startClicked(View view) {
        findViewById(R.id.banner).setVisibility(View.GONE);
        if (thread != null) {
            thread.interrupt();
            thread = null;
            ((AppCompatButton) findViewById(R.id.start_btn)).setText(R.string.start_test);
        } else {
            enableActionButtons(false);
            webView.clearCache(true);
            ((AppCompatButton) findViewById(R.id.start_btn)).setText(R.string.stop_test);
            thread = new Thread(this);
            thread.start();
        }
    }

    private void enableActionButtons(boolean enable) {
        findViewById(R.id.settings).setEnabled(enable);
        findViewById(R.id.help).setEnabled(enable);
    }

    public long loadSitesInWebView() {
        if (Thread.currentThread().isInterrupted())
            return 0;

        long startTime = Utils.readCpuTime();

        try {
            String domainsList = Utils.loadDomains(MainActivity.this);
            domainsList = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getString(SettingsActivity.PREF_DOMAIN_LIST, domainsList);
            int repeatCount = PreferenceManager.getDefaultSharedPreferences(this).getInt(SettingsActivity.PREF_REPEAT_COUNT, 1);

            ProgressRunnable progressRunnable = new ProgressRunnable();


            if (domainsList != null) {
                String[] lines = domainsList.split("\\n");
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setVisibility(View.VISIBLE);
                        progressBar.setProgress(0);
                    }
                });

                int curLine = 0;
                int maxLine = lines.length * repeatCount;
                for (int i = 0; i < repeatCount; i++) {
                    for (String line : lines) {
                        if (Thread.currentThread().isInterrupted())
                            break;
                        line = line.trim();
                        if (line.isEmpty())
                            continue;

                        if (!line.toLowerCase().startsWith("http://") && !line.toLowerCase().startsWith("https://")) {
                            line = "http://" + line;
                        }

                        long loadStartTime = System.currentTimeMillis();
                        final String finalLine = line;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                webView.loadUrl(finalLine);
                            }
                        });

                        Thread.sleep(1000);

                        while (webViewClient.isLoading && (System.currentTimeMillis() - loadStartTime) < 15000) {
                            Thread.sleep(200);
                        }

                        Thread.sleep(1000);

                        curLine++;
                        progressRunnable.setProgress(curLine * 1000 / maxLine);
                        MainActivity.this.runOnUiThread(progressRunnable);
                    }
                    // Clearing cache between repeats
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            webView.clearCache(true);
                            Toast.makeText(MainActivity.this, "Repeating test...", Toast.LENGTH_LONG).show();
                        }
                    });
                    // Waiting for Runnable to work out
                    Thread.sleep(1000);
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(View.GONE);
            }
        });

        return Utils.readCpuTime() - startTime;
    }

    @Override
    public void run() {
        startBatteryPercent = batteryInfoReceiver.getBatteryPercent();
        startTemperature = batteryInfoReceiver.getTemperature();
        startVoltage = batteryInfoReceiver.getVoltage();

        long startData = Utils.readRemoteData();

        long startTime = System.currentTimeMillis();
        long cpuTime = 0;
        cpuTime += loadSitesInWebView();
        long testTime = System.currentTimeMillis() - startTime;

        float finishBatteryPercent = batteryInfoReceiver.getBatteryPercent();
        int finishTemperature = batteryInfoReceiver.getTemperature();
        int finishVoltage = batteryInfoReceiver.getVoltage();

        long testData = Utils.readRemoteData() - startData;
        final String dataString = Utils.formatTrafficWithDecimal(MainActivity.this, testData);

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Test results");
        final String message = String.format("Cpu time: %d (USER_HZ)\nLoad: %d%% (divide by cpu-cores)\nBattery: %+.1f%%\nVoltage: %+dmV\nTemperature: %+d°C\nBytes transmitted: %s",
                cpuTime, (cpuTime * 10) * 100 / testTime, finishBatteryPercent - startBatteryPercent, finishVoltage - startVoltage, finishTemperature - startTemperature, dataString);
        builder.setMessage(message);
        builder.setCancelable(false);
        builder.setNeutralButton("Copy & Close", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Test results", message);
                clipboard.setPrimaryClip(clip);
                dialog.dismiss();
            }
        });
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                enableActionButtons(true);
                webView.loadUrl("about:blank");
                ((AppCompatButton) findViewById(R.id.start_btn)).setText(R.string.start_test);
                builder.create().show();
            }
        });

        thread = null;
    }

    private class MyWebViewClient extends WebViewClient {

        private boolean isLoading = false;

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return false;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            isLoading = true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            isLoading = false;
        }
    }

    private class ProgressRunnable implements Runnable {
        int progress = 0;

        public void setProgress(int progress) {
            this.progress = progress;
        }

        @Override
        public void run() {
            progressBar.setProgress(progress);
        }
    }
}
