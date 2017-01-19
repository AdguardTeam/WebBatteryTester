package com.adguard.webbatterytester;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("ConstantConditions")
public class MainActivity extends AppCompatActivity implements Runnable {

    private final static String TAG = "WebBatteryTester";
    private WebView webView;
    private Thread thread = null;
    private static int startVoltage = 0;
    private static int startTemperature = 0;
    private static float startBatteryPercent = 0f;
    private BatteryBroadcastReceiver batteryInfoReceiver = new BatteryBroadcastReceiver();
    private MyWebViewClient webViewClient;
    private ProgressBar progressBar;

    @SuppressLint("SetJavaScriptEnabled")
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

        findViewById(R.id.start_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startClicked(v);
            }
        });

        progressBar = (ProgressBar) findViewById(R.id.progressPar);
        progressBar.setMax(1000);

        webView = (WebView) findViewById(R.id.webView);
        webViewClient = new MyWebViewClient();
        webView.setWebViewClient(webViewClient);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(true);
        webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(false);
        webSettings.setLoadsImagesAutomatically(true);
        clearCookies();

        final String dataPath = getApplicationContext().getFilesDir().getAbsolutePath();
        Utils.copyFileFromAssets(this.getApplicationContext(), "test.txt", dataPath + "/test.txt");
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(batteryInfoReceiver);
        super.onDestroy();
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
            webViewClient.reset();
            ((AppCompatButton) findViewById(R.id.start_btn)).setText(R.string.stop_test);
            thread = new Thread(this);
            thread.start();
        }
    }

    private void enableActionButtons(boolean enable) {
        findViewById(R.id.settings).setEnabled(enable);
        findViewById(R.id.help).setEnabled(enable);
    }

    /**
     * Clears cookies
     */
    private static void clearCookies() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.removeAllCookies(null);
        }
    }

    /**
     * @return List of domains to test
     */
    private List<String> getTestDomains() {
        String domainsList = Utils.loadDomains(MainActivity.this);
        domainsList = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getString(SettingsActivity.PREF_DOMAIN_LIST, domainsList);
        int repeatCount = PreferenceManager.getDefaultSharedPreferences(this).getInt(SettingsActivity.PREF_REPEAT_COUNT, 5);

        if (domainsList == null) {
            return null;
        }

        String[] domains = domainsList.split("\\n");
        List<String> testDomains = new ArrayList<>();
        for (String domainName : domains) {
            domainName = domainName.trim();

            if (!domainName.isEmpty()) {
                if (!domainName.toLowerCase().startsWith("http://") && !domainName.toLowerCase().startsWith("https://")) {
                    domainName = "http://" + domainName;
                }

                for (int i = 0; i < repeatCount; i++) {
                    testDomains.add(domainName);
                }
            }
        }

        return testDomains;
    }

    /**
     * Does the actual testing: loads websites in a webview
     */
    private void loadSitesInWebView() {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }

        try {
            List<String> testDomains = getTestDomains();

            ProgressRunnable progressRunnable = new ProgressRunnable();
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(0);
                }
            });

            int curLine = 0;
            int maxLine = testDomains.size();
            for (String domainName : testDomains) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                loadDomain(domainName);

                curLine++;
                progressRunnable.setProgress(curLine * 1000 / maxLine);
                MainActivity.this.runOnUiThread(progressRunnable);
            }

            // Waiting for Runnable to finish it's work
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Log.d(TAG, "Thread was interrupted: " + e.getMessage());
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    /**
     * Loads domain in a web view
     *
     * @param domainName Domain to load
     * @throws InterruptedException
     */
    private void loadDomain(final String domainName) throws InterruptedException {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                webView.loadUrl(domainName);
            }
        });

        final int MIN_DOMAIN_LOAD_TIME = 10 * 1000; // spend minimum 10 seconds on a website
        final int MAX_DOMAIN_LOAD_TIME = 60 * 1000; // 30 seconds limit (just in case)
        long startTime = System.currentTimeMillis();
        Pair<Long, Long> startData = Utils.readDataUsage();

        // Give it some time to start loading
        long elapsed = 0;
        while ((!isPageLoaded() || elapsed < MIN_DOMAIN_LOAD_TIME) &&
                elapsed < MAX_DOMAIN_LOAD_TIME) {
            Thread.sleep(500);
            elapsed = System.currentTimeMillis() - startTime;
        }

        Pair<Long, Long> endData = Utils.readDataUsage();
        String pageSize = Utils.formatTrafficWithDecimal(this, endData.first + endData.second - startData.first - startData.second);

        //noinspection StringBufferReplaceableByString
        StringBuilder sb = new StringBuilder();
        sb.append("Finished loading of ");
        sb.append(domainName);
        sb.append(" (");
        sb.append(webViewClient.pageTitle);
        sb.append(")\n");
        // Page code load time
        sb.append("Page load time (onPageFinished): ");
        sb.append((webViewClient.pageLoadFinishTime - webViewClient.pageLoadStartTime));
        sb.append(" ms\n");
        // Page code load time
        sb.append("Page load time (document.readyState): ");
        sb.append((System.currentTimeMillis() - webViewClient.pageLoadStartTime));
        sb.append(" ms\n");
        // Page code load time
        sb.append("Page size: ");
        sb.append(pageSize);
        sb.append(" \n");
        // Requests count
        sb.append("Requests count: ");
        sb.append(webViewClient.pageRequestsCount);
        sb.append("\n");
        // Requests blocked
        sb.append("Requests blocked: ");
        sb.append(webViewClient.pageRequestsBlocked);

        Log.d(TAG, sb.toString());
    }

    @Override
    public void run() {
        startBatteryPercent = batteryInfoReceiver.getBatteryPercent();
        startTemperature = batteryInfoReceiver.getTemperature();
        startVoltage = batteryInfoReceiver.getVoltage();

        Pair<Long, Long> startData = Utils.readDataUsage();
        long startCpuTime = Utils.readCpuTime();

        // Do the test
        loadSitesInWebView();

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Test results");
        final String message = createResultMessage(startData, startCpuTime);
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

    private String createResultMessage(Pair<Long, Long> startData, long startCpuTime) {
        Pair<Long, Long> endData = Utils.readDataUsage();
        long bytesReceived = endData.first - startData.first;
        long bytesSent = endData.second - startData.second;
        long cpuTime = Utils.readCpuTime() - startCpuTime;
        String bytesReceivedStr = Utils.formatTrafficWithDecimal(MainActivity.this, bytesReceived);
        String bytesSentStr = Utils.formatTrafficWithDecimal(MainActivity.this, bytesSent);
        String dataUsage = Utils.formatTrafficWithDecimal(MainActivity.this, bytesSent + bytesReceived);

        //noinspection StringBufferReplaceableByString
        StringBuilder sb = new StringBuilder();
        sb.append("## CPU stats\n");
        sb.append(String.format("Cpu time: %d (USER_HZ)\n", cpuTime));
        sb.append("\n");
        sb.append("## Data usage\n");
        sb.append(String.format("Sent: %s\n", bytesSentStr));
        sb.append(String.format("Received: %s\n", bytesReceivedStr));
        sb.append(String.format("Overall: %s\n", dataUsage));

        sb.append("\n");
        sb.append("## Ad blocking\n");

        sb.append(String.format("Web requests attempts: %d\n", webViewClient.requestsCount));
        sb.append(String.format("Web requests processed: %d\n", webViewClient.requestsCount - webViewClient.blockedCount));
        sb.append(String.format("Web requests blocked: %d (%d%%)\n", webViewClient.blockedCount, webViewClient.blockedCount * 100 / webViewClient.requestsCount));

        sb.append("\n");
        sb.append("## Privacy\n");

        sb.append(String.format("Third-party requests attempts: %d\n", webViewClient.thirdPartyRequestsCount));
        sb.append(String.format("Third-party requests processed: %d\n", webViewClient.thirdPartyRequestsCount - webViewClient.blockedThirdPartyRequestsCount));
        sb.append(String.format("Third-party requests blocked: %d (%d%%)\n", webViewClient.blockedThirdPartyRequestsCount, webViewClient.blockedThirdPartyRequestsCount * 100 / webViewClient.thirdPartyRequestsCount));

        return sb.toString();
    }

    /**
     * Checks if webview has finished loading the page
     *
     * @return true if document.readyState is "complete"
     */
    private boolean isPageLoaded() {
        final String[] result = new String[1];
        final Object waitLock = new Object();

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (waitLock) {

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    webView.evaluateJavascript("(function() { return document.readyState; })();", new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String value) {
                            result[0] = value;
                            synchronized (waitLock) {
                                waitLock.notifyAll();
                            }
                        }
                    });
                }
            });

            try {
                waitLock.wait();
            } catch (InterruptedException e) {
                // Ignore
            }
        }

        return result.length == 1 &&
                result[0] != null &&
                result[0].contains("complete") &&
                webViewClient.pageLoadFinishTime > 0;
    }

    private class MyWebViewClient extends WebViewClient {

        // Per-page statistics
        private long pageLoadStartTime = 0;
        private long pageLoadFinishTime = 0;
        private int pageRequestsCount = 0;
        private int pageRequestsBlocked = 0;
        private String pageTitle;
        private String pageUrl;

        // Total statistics
        private int requestsCount = 0;
        private int blockedCount = 0;
        private int thirdPartyRequestsCount = 0;
        private int blockedThirdPartyRequestsCount = 0;

        /**
         * Resets global stats
         */
        void reset() {
            requestsCount = 0;
            blockedCount = 0;
            thirdPartyRequestsCount = 0;
            blockedThirdPartyRequestsCount = 0;
            resetPage();
        }

        /**
         * Resets page stats
         */
        void resetPage() {
            pageLoadStartTime = System.currentTimeMillis();
            pageLoadFinishTime = 0;
            pageRequestsCount = 0;
            pageRequestsBlocked = 0;
            pageTitle = null;
            pageUrl = null;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Log.d(TAG, "shouldOverrideUrlLoading() for " + url);
            return false;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            Log.d(TAG, "onPageStarted() for " + url);
            resetPage();
            pageUrl = url;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            Log.d(TAG, "onPageFinished() for " + url);
            pageTitle = view.getTitle();
            pageLoadFinishTime = System.currentTimeMillis();
        }

        private boolean isValidWebRequest(String url) {
            return !url.startsWith("data:") &&
                    !url.startsWith("blob:") &&
                    // `injections.adguard.com` is a virtual domain used for loading cosmetic filters
                    // actually, there is no real web request done
                    !url.contains("injections.adguard.com");
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {

            String url = request.getUrl().toString();
            Log.d(TAG, "shouldInterceptRequest() for " + url);

            if (isValidWebRequest(url)) {
                requestsCount++;
                pageRequestsCount++;

                if (Utils.isThirdPartyRequest(url, pageUrl)) {
                    thirdPartyRequestsCount++;
                }
            }
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            Log.d(TAG, "onReceivedError() for " + request.getUrl());
            blockedCount++;
            pageRequestsBlocked++;

            if (Utils.isThirdPartyRequest(request.getUrl().toString(), pageUrl)) {
                blockedThirdPartyRequestsCount++;
            }

            super.onReceivedError(view, request, error);
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            Log.d(TAG, "onReceivedHttpError() for " + request.getUrl());
            blockedCount++;
            pageRequestsBlocked++;

            if (Utils.isThirdPartyRequest(request.getUrl().toString(), pageUrl)) {
                blockedThirdPartyRequestsCount++;
            }

            super.onReceivedHttpError(view, request, errorResponse);
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            Log.d(TAG, "onReceivedSslError() for " + error.getUrl());
            blockedCount++;
            pageRequestsBlocked++;

            if (Utils.isThirdPartyRequest(error.getUrl(), pageUrl)) {
                blockedThirdPartyRequestsCount++;
            }

            super.onReceivedSslError(view, handler, error);
        }
    }

    private class ProgressRunnable implements Runnable {
        int progress = 0;

        void setProgress(int progress) {
            this.progress = progress;
        }

        @Override
        public void run() {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.clearHistory();
            clearCookies();
            progressBar.setProgress(progress);
            webView.clearSslPreferences();
        }
    }
}
