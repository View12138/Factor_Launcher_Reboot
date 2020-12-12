package com.factor.launcher.managers;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.util.Log;
import android.view.*;
import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;
import com.factor.launcher.R;
import com.factor.launcher.database.AppListDatabase;
import com.factor.launcher.databinding.AppListItemBinding;
import com.factor.launcher.models.UserApp;
import com.factor.launcher.util.Constants;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static com.factor.launcher.util.Constants.PACKAGE_NAME;

public class AppListManager
{
    private final String TAG = "AppListManager";

    private boolean displayHidden = false;

    private final ArrayList<UserApp> userApps = new ArrayList<>();

    private final Activity activity;

    private final AppListDatabase appListDatabase;

    private final SharedPreferences factorSharedPreferences;

    private final PackageManager packageManager;

    private SharedPreferences.Editor editor;

    private FactorManager factorManager;

    public AppListAdapter adapter;

    //constructor
    public AppListManager(Activity activity, ViewGroup background)
    {
        this.activity = activity;
        packageManager = activity.getPackageManager();
        initialize(background);
        appListDatabase = Room.databaseBuilder(activity, AppListDatabase.class, "app_drawer_list").build();
        factorSharedPreferences = activity.getSharedPreferences(PACKAGE_NAME + "_FIRST_LAUNCH", Context.MODE_PRIVATE);
        loadApps(factorSharedPreferences.getBoolean("saved", false));
    }

    //initialize adapter and background view for blur
    public void initialize(ViewGroup background)
    {
        this.adapter = new AppListAdapter(userApps);
        this.factorManager = new FactorManager(activity, background, packageManager);
    }

    //compare app label (new)
    private final Comparator<UserApp> first_letter = new Comparator<UserApp>()
    {
        private final Collator sCollator = Collator.getInstance();
        @Override
        public int compare(UserApp app1, UserApp app2)
        {
            return sCollator.compare(app1.getLabelNew(), app2.getLabelNew());
        }
    };

    //return activity
    public Activity getActivity()
    {
        return this.activity;
    }

    //return factor manager
    public FactorManager getFactorManager()
    {
        return this.factorManager;
    }

    //load app drawer list
    private void loadApps(Boolean isSaved)
    {
        if (isSaved)
        {
            new Thread(() ->
            {
                try
                {
                    Intent i = new Intent(Intent.ACTION_MAIN, null);
                    i.addCategory(Intent.CATEGORY_LAUNCHER);
                    List<ResolveInfo> availableApps = packageManager.queryIntentActivities(i, 0);
                    for (ResolveInfo r : availableApps)
                    {
                        if (!r.activityInfo.packageName.equals(Constants.PACKAGE_NAME))
                        {
                            UserApp app = appListDatabase.appListDao().findByPackage(r.activityInfo.packageName);
                            //noinspection ConstantConditions
                            if (app == null) //package name does not exist in database
                            {
                                app = new UserApp();
                                app.setLabelOld((String) r.loadLabel(packageManager));
                                app.setLabelNew(app.getLabelOld());
                                app.setPackageName(r.activityInfo.packageName);
                                app.icon = r.activityInfo.loadIcon(packageManager);
                                userApps.add(app);
                                appListDatabase.appListDao().insert(app);
                            }
                            else
                            {
                                if (doesPackageExist(app) && packageManager.getApplicationInfo(app.getPackageName(), 0).enabled)
                                {
                                    app.icon = r.activityInfo.loadIcon(packageManager);
                                    userApps.add(app);
                                    app.setPinned(factorManager.isAppPinned(app));
                                    userApps.sort(first_letter);
                                }
                                else
                                {
                                    appListDatabase.appListDao().delete(app);
                                }
                            }
                        }
                    }
                    adapter.updateList();
                    activity.runOnUiThread(adapter::notifyDataSetChanged);
                }
                catch (Exception ex)
                {
                    Log.e(TAG, ex.getMessage());
                }
            }).start();
        }
        else
        {
            editor = factorSharedPreferences.edit();
            new Thread(() ->
            {
                try
                {
                    Intent i = new Intent(Intent.ACTION_MAIN, null);
                    i.addCategory(Intent.CATEGORY_LAUNCHER);
                    List<ResolveInfo> availableApps = packageManager.queryIntentActivities(i, 0);
                    for (ResolveInfo r : availableApps)
                    {
                        if (!r.activityInfo.packageName.equals(Constants.PACKAGE_NAME) && packageManager.getApplicationInfo(r.activityInfo.packageName, 0).enabled)
                        {
                            UserApp app = new UserApp();
                            app.setLabelOld((String) r.loadLabel(packageManager));
                            app.setLabelNew(app.getLabelOld());
                            app.setPackageName(r.activityInfo.packageName);
                            app.icon = r.activityInfo.loadIcon(packageManager);
                            userApps.add(app);
                        }
                    }
                    userApps.sort(first_letter);
                    appListDatabase.appListDao().insertAll(userApps);

                    adapter.updateList();
                    activity.runOnUiThread(adapter::notifyDataSetChanged);
                    editor.putBoolean("saved", true);
                    editor.apply();

                }
                catch (Exception ex)
                {
                    Log.d(TAG, ex.getMessage());
                }
            }).start();
        }
    }

