package com.nabinbhandari.android.permissions;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;

import java.util.ArrayList;

/**
 * Created by Nabin Bhandari on 7/21/2017 on 11:19 PM
 */

@SuppressWarnings("unchecked")
@TargetApi(Build.VERSION_CODES.M)
public class PermissionsActivity extends Activity {

    private static final int RC_SETTINGS = 6739;
    private static final int RC_PERMISSION = 6937;

    static final String EXTRA_PERMISSIONS = "permissions";
    static final String EXTRA_RATIONALE = "rationale";

    static PermissionHandler permissionHandler;

    private boolean cleanHandlerOnDestroy = true;
    private ArrayList<String> allPermissions, deniedPermissions, noRationaleList;

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (intent == null || !intent.hasExtra(EXTRA_PERMISSIONS)) {
            finish();
            return;
        }

        getWindow().setStatusBarColor(0);
        allPermissions = (ArrayList<String>) intent.getSerializableExtra(EXTRA_PERMISSIONS);
        deniedPermissions = new ArrayList<>();
        noRationaleList = new ArrayList<>();

        boolean noRationale = true;
        for (String permission : allPermissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                deniedPermissions.add(permission);
                if (shouldShowRequestPermissionRationale(permission)) {
                    noRationale = false;
                } else {
                    noRationaleList.add(permission);
                }
            }
        }

        String rationale = intent.getStringExtra(EXTRA_RATIONALE);
        if (noRationale || TextUtils.isEmpty(rationale)) {
            Permissions.log("No rationale.");
            requestPermissions(deniedPermissions.toArray(new String[0]), RC_PERMISSION);
        } else {
            Permissions.log("Show rationale.");
            showRationale(rationale);
        }
    }

    private void showRationale(String rationale) {
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    requestPermissions(deniedPermissions.toArray(new String[0]), RC_PERMISSION);
                } else {
                    deny();
                }
            }
        };
        new AlertDialog.Builder(this).setTitle(Permissions.dialogTitle)
                .setMessage(rationale)
                .setPositiveButton(android.R.string.ok, listener)
                .setNegativeButton(android.R.string.cancel, listener)
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        deny();
                    }
                }).create().show();
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (grantResults.length == 0) {
            deny();
        } else {
            deniedPermissions.clear();
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    deniedPermissions.add(permissions[i]);
                }
            }
            if (deniedPermissions.size() == 0) {
                Permissions.log("Just allowed.");
                grant();
            } else {
                ArrayList<String> blockedList = new ArrayList<>(); //set not to ask again.
                ArrayList<String> justBlockedList = new ArrayList<>(); //just set not to ask again.
                ArrayList<String> justDeniedList = new ArrayList<>();
                for (String permission : deniedPermissions) {
                    if (shouldShowRequestPermissionRationale(permission)) {
                        justDeniedList.add(permission);
                    } else {
                        blockedList.add(permission);
                        if (!noRationaleList.contains(permission)) {
                            justBlockedList.add(permission);
                        }
                    }
                }

                if (justBlockedList.size() > 0) { //checked don't ask again for at least one.
                    if (permissionHandler != null) {
                        permissionHandler.onJustBlocked(this, justBlockedList, deniedPermissions);
                    }
                    finish();
                } else if (justDeniedList.size() > 0) { //clicked deny for at least one.
                    deny();
                } else { //unavailable permissions were already set not to ask again.
                    if (permissionHandler != null && !permissionHandler.onBlocked(this,
                            blockedList)) {
                        sendToSettings();
                    } else finish();
                }
            }
        }
    }

    private void sendToSettings() {
        if (!Permissions.sendSetNotToAskAgainToSettings) {
            deny();
            return;
        }
        Permissions.log("Ask to go to settings.");
        new AlertDialog.Builder(this).setTitle(Permissions.dialogTitle)
                .setMessage(Permissions.forceDeniedDialogMessage)
                .setPositiveButton(Permissions.settingsText, new DialogInterface.OnClickListener() {
                    @Override
                    @SuppressWarnings("InlinedAPI")
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", getPackageName(), null));
                        startActivityForResult(intent, RC_SETTINGS);
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deny();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        deny();
                    }
                }).create().show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_SETTINGS && permissionHandler != null) {
            String first = allPermissions.get(0);
            if (allPermissions.size() == 0) {
                Permissions.runPermissionCheck(this, null, permissionHandler, first);
            } else {
                ArrayList<String> all = new ArrayList<>(allPermissions);
                all.remove(0);
                Permissions.runPermissionCheck(this, null, permissionHandler, first,
                        all.toArray(new String[0]));
            }
            cleanHandlerOnDestroy = false;
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        if (cleanHandlerOnDestroy) {
            permissionHandler = null;
        }
        super.onDestroy();
    }

    private void deny() {
        if (permissionHandler != null) {
            permissionHandler.onDenied(this, deniedPermissions);
        }
        finish();
    }

    private void grant() {
        if (permissionHandler != null) {
            permissionHandler.onGranted();
        }
        finish();
    }

}