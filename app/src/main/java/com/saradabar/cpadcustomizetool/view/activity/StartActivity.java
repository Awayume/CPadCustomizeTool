package com.saradabar.cpadcustomizetool.view.activity;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceFragmentCompat;

import com.saradabar.cpadcustomizetool.MainActivity;
import com.saradabar.cpadcustomizetool.R;
import com.saradabar.cpadcustomizetool.data.event.DownloadEventListener;
import com.saradabar.cpadcustomizetool.data.event.InstallEventListener;
import com.saradabar.cpadcustomizetool.data.handler.ByteProgressHandler;
import com.saradabar.cpadcustomizetool.data.service.KeepService;
import com.saradabar.cpadcustomizetool.util.Constants;
import com.saradabar.cpadcustomizetool.util.Preferences;
import com.saradabar.cpadcustomizetool.view.flagment.AppSettingsFragment;
import com.saradabar.cpadcustomizetool.view.flagment.DeviceOwnerFragment;
import com.saradabar.cpadcustomizetool.view.flagment.MainFragment;
import com.saradabar.cpadcustomizetool.view.views.AppListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.zeroturnaround.zip.commons.FileUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

import jp.co.benesse.dcha.dchaservice.IDchaService;

public class StartActivity extends AppCompatActivity implements InstallEventListener, DownloadEventListener {

    static StartActivity instance = null;

    IDchaService mDchaService;
    ProgressDialog loadingDialog;
    Menu menu;

    public static StartActivity getInstance() {
        return instance;
    }

