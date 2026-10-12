package org.familypolicy.android;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.pm.PackageManager;
import android.util.Log;

public final class UpdateJobService extends JobService {
    private static final String TAG = "FamilyPolicyUpdate";

    @Override
    public boolean onStartJob(JobParameters parameters) {
        Thread worker = new Thread(() -> {
            try {
                GitHubUpdateClient.UpdateInfo update =
                    new GitHubUpdateClient().findUpdate(AppUpdateManager.installedVersionCode(this));
                if (update != null) {
                    AppUpdateManager.notifyUpdate(this, update);
                }
            } catch (PackageManager.NameNotFoundException | java.io.IOException | org.json.JSONException error) {
                Log.w(TAG, "Scheduled update check failed; Android will retry later.", error);
            } finally {
                jobFinished(parameters, false);
            }
        }, "family-policy-update-check");
        worker.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters parameters) {
        return true;
    }
}
