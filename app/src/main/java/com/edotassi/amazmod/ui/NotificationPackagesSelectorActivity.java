package com.edotassi.amazmod.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

import com.edotassi.amazmod.R;
import com.edotassi.amazmod.adapters.AppInfoAdapter;
import com.edotassi.amazmod.db.model.NotificationPreferencesEntity;
import com.edotassi.amazmod.support.AppInfo;
import com.edotassi.amazmod.support.SilenceApplicationHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.Flowable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import me.zhanghai.android.materialprogressbar.MaterialProgressBar;

public class NotificationPackagesSelectorActivity extends AppCompatActivity implements AppInfoAdapter.Bridge {

    @BindView(R.id.activity_notification_packages_selector_list)
    ListView listView;
    @BindView(R.id.activity_notification_packages_selector_progress)
    MaterialProgressBar materialProgressBar;

    private List<AppInfo> appInfoList;
    private AppInfoAdapter appInfoAdapter;

    private boolean selectedAll;

    @SuppressLint("CheckResult")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_packages_selector);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.installed_apps);

        ButterKnife.bind(this);

        appInfoAdapter = new AppInfoAdapter(this, R.layout.row_appinfo, new ArrayList<AppInfo>());
        listView.setAdapter(appInfoAdapter);

        loadApps(false);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_notification_packages_selector, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_activity_notification_packages_selector_show_system) {
            item.setChecked(!item.isChecked());
            loadApps(item.isChecked());
            return true;
        }

        if (id == R.id.action_activity_notification_packges_selector_toggle_all) {
            if (appInfoList != null) {
                long count = 0;
                for (AppInfo appInfoCheckforAll : appInfoList) {
                    if (appInfoCheckforAll.isEnabled()) count++;
                }
                if (appInfoList.size() <= count) {
                    selectedAll = true;
                }
                for (AppInfo appInfo : appInfoList) {
                    appInfo.setEnabled(!selectedAll);
                }
                selectedAll = !selectedAll;
                sortAppInfo(appInfoList);
                appInfoAdapter.clear();
                appInfoAdapter.addAll(appInfoList);
                appInfoAdapter.notifyDataSetChanged();
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onAppInfoStatusChange() {
        sortAppInfo(appInfoList);
        appInfoAdapter.clear();
        appInfoAdapter.addAll(appInfoList);
        appInfoAdapter.notifyDataSetChanged();
    }

    @Override
    public Context getContext() {
        return this;
    }

    @SuppressLint("CheckResult")
    private void loadApps(final boolean showSystemApps) {
        materialProgressBar.setVisibility(View.VISIBLE);
        listView.setVisibility(View.GONE);

        Flowable.fromCallable(new Callable<List<AppInfo>>() {
            @Override
            public List<AppInfo> call() throws Exception {
                //List installed packages and create a list of appInfo based on them
                List<PackageInfo> packageInfoList = getPackageManager().getInstalledPackages(0);
                List<AppInfo> appInfoList = new ArrayList<>();
                Map<String, NotificationPreferencesEntity> packagesMap = SilenceApplicationHelper.listApps();
                for (PackageInfo packageInfo : packageInfoList) {
                    boolean isSystemApp = (packageInfo.applicationInfo.flags & (ApplicationInfo.FLAG_UPDATED_SYSTEM_APP | ApplicationInfo.FLAG_SYSTEM)) > 0;
                    boolean enabled = packagesMap.containsKey(packageInfo.packageName);
                    if (enabled || !isSystemApp || (showSystemApps && isSystemApp)) {
                        appInfoList.add(createAppInfo(packageInfo, enabled));
                    }
                }
                sortAppInfo(appInfoList);
                NotificationPackagesSelectorActivity.this.appInfoList = appInfoList;
                return appInfoList;
            }
        }).subscribeOn(Schedulers.computation())
                .observeOn(Schedulers.single())
                .subscribe(new Consumer<List<AppInfo>>() {
                    @Override
                    public void accept(final List<AppInfo> appInfoList) {
                        NotificationPackagesSelectorActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                //fill List with appInfoList
                                appInfoAdapter.clear();
                                appInfoAdapter.addAll(appInfoList);
                                appInfoAdapter.notifyDataSetChanged();

                                materialProgressBar.setVisibility(View.GONE);
                                listView.setVisibility(View.VISIBLE);
                            }
                        });
                    }
                });
    }

    private void sortAppInfo(List<AppInfo> appInfoList) {
        Collections.sort(appInfoList, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo o1, AppInfo o2) {
                if (o1.isEnabled() && !o2.isEnabled()) {
                    return -1;
                } else if (!o1.isEnabled() && o2.isEnabled()) {
                    return 1;
                } else if ((!o1.isEnabled() && !o2.isEnabled()) || (o1.isEnabled() && o2.isEnabled())) {
                    return o1.getAppName().compareTo(o2.getAppName());
                }
                return o1.getAppName().compareTo(o2.getAppName());
            }
        });
    }

    private AppInfo createAppInfo(PackageInfo packageInfo, boolean enabled) {
        AppInfo appInfo = new AppInfo();
        appInfo.setPackageName(packageInfo.packageName);
        appInfo.setAppName(packageInfo.applicationInfo.loadLabel(getPackageManager()).toString());
        appInfo.setVersionName(packageInfo.versionName);
        appInfo.setIcon(packageInfo.applicationInfo.loadIcon(getPackageManager()));
        appInfo.setEnabled(enabled);
        return appInfo;
    }
}
