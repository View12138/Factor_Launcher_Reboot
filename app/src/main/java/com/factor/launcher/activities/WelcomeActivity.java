package com.factor.launcher.activities;

import android.Manifest;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.databinding.DataBindingUtil;
import com.factor.launcher.R;
import com.factor.launcher.databinding.ActivityWelcomeBinding;
import com.factor.launcher.util.Constants;
import pub.devrel.easypermissions.AppSettingsDialog;
import pub.devrel.easypermissions.EasyPermissions;
import pub.devrel.easypermissions.PermissionRequest;

import java.util.List;

public class WelcomeActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks
{
    private final String[] perms = {Manifest.permission.READ_EXTERNAL_STORAGE};

    private ActivityWelcomeBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_welcome);

        binding.welcomeHomePager.addView(binding.welcomePage);

        EasyPermissions.requestPermissions(
                new PermissionRequest.Builder(this, Constants.STORAGE_PERMISSION_CODE, perms)
                        .setRationale("Factor launcher needs to access your external storage")
                        .setPositiveButtonText("Okay")
                        .setNegativeButtonText("Cancel")
                        .setTheme(R.style.TransparentDialogTheme)
                        .build());



    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> perms)
    {
        Intent i = new Intent(getApplicationContext(), HomeActivity.class);
        startActivity(i);
    }


    @Override
    public void onPermissionsDenied(int requestCode, @NonNull List<String> perms) {
        // (Optional) Check whether the user denied any permissions and checked "NEVER ASK AGAIN."
        // This will display a dialog directing them to enable the permission in app settings.
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            new AppSettingsDialog.Builder(this).build().show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == AppSettingsDialog.DEFAULT_SETTINGS_REQ_CODE)
        {
            // Do something after user returned from app settings screen, like showing a Toast.
        }
    }
}