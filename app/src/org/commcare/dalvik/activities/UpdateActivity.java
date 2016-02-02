package org.commcare.dalvik.activities;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.framework.CommCareActivityUIController;
import org.commcare.android.framework.WithUIController;
import org.commcare.android.resource.AppInstallStatus;
import org.commcare.android.resource.ResourceInstallUtils;
import org.commcare.android.tasks.InstallStagedUpdateTask;
import org.commcare.android.tasks.TaskListener;
import org.commcare.android.tasks.TaskListenerRegistrationException;
import org.commcare.android.tasks.UpdateTask;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.dialogs.CustomProgressDialog;
import org.commcare.dalvik.utils.ConnectivityStatus;
import org.javarosa.core.services.locale.Localization;

/**
 * Allow user to manage app updating:
 * - Check and download the latest update
 * - Stop a downloading update
 * - Apply a downloaded update
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class UpdateActivity extends CommCareActivity<UpdateActivity>
        implements TaskListener<Integer, AppInstallStatus>, WithUIController {

    private static final String TAG = UpdateActivity.class.getSimpleName();
    private static final String TASK_CANCELLING_KEY = "update_task_cancelling";
    private static final int DIALOG_UPGRADE_INSTALL = 6;

    private boolean taskIsCancelling;
    private UpdateTask updateTask;
    private UpdateUIController uiController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        uiController.setupUI();

        loadSaveInstanceState(savedInstanceState);

        boolean isRotation = savedInstanceState != null;
        setupUpdateTask(isRotation);
    }

    private void loadSaveInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            taskIsCancelling =
                    savedInstanceState.getBoolean(TASK_CANCELLING_KEY, false);
            uiController.loadSavedUIState(savedInstanceState);
        }
    }

    private void setupUpdateTask(boolean isRotation) {
        updateTask = UpdateTask.getRunningInstance();

        if (updateTask != null) {
            try {
                updateTask.registerTaskListener(this);
            } catch (TaskListenerRegistrationException e) {
                Log.e(TAG, "Attempting to register a TaskListener to an already " +
                        "registered task.");
                uiController.errorUiState();
            }
        } else if (!isRotation && !taskIsCancelling) {
            startUpdateCheck();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!ConnectivityStatus.isNetworkAvailable(this) &&
                ConnectivityStatus.isAirplaneModeOn(this)) {
            uiController.noConnectivityUiState();
            return;
        }

        setUiFromTask();
    }

    private void setUiFromTask() {
        if (updateTask != null) {
            if (taskIsCancelling) {
                uiController.cancellingUiState();
            } else {
                setUiStateFromTaskStatus(updateTask.getStatus());
            }

            int currentProgress = updateTask.getProgress();
            int maxProgress = updateTask.getMaxProgress();
            uiController.updateProgressBar(currentProgress, maxProgress);
        } else {
            setPendingUpdate();
        }
        uiController.refreshView();
    }

    private void setUiStateFromTaskStatus(AsyncTask.Status taskStatus) {
        switch (taskStatus) {
            case RUNNING:
                uiController.downloadingUiState();
                break;
            case PENDING:
                break;
            case FINISHED:
                uiController.errorUiState();
                break;
            default:
                uiController.errorUiState();
        }
    }

    private void setPendingUpdate() {
        if (ResourceInstallUtils.isUpdateReadyToInstall()) {
            uiController.unappliedUpdateAvailableUiState();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterTask();
    }

    private void unregisterTask() {
        if (updateTask != null) {
            try {
                updateTask.unregisterTaskListener(this);
            } catch (TaskListenerRegistrationException e) {
                Log.e(TAG, "Attempting to unregister a not previously " +
                        "registered TaskListener.");
            }
            updateTask = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(TASK_CANCELLING_KEY, taskIsCancelling);
        uiController.saveCurrentUIState(outState);
    }

    @Override
    public void handleTaskUpdate(Integer... vals) {
        int progress = vals[0];
        int max = vals[1];
        uiController.updateProgressBar(progress, max);
        String msg = Localization.get("updates.found",
                new String[]{"" + progress, "" + max});
        uiController.updateProgressText(msg);
    }

    @Override
    public void handleTaskCompletion(AppInstallStatus result) {
        if (result == AppInstallStatus.UpdateStaged) {
            uiController.unappliedUpdateAvailableUiState();
        } else if (result == AppInstallStatus.UpToDate) {
            uiController.upToDateUiState();
        } else {
            // Gives user generic failure warning; even if update staging
            // failed for a specific reason like xml syntax
            uiController.checkFailedUiState();
        }

        unregisterTask();

        uiController.refreshView();
    }

    @Override
    public void handleTaskCancellation(AppInstallStatus result) {
        unregisterTask();

        uiController.idleUiState();
    }

    protected void startUpdateCheck() {
        try {
            updateTask = UpdateTask.getNewInstance();
            updateTask.startPinnedNotification(this);
            updateTask.registerTaskListener(this);
        } catch (IllegalStateException e) {
            connectToRunningTask();
            return;
        } catch (TaskListenerRegistrationException e) {
            enterErrorState("Attempting to register a TaskListener to an " +
                    "already registered task.");
            return;
        }

        String ref = ResourceInstallUtils.getDefaultProfileRef();
        updateTask.execute(ref);
        uiController.downloadingUiState();
    }

    private void connectToRunningTask() {
        setupUpdateTask(false);

        setUiFromTask();
    }

    private void enterErrorState(String errorMsg) {
        Log.e(TAG, errorMsg);
        uiController.errorUiState();
    }

    public void stopUpdateCheck() {
        if (updateTask != null) {
            updateTask.cancelWasUserTriggered();
            updateTask.cancel(true);
            taskIsCancelling = true;
            uiController.cancellingUiState();
        } else {
            uiController.idleUiState();
        }
    }

    /**
     * Block the user with a dialog while the update is finalized.
     */
    protected void lauchUpdateInstallTask() {
        InstallStagedUpdateTask<UpdateActivity> task =
                new InstallStagedUpdateTask<UpdateActivity>(DIALOG_UPGRADE_INSTALL) {

                    @Override
                    protected void deliverResult(UpdateActivity receiver,
                                                 AppInstallStatus result) {
                        if (result == AppInstallStatus.Installed) {
                            receiver.logoutOnSuccessfulUpdate();
                        } else {
                            receiver.uiController.errorUiState();
                        }
                    }

                    @Override
                    protected void deliverUpdate(UpdateActivity receiver,
                                                 int[]... update) {
                    }

                    @Override
                    protected void deliverError(UpdateActivity receiver,
                                                Exception e) {
                        receiver.uiController.errorUiState();
                    }
                };
        task.connect(this);
        task.execute();
        uiController.applyingUpdateUiState();
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        if (taskId != DIALOG_UPGRADE_INSTALL) {
            Log.w(TAG, "taskId passed to generateProgressDialog does not match "
                    + "any valid possibilities in CommCareSetupActivity");
            return null;
        }
        String title = Localization.get("updates.installing.title");
        String message = Localization.get("updates.installing.message");
        CustomProgressDialog dialog =
                CustomProgressDialog.newInstance(title, message, taskId);
        dialog.setCancelable(false);
        return dialog;
    }

    private void logoutOnSuccessfulUpdate() {
        final String upgradeFinishedText =
                Localization.get("updates.install.finished");
        Toast.makeText(this, upgradeFinishedText, Toast.LENGTH_LONG).show();
        CommCareApplication._().expireUserSession();
        setResult(RESULT_OK);
        this.finish();
    }

    @Override
    public String getActivityTitle() {
        return "Update" + super.getActivityTitle();
    }

    @Override
    public void initUIController() {
        boolean fromAppManager = getIntent().getBooleanExtra(AppManagerActivity.KEY_LAUNCH_FROM_MANAGER, false);
        uiController = new UpdateUIController(this, fromAppManager);
    }

    @Override
    public CommCareActivityUIController getUIController() {
        return this.uiController;
    }

}