    //pin & unpin
    private boolean changePin(UserApp userApp)
    {
        userApp.changePinnedState();
        if (userApp.isPinned())
            factorManager.addToHome(userApp);

        new Thread(() ->
        {
            appListDatabase.appListDao().updateAppInfo(userApp);
            adapter.updateList();
            activity.runOnUiThread(() -> adapter.notifyItemChanged(userApps.indexOf(userApp)));
        }).start();

        return userApps.contains(userApp);
    }

    //set app to hidden
    private boolean hideApp(UserApp userApp)
    {
        userApp.setHidden(true);
        new Thread(() ->
        {
            appListDatabase.appListDao().updateAppInfo(userApp);
            adapter.updateList();
            activity.runOnUiThread(() -> adapter.notifyItemChanged(userApps.indexOf(userApp)));
        }).start();

        return userApps.contains(userApp);
    }

    //set app to not hidden
    private boolean showApp(UserApp userApp)
    {
        userApp.setHidden(false);
        new Thread(() ->
        {
            appListDatabase.appListDao().updateAppInfo(userApp);
            adapter.updateList();
            activity.runOnUiThread(() -> adapter.notifyItemChanged(userApps.indexOf(userApp)));
        }).start();

        return userApps.contains(userApp);
    }

    //check if package exists
    private boolean doesPackageExist(UserApp a)
    {
        boolean result = false;
        Intent i = new Intent(Intent.ACTION_MAIN, null);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> availableApps = packageManager.queryIntentActivities(i, 0);
        for (ResolveInfo r : availableApps)
        {
            if (!r.activityInfo.packageName.equals(Constants.PACKAGE_NAME))
            {
                if (r.activityInfo.packageName.equals(a.getPackageName()))
                {
                    result = true;
                    break;
                }
            }
        }
        return result;
    }

    private UserApp findAppByPackage(String packageName)
    {
        UserApp app = new UserApp();
        for (UserApp a : userApps)
        {
            if (a.getPackageName().equals(packageName))
                app = a;
        }

        return app;
    }

    //remove app from the list only if package no longer exists
    public void removeApp(UserApp app)
    {
        if (!doesPackageExist(app))
        {
            int position = userApps.indexOf(app);
            userApps.remove(app);
            new Thread(() ->
            {

                appListDatabase.appListDao().delete(app);
                adapter.updateList();
                activity.runOnUiThread(() -> adapter.notifyItemRemoved(position));
            }).start();
            factorManager.remove(app);
        }
    }

