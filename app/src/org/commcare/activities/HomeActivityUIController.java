package org.commcare.activities;

import android.annotation.SuppressLint;
import android.os.Build;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.view.ViewTreeObserver;
import android.widget.Toast;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.adapters.HomeScreenAdapter;
import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.preferences.CommCarePreferences;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.suite.model.Profile;

import java.util.Vector;

/**
 * Handles home activity UI
 *
 * @author Aliza Stone (astone@dimagi.com)
 */
public class HomeActivityUIController implements CommCareActivityUIController {

    private final CommCareHomeActivity activity;

    private HomeScreenAdapter adapter;

    public HomeActivityUIController(CommCareHomeActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        activity.setContentView(R.layout.home_screen);
        adapter = new HomeScreenAdapter(activity, getHiddenButtons(), CommCareHomeActivity.isDemoUser());
        setupGridView();
    }

    @Override
    public void refreshView() {
        if (adapter != null) {
            // adapter can be null if backstack was cleared for memory reasons
            adapter.notifyDataSetChanged();
        }
    }

    private static Vector<String> getHiddenButtons() {
        CommCareApp ccApp = CommCareApplication._().getCurrentApp();
        Vector<String> hiddenButtons = new Vector<>();

        Profile p = ccApp.getCommCarePlatform().getCurrentProfile();
        if ((p != null && !p.isFeatureActive(Profile.FEATURE_REVIEW)) || !CommCarePreferences.isSavedFormsEnabled()) {
            hiddenButtons.add("saved");
        }

        if (!CommCarePreferences.isIncompleteFormsEnabled()) {
            hiddenButtons.add("incomplete");
        }
        if (!DeveloperPreferences.isHomeReportEnabled()) {
            hiddenButtons.add("report");
        }

        return hiddenButtons;
    }

    private void setupGridView() {
        final RecyclerView grid = (RecyclerView)activity.findViewById(R.id.home_gridview_buttons);
        grid.setHasFixedSize(false);

        StaggeredGridLayoutManager gridView =
                new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        grid.setLayoutManager(gridView);
        grid.setItemAnimator(null);
        grid.setAdapter(adapter);

        grid.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @SuppressLint("NewApi")
            @Override
            public void onGlobalLayout() {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                    grid.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                } else {
                    grid.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }

                grid.requestLayout();
                adapter.notifyDataSetChanged();
                activity.rebuildOptionMenu();
            }
        });
    }

    // TODO: Use syncNeeded flag to change color of sync message
    protected void displayMessage(String message, boolean syncNeeded, boolean suppressToast) {
        if (!suppressToast) {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        }

        // Manually route message payloads since RecyclerView payloads are a pain in the ass
        adapter.setMessagePayload(adapter.getSyncButtonPosition(), message);
        adapter.notifyItemChanged(adapter.getSyncButtonPosition());
    }
}
