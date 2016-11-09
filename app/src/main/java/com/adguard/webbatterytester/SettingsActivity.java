package com.adguard.webbatterytester;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

public class SettingsActivity extends AppCompatActivity {

    public static final String PREF_REPEAT_COUNT = "pref_repeat_count";
    public static final String PREF_DOMAIN_LIST = "pref_domain_list";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // As we're using a Toolbar, we should retrieve it and set it to be our ActionBar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        View group = findViewById(R.id.reset_settings);
        ((TextView)group.findViewById(R.id.title)).setText(R.string.pref_title_reset_settings);
        ((TextView)group.findViewById(R.id.summary)).setText(R.string.pref_summary_reset_settings);
        group.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String dataPath = getApplicationContext().getFilesDir().getAbsolutePath();
                Utils.copyFileFromAssets(getApplicationContext(), "test.txt", dataPath + "/test.txt");
                SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this).edit();
                final String domains = Utils.loadDomains(SettingsActivity.this);
                editor.putString(PREF_DOMAIN_LIST, domains);
                editor.putInt(PREF_REPEAT_COUNT, 5);
                editor.commit();
            }
        });

        group = findViewById(R.id.domain_settings);
        ((TextView)group.findViewById(R.id.title)).setText(R.string.pref_title_domain_settings);
        ((TextView)group.findViewById(R.id.summary)).setText(R.string.pref_summary_domain_settings);
        group.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String domains = Utils.loadDomains(SettingsActivity.this);
                final String prefDomains = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this).getString(PREF_DOMAIN_LIST, domains);
                showDomainsDialog(prefDomains, getString(R.string.dialog_title_domain_list));
            }
        });

        group = findViewById(R.id.repeat_settings);
        ((TextView)group.findViewById(R.id.title)).setText(R.string.pref_title_repeat_settings);
        ((TextView)group.findViewById(R.id.summary)).setText(R.string.pref_summary_repeat_settings);
        group.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final int repeatCount = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this).getInt(PREF_REPEAT_COUNT, 5);
                showRepeatDialog(repeatCount, getString(R.string.dialog_title_repeat_count));
            }
        });

    }

    private void showDomainsDialog(final String domains, String title) {
        final LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View dialogLayout = inflater.inflate(R.layout.edit_text_dialog, null);
        final EditText view = (EditText) dialogLayout.findViewById(R.id.textView);
        view.setText(domains);

        new AlertDialog.Builder(this, R.style.AlertDialog)
                .setTitle(title)
                .setView(dialogLayout)
                .setCancelable(true)
                .setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        final String text = view.getText().toString();
                        if (!domains.equals(text)) {
                            saveDomainsResult(text);
                        }
                        view.getText().clear();
                    }
                })
                .setNegativeButton(getString(android.R.string.cancel), null)
                .show();
    }

    private void saveDomainsResult(String text) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString(PREF_DOMAIN_LIST, text);
        editor.commit();
    }

    private void showRepeatDialog(final int startValue, String title) {
        final LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View dialogLayout = inflater.inflate(R.layout.edit_number_dialog, null);
        final EditText view = (EditText) dialogLayout.findViewById(R.id.textView);
        view.setText(Integer.toString(startValue));
        view.selectAll();

        new AlertDialog.Builder(this, R.style.AlertDialog)
                .setTitle(title)
                .setView(dialogLayout)
                .setCancelable(true)
                .setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        final String newText = view.getText().toString();
                        int newValue = Integer.parseInt(newText);
                        if (newValue != startValue) {
                            saveRepeatResult(newValue);
                        }
                        view.getText().clear();
                    }
                })
                .setNegativeButton(getString(android.R.string.cancel), null)
                .show();
    }

    private void saveRepeatResult(int newValue) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putInt(PREF_REPEAT_COUNT, newValue);
        editor.commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }
}