    //add app after receiving PACKAGE_ADDED broadcast
    public void addApp(UserApp app)
    {
        if (doesPackageExist(app))
        {
            new Thread(() ->
            {
                try
                {
                    ApplicationInfo info = packageManager.getApplicationInfo(app.getPackageName(), 0);
                    app.setIcon(packageManager.getApplicationIcon(app.getPackageName()));
                    app.setLabelOld((String) packageManager.getApplicationLabel(info));
                    app.setLabelNew(app.getLabelOld());
                    appListDatabase.appListDao().insert(app);
                    userApps.add(app);
                    userApps.sort(first_letter);

                    adapter.updateList();
                    activity.runOnUiThread(() -> adapter.notifyItemInserted(userApps.indexOf(app)));
                }
                catch (PackageManager.NameNotFoundException e)
                {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    public void updateApp(UserApp app)
    {
        UserApp appToUpdate = findAppByPackage(app.getPackageName());
        if (!appToUpdate.getLabelNew().equals(app.getLabelNew()) && !appToUpdate.isCustomized())
            updateAppReorder(app);
        else
            updateAppNoReorder(app);
    }

    //called when receiving PACKAGE_ADDED broadcast with EXTRA_REPLACING set to true
    private void updateAppNoReorder(UserApp app)
    {
        UserApp appToUpdate;
        try {
            if (doesPackageExist(app) && packageManager.getApplicationInfo(app.getPackageName(), 0).enabled)
            {
                if (userApps.contains(app))
                {
                    int position = userApps.indexOf(app);
                    appToUpdate = userApps.get(position);
                    new Thread(() ->
                    {
                        try
                        {
                            ApplicationInfo info = packageManager.getApplicationInfo(appToUpdate.getPackageName(), 0);
                            userApps.get(position).setIcon(packageManager.getApplicationIcon(appToUpdate.getPackageName()));
                            userApps.get(position).setLabelOld((String) packageManager.getApplicationLabel(info));
                            appListDatabase.appListDao().updateAppInfo(appToUpdate);
                            userApps.sort(first_letter);

                            adapter.updateList();
                            activity.runOnUiThread(() -> adapter.notifyItemChanged(position));
                        }
                        catch (PackageManager.NameNotFoundException e)
                        {
                            e.printStackTrace();
                        }
                    }).start();

                    if (appToUpdate.isPinned())
                    {
                        factorManager.updateFactor(appToUpdate);
                    }

                }
                else
                {
                    addApp(app);
                }
            }
            else
            {
                if (userApps.contains(app))
                {
                    removeApp(app);
                }
            }
        }
        catch (PackageManager.NameNotFoundException e)
        {
            Log.d(TAG, e.getMessage());
        }
    }

    //same as updateAppNoReorder, but notifyDatasetChanged because the app name has changed
    //todo: better animation (remove at position, insert at new position)
    private void updateAppReorder(UserApp app)
    {
        UserApp appToUpdate;
        try {
            if (doesPackageExist(app) && packageManager.getApplicationInfo(app.getPackageName(), 0).enabled)
            {
                if (userApps.contains(app))
                {
                    int position = userApps.indexOf(app);
                    appToUpdate = userApps.get(position);
                    new Thread(() ->
                    {
                        try
                        {
                            ApplicationInfo info = packageManager.getApplicationInfo(appToUpdate.getPackageName(), 0);
                            userApps.get(position).setIcon(packageManager.getApplicationIcon(appToUpdate.getPackageName()));
                            userApps.get(position).setLabelOld((String) packageManager.getApplicationLabel(info));
                            appListDatabase.appListDao().updateAppInfo(appToUpdate);
                            userApps.sort(first_letter);

                            adapter.updateList();
                            activity.runOnUiThread(adapter::notifyDataSetChanged);
                        }
                        catch (PackageManager.NameNotFoundException e)
                        {
                            e.printStackTrace();
                        }
                    }).start();

                    if (appToUpdate.isPinned())
                    {
                        factorManager.updateFactor(appToUpdate);
                    }

                }
                else
                {
                    addApp(app);
                }
            }
            else
            {
                if (userApps.contains(app))
                {
                    removeApp(app);
                }
            }
        }
        catch (PackageManager.NameNotFoundException e)
        {
            Log.d(TAG, e.getMessage());
        }
    }

    //remove from home
    public void unPin(String packageName)
    {
        UserApp appToUnPin = new UserApp();
        for (UserApp app : userApps)
        {
            if (app.getPackageName().equals(packageName))
            {
                appToUnPin = app;
                break;
            }
        }
        if (!appToUnPin.getPackageName().isEmpty())
            changePin(appToUnPin);
    }

    //search bar filter app list
    public int findPosition(String newText)
    {
        for (UserApp app : userApps)
        {
            if (!app.isHidden() && app.getSearchReference().contains(newText.toLowerCase()))
            {
                Log.d("target", "found: " + newText + " matching " + app.getSearchReference());
                return userApps.indexOf(app);
            }
        }
        return 0;
    }

    //edit app dialog
    public void renameApp(UserApp app, String newLabel)
    {
        if (!userApps.contains(app))
            return;

        app.setCustomized(true);
        app.setLabelNew(newLabel);
        updateAppReorder(app);
    }

    public void resetAppEdit(UserApp app)
    {
        if (!userApps.contains(app)) return;
        app.setCustomized(false);
        app.setLabelNew(app.getLabelOld());
        updateAppReorder(app);
    }

    //change display mode, return a new adapter
    public AppListAdapter setDisplayHidden(boolean displayHidden)
    {
        this.displayHidden = displayHidden;
        this.adapter = new AppListAdapter(userApps);
        return adapter;
    }

    //get display mode
    public boolean isDisplayingHidden()
    {
        return this.displayHidden;
    }

    //adapter for app drawer
    public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.AppListViewHolder>
    {
        private final ArrayList<UserApp> appsShown;

        public AppListAdapter(ArrayList<UserApp> apps)
        {
            this.appsShown = new ArrayList<>(apps);
        }

        public void updateList()
        {
            this.appsShown.clear();
            this.appsShown.addAll(userApps);
        }

        @NonNull
        @Override
        public AppListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
        {
            int id;
            if (displayHidden)
                id = viewType == 1 ? R.layout.hidden_app : R.layout.app_list_item;
            else
                id = viewType == 1 ? R.layout.app_list_item : R.layout.hidden_app;



            View view = LayoutInflater.from(parent.getContext()).inflate(id, parent, false);
            return new AppListViewHolder(view);

        }

        @Override
        public void onBindViewHolder(@NonNull AppListViewHolder holder, int position)
        {
            holder.bindApp(appsShown.get(position));
        }

        @Override
        public int getItemCount()
        {
            return appsShown.size();
        }

        @Override
        public int getItemViewType(int position)
        {
            if (userApps.get(position).isHidden())
                return 0; //do not show
            else
                return 1; //show
        }



        class AppListViewHolder extends RecyclerView.ViewHolder
        {
            private final ViewDataBinding binding;
            public AppListViewHolder(@NonNull View itemView)
            {
                super(itemView);
                binding = DataBindingUtil.bind(itemView);
            }

            public void bindApp(UserApp app)
            {
                if (binding instanceof AppListItemBinding)
                {
                    AppListItemBinding appBinding = (AppListItemBinding)binding;
                    appBinding.setUserApp(app);
                    activity.registerForContextMenu(itemView);
                    itemView.setOnCreateContextMenuListener((menu, v, menuInfo) ->
                    {
                        MenuInflater inflater = activity.getMenuInflater();
                        inflater.inflate(R.menu.app_list_item_menu, menu);
                        if (app.isPinned())
                            menu.getItem(0).setEnabled(false);

                        //add to home & remove from home
                        menu.getItem(0).setOnMenuItemClickListener(item -> changePin(app));

                        //edit
                        SubMenu sub = menu.getItem(1).getSubMenu();
                        //todo: rename
                        sub.getItem(0).setOnMenuItemClickListener(item ->
                        {
                            enterEditMode(appBinding);
                            return true;
                        });
                        //hide
                        MenuItem hide = sub.getItem(1);
                        if (app.isHidden())
                        hide.setTitle("Show");
                        else hide.setTitle("Hide");
                        hide.setOnMenuItemClickListener(item -> !app.isHidden() ? hideApp(app) : showApp(app));
                        //info
                        menu.getItem(2).setOnMenuItemClickListener(item ->
                        {
                            activity.startActivity(
                                    new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:"+app.getPackageName())));
                            return true;
                        });

                        //uninstall
                        menu.getItem(3).setOnMenuItemClickListener(item ->
                        {
                            activity.startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:"+app.getPackageName()))
                                    .putExtra(Intent.EXTRA_RETURN_RESULT, true));
                            return true;
                        });
                    });

                    setOnClickListener(appBinding);
                }
            }


            private void enterEditMode(AppListItemBinding binding)
            {
                itemView.setOnClickListener(v ->{});
                binding.label.setVisibility(View.GONE);
                binding.labelEdit.setVisibility(View.VISIBLE);
                binding.labelEdit.setText(binding.getUserApp().getLabelNew());
                binding.editButtonGroup.setVisibility(View.VISIBLE);

                binding.cancelEditButton.setOnClickListener(view -> exitEditMode(binding));
                binding.resetEditButton.setOnClickListener(view -> resetEditMode(binding));
                binding.confirmEditButton.setOnClickListener(view ->
                {
                    String newName = Objects.requireNonNull(binding.labelEdit.getText()).toString();
                    if (newName.isEmpty() || newName.equals(binding.getUserApp().getLabelOld()))
                        exitEditMode(binding);
                    else
                        renameApp(binding.getUserApp(), newName);

                });

            }

            private void exitEditMode(AppListItemBinding binding)
            {
                setOnClickListener(binding);
                binding.label.setVisibility(View.VISIBLE);
                binding.labelEdit.setVisibility(View.GONE);
                binding.editButtonGroup.setVisibility(View.GONE);
            }

            private void resetEditMode(AppListItemBinding binding)
            {
                if (!binding.getUserApp().isCustomized())
                    exitEditMode(binding);
                else
                    resetAppEdit(binding.getUserApp());
            }

            private void setOnClickListener(AppListItemBinding binding)
            {
                itemView.setOnClickListener(v ->
                {
                    Intent intent = packageManager.getLaunchIntentForPackage(binding.getUserApp().getPackageName());
                    if (intent != null)
                        activity.startActivity(intent,
                                ActivityOptions.makeClipRevealAnimation(itemView,0,0,100, 100).toBundle());
                });
            }

        }
    }
}