    /* 設定画面表示 */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        instance = this;
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);

        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(false);

        if (getIntent().getBooleanExtra("result", false)) {
            setContentView(R.layout.activity_main);
            transitionFragment(new MainFragment(), false);
            return;
        }

        if (dpm.isDeviceOwnerApp(getPackageName())) {
            setContentView(R.layout.activity_main_error_enable_own);

            findViewById(R.id.main_error_button_1).setOnClickListener(view -> new AlertDialog.Builder(view.getContext())
                    .setTitle(R.string.dialog_question_device_owner)
                    .setCancelable(false)
                    .setPositiveButton(R.string.dialog_common_yes, (dialog, which) -> {
                        dpm.clearDeviceOwnerApp(getPackageName());
                        finish();
                        overridePendingTransition(0, 0);
                        startActivity(getIntent().addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION).putExtra("result", false));
                    })
                    .setNegativeButton(R.string.dialog_common_no, null)
                    .show());

            findViewById(R.id.main_error_button_2).setOnClickListener(view -> {
                try {
                    startActivity(new Intent(view.getContext(), UninstallBlockActivity.class).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION));
                } catch (ActivityNotFoundException ignored) {
                }
            });

            findViewById(R.id.main_error_button_3).setOnClickListener(this::resetConf);
        } else {
            setContentView(R.layout.activity_main_error_disable_own);
            findViewById(R.id.main_error_button_4).setOnClickListener(this::resetConf);
        }
    }

    private void resetConf(View view) {
        new AlertDialog.Builder(view.getContext())
                .setTitle(R.string.dialog_question_are_you_sure)
                .setCancelable(false)
                .setPositiveButton(R.string.dialog_common_yes, (dialog, which) -> {
                    /* 全サービスの停止 */
                    SharedPreferences sp1 = getSharedPreferences(Constants.SHARED_PREFERENCE_KEY, Context.MODE_PRIVATE);
                    SharedPreferences.Editor spe = sp1.edit();
                    spe.putBoolean(Constants.KEY_ENABLED_KEEP_SERVICE, false);
                    spe.putBoolean(Constants.KEY_ENABLED_KEEP_DCHA_STATE, false);
                    spe.putBoolean(Constants.KEY_ENABLED_KEEP_MARKET_APP_SERVICE, false);
                    spe.putBoolean(Constants.KEY_ENABLED_KEEP_USB_DEBUG, false);
                    spe.putBoolean(Constants.KEY_ENABLED_KEEP_HOME, false);
                    spe.apply();
                    ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                    for (ActivityManager.RunningServiceInfo serviceInfo : Objects.requireNonNull(manager).getRunningServices(Integer.MAX_VALUE)) {
                        if (KeepService.class.getName().equals(serviceInfo.service.getClassName())) {
                            KeepService.getInstance().stopService(6);
                        }
                    }
                    stopService(Constants.KEEP_SERVICE);
                    stopService(Constants.PROTECT_KEEP_SERVICE);
                    /* 設定リセット */
                    Preferences.SET_UPDATE_FLAG(true, this);
                    Preferences.SET_DCHASERVICE_FLAG(false, this);
                    ContentResolver resolver = getContentResolver();
                    Settings.System.putInt(resolver, Constants.DCHA_STATE, 0);
                    Settings.System.putInt(resolver, Constants.HIDE_NAVIGATION_BAR, 0);
                })
                .setNegativeButton(R.string.dialog_common_no, null)
                .show();
    }

    /* メニュー表示 */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.menu = menu;

        if (getIntent().getBooleanExtra("result", false)) {
            getMenuInflater().inflate(R.menu.main, menu);
        } else {
            getMenuInflater().inflate(R.menu.sub, menu);
        }

        return true;
    }

    /* メニュー選択 */
    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.app_info_1:
                startActivity(new Intent(this, AppInfoActivity.class).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION).putExtra("result", getIntent().getBooleanExtra("result", false)));
                return true;
            case R.id.app_info_2:
                startActivity(new Intent(this, SelfUpdateActivity.class).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION));
                return true;
            case R.id.app_info_3:
                menu.findItem(R.id.app_info_3).setVisible(false);
                nullTransitionFragment(new AppSettingsFragment());
                return true;
            case android.R.id.home:
                menu.findItem(R.id.app_info_3).setVisible(true);
                getFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                transitionFragment(new MainFragment(), false);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (getIntent().getBooleanExtra("result", false)) {
            menu.findItem(R.id.app_info_3).setVisible(true);
            getFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            transitionFragment(new MainFragment(), false);
        }
    }

    public void transitionFragment(PreferenceFragmentCompat preferenceFragmentCompat, boolean enabled) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.layout_main, preferenceFragmentCompat)
                .commit();

        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(enabled);
    }

    private void nullTransitionFragment(PreferenceFragmentCompat nextPreferenceFragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .addToBackStack(null)
                .replace(R.id.layout_main, nextPreferenceFragment)
                .commit();

        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    ServiceConnection mDchaServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mDchaService = IDchaService.Stub.asInterface(iBinder);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
        }
    };

    public boolean isBindDchaService() {
        return bindService(Constants.DCHA_SERVICE, mDchaServiceConnection, Context.BIND_AUTO_CREATE);
    }

    public DeviceOwnerFragment.TryApkMTask.Listener apkMListener() {
        return new DeviceOwnerFragment.TryApkMTask.Listener() {
            AlertDialog alertDialog;

            @SuppressLint("SetTextI18n")
            @Override
            public void onShow() {
                View view = getLayoutInflater().inflate(R.layout.view_progress, null);
                ProgressBar progressBar = view.findViewById(R.id.progress);
                progressBar.setProgress(0);
                TextView textPercent = view.findViewById(R.id.progress_percent);
                TextView textByte = view.findViewById(R.id.progress_byte);
                textPercent.setText(progressBar.getProgress() + getString(R.string.percent));
                alertDialog = new AlertDialog.Builder(StartActivity.this)
                        .setView(view)
                        .setMessage("")
                        .setCancelable(false)
                        .create();

                if (!alertDialog.isShowing()) alertDialog.show();

                ByteProgressHandler progressHandler = new ByteProgressHandler(1);
                progressHandler.progressBar = progressBar;
                progressHandler.textPercent = textPercent;
                progressHandler.textByte = textByte;
                progressHandler.tryApkMTask = new DeviceOwnerFragment.TryApkMTask();
                progressHandler.sendEmptyMessage(0);
            }

            @Override
            public void onSuccess() {
                alertDialog.dismiss();

                new DeviceOwnerFragment.TryApkMTask().cancel(true);
                DeviceOwnerFragment.TryApkTask tryApkTask = new DeviceOwnerFragment.TryApkTask();
                tryApkTask.setListener(apkListener());
                tryApkTask.execute();
            }

            @Override
            public void onFailure() {
                alertDialog.dismiss();

                try {
                    /* 一時ファイルを消去 */
                    FileUtils.deleteDirectory(StartActivity.this.getExternalCacheDir());
                } catch (IOException ignored) {
                }

                new DeviceOwnerFragment.TryApkMTask().cancel(true);

                new AlertDialog.Builder(StartActivity.this)
                        .setMessage(getString(R.string.dialog_info_failure))
                        .setCancelable(false)
                        .setPositiveButton(R.string.dialog_common_ok, (dialog, which) -> dialog.dismiss())
                        .show();
            }

            @Override
            public void onError(String str) {
                alertDialog.dismiss();

                try {
                    /* 一時ファイルを消去 */
                    FileUtils.deleteDirectory(StartActivity.this.getExternalCacheDir());
                } catch (IOException ignored) {
                }

                new DeviceOwnerFragment.TryApkMTask().cancel(true);

                new AlertDialog.Builder(StartActivity.this)
                        .setMessage(getString(R.string.dialog_error) + "\n" + str)
                        .setCancelable(false)
                        .setPositiveButton(R.string.dialog_common_ok, (dialog, which) -> dialog.dismiss())
                        .show();
            }

            @Override
            public void onProgressUpdate(String str) {
                alertDialog.setMessage(str);
            }
        };
    }

    public DeviceOwnerFragment.TryXApkTask.Listener xApkListener() {
        return new DeviceOwnerFragment.TryXApkTask.Listener() {
            AlertDialog alertDialog;

            @SuppressLint("SetTextI18n")
            @Override
            public void onShow() {
                View view = getLayoutInflater().inflate(R.layout.view_progress, null);
                ProgressBar progressBar = view.findViewById(R.id.progress);
                progressBar.setProgress(0);
                TextView textPercent = view.findViewById(R.id.progress_percent);
                TextView textByte = view.findViewById(R.id.progress_byte);
                textPercent.setText(progressBar.getProgress() + getString(R.string.percent));
                alertDialog = new AlertDialog.Builder(StartActivity.this)
                        .setView(view)
                        .setMessage("")
                        .setCancelable(false)
                        .create();

                if (!alertDialog.isShowing()) alertDialog.show();

                ByteProgressHandler progressHandler = new ByteProgressHandler(0);
                progressHandler.progressBar = progressBar;
                progressHandler.textPercent = textPercent;
                progressHandler.textByte = textByte;
                progressHandler.tryXApkTask = new DeviceOwnerFragment.TryXApkTask();
                progressHandler.sendEmptyMessage(0);
            }

            @Override
            public void onSuccess() {
                alertDialog.dismiss();

                new DeviceOwnerFragment.TryXApkTask().cancel(true);
                DeviceOwnerFragment.TryApkTask tryApkTask = new DeviceOwnerFragment.TryApkTask();
                tryApkTask.setListener(apkListener());
                tryApkTask.execute();
            }

            @Override
            public void onFailure() {
                alertDialog.dismiss();

                try {
                    /* 一時ファイルを消去 */
                    FileUtils.deleteDirectory(StartActivity.this.getExternalCacheDir());
                } catch (IOException ignored) {
                }

                new DeviceOwnerFragment.TryXApkTask().cancel(true);

                new AlertDialog.Builder(StartActivity.this)
                        .setMessage(getString(R.string.dialog_info_failure))
                        .setCancelable(false)
                        .setPositiveButton(R.string.dialog_common_ok, (dialog, which) -> dialog.dismiss())
                        .show();
            }

            @Override
            public void onError(String str) {
                alertDialog.dismiss();

                try {
                    /* 一時ファイルを消去 */
                    FileUtils.deleteDirectory(StartActivity.this.getExternalCacheDir());
                } catch (IOException ignored) {
                }

                new DeviceOwnerFragment.TryXApkTask().cancel(true);

                new AlertDialog.Builder(StartActivity.this)
                        .setMessage(getString(R.string.dialog_error) + "\n" + str)
                        .setCancelable(false)
                        .setPositiveButton(R.string.dialog_common_ok, (dialog, which) -> dialog.dismiss())
                        .show();
            }

            @Override
            public void onProgressUpdate(String str) {
                alertDialog.setMessage(str);
            }
        };
    }

    public DeviceOwnerFragment.TryApkTask.Listener apkListener() {
        return new DeviceOwnerFragment.TryApkTask.Listener() {
            ProgressDialog progressDialog;

            /* プログレスバーの表示 */
            @Override
            public void onShow() {
                progressDialog = new ProgressDialog(StartActivity.this);
                progressDialog.setTitle("");
                progressDialog.setMessage(getString(R.string.progress_state_installing));
                progressDialog.setCancelable(false);
                progressDialog.show();
            }

            /* 成功 */
            @Override
            public void onSuccess() {
                progressDialog.dismiss();

                try {
                    /* 一時ファイルを消去 */
                    FileUtils.deleteDirectory(StartActivity.this.getExternalCacheDir());
                } catch (IOException ignored) {
                }

                new DeviceOwnerFragment.TryApkTask().cancel(true);

                AlertDialog alertDialog = new AlertDialog.Builder(StartActivity.this)
                        .setMessage(R.string.dialog_info_success_silent_install)
                        .setCancelable(false)
                        .setPositiveButton(R.string.dialog_common_ok, (dialog, which) -> dialog.dismiss())
                        .create();

                if (!alertDialog.isShowing()) {
                    alertDialog.show();
                }
            }

            /* 失敗 */
            @Override
            public void onFailure(String str) {
                progressDialog.dismiss();

                try {
                    /* 一時ファイルを消去 */
                    FileUtils.deleteDirectory(StartActivity.this.getExternalCacheDir());
                } catch (IOException ignored) {
                }

                new DeviceOwnerFragment.TryApkTask().cancel(true);

                new AlertDialog.Builder(StartActivity.this)
                        .setMessage(getString(R.string.dialog_info_failure_silent_install) + "\n" + str)
                        .setCancelable(false)
                        .setPositiveButton(R.string.dialog_common_ok, (dialog, which) -> dialog.dismiss())
                        .show();
            }

            @Override
            public void onError(String str) {
                progressDialog.dismiss();

                try {
                    /* 一時ファイルを消去 */
                    FileUtils.deleteDirectory(StartActivity.this.getExternalCacheDir());
                } catch (IOException ignored) {
                }

                new DeviceOwnerFragment.TryApkTask().cancel(true);

                new AlertDialog.Builder(StartActivity.this)
                        .setMessage(getString(R.string.dialog_error) + "\n" + str)
                        .setCancelable(false)
                        .setPositiveButton(R.string.dialog_common_ok, (dialog, which) -> dialog.dismiss())
                        .show();
            }
        };
    }

    public MainFragment.installTask.Listener installListener() {
        return new MainFragment.installTask.Listener() {
            ProgressDialog progressDialog;

            /* プログレスバーの表示 */
            @Override
            public void onShow() {
                progressDialog = new ProgressDialog(StartActivity.this);
                progressDialog.setTitle("");
                progressDialog.setMessage(getString(R.string.progress_state_installing));
                progressDialog.setCancelable(false);
                progressDialog.show();
            }

            /* 成功 */
            @Override
            public void onSuccess() {
                progressDialog.dismiss();

                new AlertDialog.Builder(StartActivity.this)
                        .setMessage(R.string.dialog_info_success_silent_install)
                        .setCancelable(false)
                        .setPositiveButton(R.string.dialog_common_ok, (dialog, which) -> dialog.dismiss())
                        .show();
            }

            /* 失敗 */
            @Override
            public void onFailure() {
                progressDialog.dismiss();

                new AlertDialog.Builder(StartActivity.this)
                        .setMessage(R.string.dialog_info_failure_silent_install)
                        .setCancelable(false)
                        .setPositiveButton(R.string.dialog_common_ok, (dialog, which) -> dialog.dismiss())
                        .show();
            }
        };
    }

    public MainFragment.resolutionTask.Listener resolutionListener() {
        return new MainFragment.resolutionTask.Listener() {
            Handler mHandler;
            Runnable mRunnable;
            /* 成功 */
            @Override
            public void onSuccess() {
                /* 設定変更カウントダウンダイアログ表示 */
                AlertDialog alertDialog = new AlertDialog.Builder(StartActivity.this)
                        .setTitle(R.string.dialog_title_resolution)
                        .setCancelable(false)
                        .setMessage("")
                        .setPositiveButton(R.string.dialog_common_yes, (dialog, which) -> {
                            dialog.dismiss();
                            mHandler.removeCallbacks(mRunnable);
                        })
                        .setNegativeButton(R.string.dialog_common_no, (dialog, which) -> {
                            dialog.dismiss();
                            mHandler.removeCallbacks(mRunnable);
                            MainFragment.getInstance().resetResolution();
                        })
                        .create();

                if (!alertDialog.isShowing()) alertDialog.show();

                /* カウント開始 */
                mHandler = new Handler();
                mRunnable = new Runnable() {
                    int i = 10;
                    @Override
                    public void run() {
                        alertDialog.setMessage("変更を適用しますか？\n" + i + "秒後に元の設定に戻ります");
                        mHandler.postDelayed(this, 1000);

                        if (i <= 0) {
                            alertDialog.dismiss();
                            mHandler.removeCallbacks(this);
                            MainFragment.getInstance().resetResolution();
                        }

                        i--;
                    }
                };
                mHandler.post(mRunnable);
            }

            /* 失敗 */
            @Override
            public void onFailure() {
                new AlertDialog.Builder(StartActivity.this)
                        .setMessage(getString(R.string.dialog_info_failure))
                        .setPositiveButton(R.string.dialog_common_ok, (dialog, which) -> dialog.dismiss())
                        .show();
            }
        };
    }

    /* 再表示 */
    @Override
    public void onResume() {
        super.onResume();

        /* DchaServiceの使用可否を確認 */
        if (Preferences.GET_DCHASERVICE_FLAG(this)) {
            //DchaServiceが機能していないなら終了
            if (!isBindDchaService()) {
                startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION));
                finish();
            }
        }
    }

    @Override
    public void onInstallSuccess() {
        DeviceOwnerFragment.TryApkTask.mListener.onSuccess();
    }

    @Override
    public void onInstallFailure(String str) {
        DeviceOwnerFragment.TryApkTask.mListener.onFailure(str);
    }

    @Override
    public void onInstallError(String str) {
        DeviceOwnerFragment.TryApkTask.mListener.onError(str);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mDchaService != null) unbindService(mDchaServiceConnection);
    }

    @Override
    public void onDownloadComplete(int reqCode) {
        switch (reqCode) {
            case Constants.REQUEST_DOWNLOAD_APP_CHECK:
                ArrayList<AppListView.AppData> list = new ArrayList<>();

                try {
                    JSONObject jsonObj1 = MainFragment.getInstance().parseJson();
                    JSONObject jsonObj2 = jsonObj1.getJSONObject("ct");
                    JSONArray jsonArray = jsonObj2.getJSONArray("appList");

                    for (int i = 0; i < jsonArray.length(); i++) {
                        AppListView.AppData data = new AppListView.AppData();
                        data.str = jsonArray.getJSONObject(i).getString("name");
                        list.add(data);
                    }
                } catch (JSONException | IOException ignored) {
                }

                View v = getLayoutInflater().inflate(R.layout.layout_app_list, null);
                ListView lv = v.findViewById(R.id.app_list);

                lv.setAdapter(new AppListView.AppListAdapter(this, list));
                lv.setOnItemClickListener((parent, view, position, id) -> {
                    CheckBox checkBox = lv.getChildAt(position).findViewById(R.id.v_app_list_check);
                    checkBox.setChecked(!checkBox.isChecked());
                });

                cancelLdDialog();

                new AlertDialog.Builder(this)
                        .setView(v)
                        .setTitle("アプリを選択してください")
                        .setPositiveButton(R.string.dialog_common_ok, (dialog, which) -> {
                            StringBuilder str = new StringBuilder();
                            for (int i = 0; i <lv.getCount(); i++) {
                                CheckBox checkBox = lv.getChildAt(i).findViewById(R.id.v_app_list_check);
                                if (checkBox.isChecked()) {
                                    try {
                                        JSONObject jsonObj1 = MainFragment.getInstance().parseJson();
                                        JSONObject jsonObj2 = jsonObj1.getJSONObject("ct");
                                        JSONArray jsonArray = jsonObj2.getJSONArray("appList");
                                        str.append(jsonArray.getJSONObject(i).getString("name")).append("\n").append(jsonArray.getJSONObject(i).getString("url")).append("\n");
                                    } catch (JSONException | IOException ignored) {
                                    }
                                }
                            }
                            if (str.toString().equals("")) {
                                str = new StringBuilder("選択されていません");
                            }
                            new AlertDialog.Builder(this)
                                    .setMessage(str.toString())
                                    .setPositiveButton(R.string.dialog_common_ok, (dialog2, which2) -> dialog.dismiss())
                                    .show();
                        })
                        .show();
                break;
            case Constants.REQUEST_DOWNLOAD_APK:
                break;
            default:
                break;
        }
    }

    @Override
    public void onDownloadError() {
        new AlertDialog.Builder(this)
                .setMessage("ダウンロードに失敗しました\nネットワークが安定しているか確認してください")
                .setPositiveButton(R.string.dialog_common_ok, (dialog, which) -> dialog.dismiss())
                .show();
    }

    @Override
    public void onConnectionError() {
        new AlertDialog.Builder(this)
                .setMessage("データ取得に失敗しました\nネットワークを確認してください")
                .setPositiveButton(R.string.dialog_common_ok, (dialog, which) -> dialog.dismiss())
                .show();
    }

    public void showLdDialog() {
        loadingDialog = ProgressDialog.show(this, "", getString(R.string.progress_state_connecting), true);
        loadingDialog.show();
    }

    public void cancelLdDialog() {
        try {
            if (loadingDialog != null) loadingDialog.dismiss();
        } catch (Exception ignored) {
        }
    }
}