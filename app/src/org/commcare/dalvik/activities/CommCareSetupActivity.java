	
/**
 * 
 */
package org.commcare.dalvik.activities;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.framework.WrappingSpinnerAdapter;
import org.commcare.android.models.notifications.NotificationMessage;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.tasks.ResourceEngineListener;
import org.commcare.android.tasks.ResourceEngineTask;
import org.commcare.android.tasks.ResourceEngineTask.ResourceEngineOutcomes;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.UnresolvedResourceException;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.PropertyUtils;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
/**
 * The CommCareStartupActivity is purely responsible for identifying
 * the state of the application (uninstalled, installed) and performing
 * any necessary setup to get to a place where CommCare can load normally.
 * 
 * If the startup activity identifies that the app is installed properly
 * it should not ever require interaction or be visible to the user. 
 * 
 * @author ctsims
 *
 */
public class CommCareSetupActivity extends Activity implements ResourceEngineListener{
	
//	public static final String DATABASE_STATE = "database_state";
	public static final String RESOURCE_STATE = "resource_state";
	public static final String KEY_PROFILE_REF = "app_profile_ref";
	public static final String KEY_UPGRADE_MODE = "app_upgrade_mode";
	public static final String KEY_REQUIRE_REFRESH = "require_referesh";
	public static final String KEY_AUTO = "is_auto_update";
	
	public enum UiState { advanced, basic, ready, error };
	public UiState uiState = UiState.basic;
	
	public static final int MODE_BASIC = Menu.FIRST;
	public static final int MODE_ADVANCED = Menu.FIRST + 1;
	
	public static final int DIALOG_INSTALL_PROGRESS = 0;
	public static final int DIALOG_VERIFY_PROGRESS = 1;
	
	public static final int BARCODE_CAPTURE = 1;
	public static final int MISSING_MEDIA_ACTIVITY=2;
	
	public static final int RETRY_LIMIT = 20;
	
	boolean startAllowed = true;
	
	int dbState;
	int resourceState;
	int retryCount=0;
	
	String incomingRef;
	
	View advancedView;
	EditText editProfileRef;
	TextView mainMessage;
	Spinner urlSpinner;
	Button installButton;
	Button mScanBarcodeButton;
	Button addressEntryButton;
	Button startOverButton;
	Button retryButton;
    private ProgressDialog mProgressDialog;
    private ProgressDialog vProgressDialog;
    
    String [] urlVals;
    int previousUrlPosition=0;
	
	boolean upgradeMode = false;
	boolean partialMode = false;
	
	CommCareApp ccApp;
	
	//Whether this needs to be interactive (if it's automatic, we want to skip a lot of the UI stuff
	boolean isAuto = false;
	
	View banner;

	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.first_start_screen);
		
		//Grab Views
		editProfileRef = (EditText)this.findViewById(R.id.edit_profile_location);
		advancedView = this.findViewById(R.id.advanced_panel);
		mainMessage = (TextView)this.findViewById(R.id.str_setup_message);
		urlSpinner = (Spinner)this.findViewById(R.id.url_spinner);
		installButton = (Button)this.findViewById(R.id.start_install);
    	mScanBarcodeButton = (Button)this.findViewById(R.id.btn_fetch_uri);
    	addressEntryButton = (Button)this.findViewById(R.id.enter_app_location);
    	startOverButton = (Button)this.findViewById(R.id.start_over);
    	retryButton = (Button)this.findViewById(R.id.retry_install);


    	//Retrieve instance state
		if(savedInstanceState == null) {
			incomingRef = this.getIntent().getStringExtra(KEY_PROFILE_REF);
			upgradeMode = this.getIntent().getBooleanExtra(KEY_UPGRADE_MODE, false);
			isAuto = this.getIntent().getBooleanExtra(KEY_AUTO, false);
		} else {
			String uiStateEncoded = savedInstanceState.getString("advanced");
			this.uiState = uiStateEncoded == null ? UiState.basic : UiState.valueOf(UiState.class, uiStateEncoded);
	        incomingRef = savedInstanceState.getString("profileref");
	        upgradeMode = savedInstanceState.getBoolean(KEY_UPGRADE_MODE);
	        isAuto = savedInstanceState.getBoolean(KEY_AUTO);
		}
		
		editProfileRef.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
		urlSpinner.setAdapter(new WrappingSpinnerAdapter(urlSpinner.getAdapter(), getResources().getStringArray(R.array.url_list_selected_display)));
		urlVals = getResources().getStringArray(R.array.url_vals);
		
    	retryButton.setText(Localization.get("install.button.retry"));
    	installButton.setText(Localization.get("install.button.start"));
		
		urlSpinner.setOnItemSelectedListener(new OnItemSelectedListener(){

			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1,
					int arg2, long arg3) {
				if((previousUrlPosition == 0 || previousUrlPosition == 1) && arg2 == 2){
					editProfileRef.setText(R.string.default_app_server);
				}
				else if(previousUrlPosition == 2 && (arg2 == 0 || arg2 == 1)){
					editProfileRef.setText("");
				}
				previousUrlPosition = arg2;
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {}
			
		});
		//First, identify the binary state
		dbState = CommCareApplication._().getDatabaseState();
		resourceState = CommCareApplication._().getAppResourceState();
		
		if(Intent.ACTION_VIEW.equals(this.getIntent().getAction())) {
			//We got called from an outside application, it's gonna be a wild ride!
			incomingRef = this.getIntent().getData().toString();
			//Now just start up normally.
		} else {
			//Otherwise we're starting up being called from inside the app. Check to see if everything is set
			//and we can just skip this unless it's upgradeMode
			if(dbState == CommCareApplication.STATE_READY && resourceState == CommCareApplication.STATE_READY && !upgradeMode) {
		        Intent i = new Intent(getIntent());	
		        
		        setResult(RESULT_OK, i);
		        finish();
		        return;
			}
		}
		    	
		mScanBarcodeButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
                try {	
                    Intent i = new Intent("com.google.zxing.client.android.SCAN");
                	//Barcode only
                    i.putExtra("SCAN_FORMATS","QR_CODE");
                    CommCareSetupActivity.this.startActivityForResult(i, BARCODE_CAPTURE);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(CommCareSetupActivity.this,"No barcode scanner installed on phone!", Toast.LENGTH_SHORT).show();
                    mScanBarcodeButton.setVisibility(View.GONE);
                }

			}
			
		});
		
		addressEntryButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				setModeToAdvanced();
			}
			
		});
		addressEntryButton.setText(Localization.get("install.button.enter"));
		
		startOverButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if(upgradeMode) {
					startResourceInstall(true);
				} else {
					retryCount = 0;
					partialMode = false;
					setModeToBasic();
				}
			}
			
		});
		
		startOverButton.setText(Localization.get("install.button.startover"));
		
		retryButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				
				if(!upgradeMode) {
					partialMode = true;
				}
				
				startResourceInstall(false);
			}
		});
		
		if(incomingRef == null || uiState == UiState.advanced) {
			//editProfileRef.setText(PreferenceManager.getDefaultSharedPreferences(this).getString("default_app_server", this.getString(R.string.default_app_server)));
			editProfileRef.setText("");
			if(this.uiState == UiState.advanced) {
				this.setModeToAdvanced();
			} else {
				this.setModeToBasic();
			}
		} else {
			this.setModeToReady(incomingRef);
		}
		
		installButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {	
				
				if(dbState == CommCareApplication.STATE_READY) {
					//If app is fully initialized, don't need to do anything
				} 
				
				//Now check on the resources
				if(resourceState == CommCareApplication.STATE_READY) {
					if(!upgradeMode) {
						fail(NotificationMessageFactory.message(ResourceEngineOutcomes.StatusFailState), true);
					}
				} else if(resourceState == CommCareApplication.STATE_UNINSTALLED) {
					startResourceInstall();
				} else if(resourceState == CommCareApplication.STATE_UPGRADE && upgradeMode) {
					//This will come up if the upgrade has problems  
					startResourceInstall();
				}
			}
		});
		if(upgradeMode) {
			mainMessage.setText(Localization.get("updates.check"));
			startResourceInstall();
		}
		
		banner = this.findViewById(R.id.screen_first_start_banner);
		
        final View activityRootView = findViewById(R.id.screen_first_start_main);
        activityRootView.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
            	int hideAll = CommCareSetupActivity.this.getResources().getInteger(R.integer.login_screen_hide_all_cuttoff);
            	int hideBanner = CommCareSetupActivity.this.getResources().getInteger(R.integer.login_screen_hide_banner_cuttoff);
                int height = activityRootView.getHeight();
                
                if(height < hideAll) {
                	banner.setVisibility(View.GONE);
                } else if(height < hideBanner) {
                	banner.setVisibility(View.GONE);
                }  else {
                	banner.setVisibility(View.VISIBLE);
                }
             }
        });
        
        //Before we get stared, we need to see whether we've started an install before, this determines whether we 
        //want to continue in partial mode if, for instance, the app shut down after trying to install. This will be
        //the standard 
        
        
	}
	/*
	 * (non-Javadoc)
	 * @see android.app.Activity#onResume()
	 */
	@Override
	protected void onResume() {
		super.onResume();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
    	//Make sure we're not holding onto the wake lock still
    	unlock();
	}
	
    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("advanced", uiState.toString());
        outState.putString("profileref", incomingRef);
        outState.putBoolean(KEY_UPGRADE_MODE, upgradeMode);
        outState.putBoolean(KEY_AUTO, isAuto);
    }
	
	/* (non-Javadoc)
	 * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if(requestCode == BARCODE_CAPTURE) {
			if(resultCode == Activity.RESULT_CANCELED) {
				//Basically nothing
			} else if(resultCode == Activity.RESULT_OK) {
    			String result = data.getStringExtra("SCAN_RESULT");
				incomingRef = result;
				//Definitely have a URI now.
				try{
					ReferenceManager._().DeriveReference(incomingRef);
				}
				catch(InvalidReferenceException ire){
					this.setModeToBasic(Localization.get("install.bad.ref"));
					System.out.println("528 bad ref caught");
					return;
				}
				
				this.setModeToReady(result);
			}
		}
	}
	
	private String getRef(){
		String ref;
		if(this.uiState == UiState.advanced) {
			int selectedIndex = urlSpinner.getSelectedItemPosition();
			String selectedString = urlVals[selectedIndex];
			if(previousUrlPosition != 2){
				ref = selectedString + editProfileRef.getText().toString();
			}
			else{
				ref = editProfileRef.getText().toString();
			}
		}
		else{
			ref = incomingRef;
		}
		return ref;
	}
	
	private CommCareApp getCommCareApp(){
		
		CommCareApp app = null;
		
		// we are in upgrade mode, just send back current app
		
		if(upgradeMode){
			app = CommCareApplication._().getCurrentApp();
			return app;
		}
		
		//we have a clean slate, create a new app
		if(partialMode){
			return ccApp;
		}
		else{
			ApplicationRecord newRecord = new ApplicationRecord(PropertyUtils.genUUID().replace("-",""), ApplicationRecord.STATUS_UNINITIALIZED);
			app = new CommCareApp(newRecord);
			return app;
		}
	}

	private void startResourceInstall() {
		this.startResourceInstall(true);
	}
	
	private void startResourceInstall(boolean startOverUpgrade) {
		
		if(startAllowed) {
			String ref = getRef();
			
			CommCareApp app = getCommCareApp();
			
			ccApp = app;
			
			ResourceEngineTask task = new ResourceEngineTask(this, upgradeMode, partialMode, app, startOverUpgrade);
			
			task.setListener(this);
			
			task.execute(ref);
			wakelock();
			
			this.showDialog(DIALOG_INSTALL_PROGRESS);
		} else {
			Log.i("commcare-install", "Blocked a resource install press since a task was already running");
		}
	}

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
    	menu.add(0, MODE_BASIC, 0, Localization.get("menu.basic")).setIcon(android.R.drawable.ic_menu_help);
    	menu.add(0, MODE_ADVANCED, 0, Localization.get("menu.advanced")).setIcon(android.R.drawable.ic_menu_edit);
        return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        
        MenuItem basic = menu.findItem(MODE_BASIC);
        MenuItem advanced = menu.findItem(MODE_ADVANCED);
        
        if(uiState == UiState.advanced) {
        	basic.setVisible(true);
        	advanced.setVisible(false);
        } else if(uiState == UiState.ready){
        	basic.setVisible(true);
        	advanced.setVisible(true);
        } else{
        	basic.setVisible(false);
        	advanced.setVisible(true);
        }
        return true;
    }
    
    public void setModeToReady(String incomingRef) {
    	this.uiState = UiState.ready;
    	mainMessage.setText(Localization.get("install.ready"));
		editProfileRef.setText(incomingRef);
    	advancedView.setVisibility(View.GONE);
    	mScanBarcodeButton.setVisibility(View.GONE);
    	installButton.setVisibility(View.VISIBLE);
    	startOverButton.setVisibility(View.VISIBLE);
    	addressEntryButton.setVisibility(View.GONE);
    	retryButton.setVisibility(View.GONE);
    }
    
    public void setModeToError(String message, boolean canRetry){
    	this.uiState = UiState.error;
    	mainMessage.setText(message);
    	advancedView.setVisibility(View.GONE);
    	mScanBarcodeButton.setVisibility(View.GONE);
    	installButton.setVisibility(View.GONE);
    	startOverButton.setVisibility(View.VISIBLE);
    	addressEntryButton.setVisibility(View.GONE);
    	if(canRetry){
    		retryButton.setVisibility(View.VISIBLE);
    	}
    	else{
    		retryButton.setVisibility(View.GONE);
    	}
    }
    
    public void setModeToBasic(){
    	this.setModeToBasic(Localization.get("install.barcode"));
    }
    
    public void setModeToBasic(String message){
    	this.uiState = UiState.basic;
    	editProfileRef.setText("");	
    	this.incomingRef = null;
    	mainMessage.setText(message);
    	addressEntryButton.setVisibility(View.VISIBLE);
    	advancedView.setVisibility(View.GONE);
    	mScanBarcodeButton.setVisibility(View.VISIBLE);
    	startOverButton.setVisibility(View.GONE);
    	installButton.setVisibility(View.GONE);
    	retryButton.setVisibility(View.GONE);
    }

    public void setModeToAdvanced(){
    	if(this.uiState == uiState.ready){
    		previousUrlPosition = -1;
    		urlSpinner.setSelection(2);
    	}
    	this.uiState = UiState.advanced;
    	mainMessage.setText(Localization.get("install.manual"));
    	advancedView.setVisibility(View.VISIBLE);
    	mScanBarcodeButton.setVisibility(View.GONE);
    	addressEntryButton.setVisibility(View.GONE);
        installButton.setVisibility(View.VISIBLE);
        startOverButton.setVisibility(View.VISIBLE);
    	installButton.setEnabled(true);
    	retryButton.setVisibility(View.GONE);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	if(!upgradeMode) {
	        switch (item.getItemId()) {
	            case MODE_BASIC:
	            	setModeToBasic();
	                return true;
	            case MODE_ADVANCED:
	            	setModeToAdvanced();
	            	return true;	
	        }
    	}
        return super.onOptionsItemSelected(item);
    }

	/* (non-Javadoc)
	 * @see android.app.Activity#onCreateDialog(int)
	 */
	@Override
	protected Dialog onCreateDialog(int id) {
		if(id == DIALOG_INSTALL_PROGRESS) {
            mProgressDialog = new ProgressDialog(this);
            if(upgradeMode) {
            	mProgressDialog.setTitle(Localization.get("updates.title"));
            	mProgressDialog.setMessage(Localization.get("updates.checking"));
            } else {
            	mProgressDialog.setTitle(Localization.get("updates.resources.initialization"));
            	mProgressDialog.setMessage(Localization.get("updates.resources.profile"));
            }
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setCancelable(false);
            return mProgressDialog;
		}
		else if(id == DIALOG_VERIFY_PROGRESS) {
			vProgressDialog=  new ProgressDialog(this);
			vProgressDialog.setTitle(Localization.get("verification.title"));
			vProgressDialog.setMessage(Localization.get("verification.checking"));
			return vProgressDialog;
		}
		return null;
	}
	
	public void updateVerifyProgress(int done, int total) {
		if(vProgressDialog != null) {
			vProgressDialog.setMessage(Localization.get("verify.progress",new String[] {""+done,""+total}));
		}
	}
	

	public void updateProgress(int done, int total, int phase) {
		if(mProgressDialog != null) {
            if(upgradeMode) {

            	if(phase == ResourceEngineTask.PHASE_DOWNLOAD) {
            		mProgressDialog.setMessage(Localization.get("updates.found", new String[] {""+done,""+total}));
            	} if(phase == ResourceEngineTask.PHASE_COMMIT) {
            		mProgressDialog.setMessage(Localization.get("updates.downloaded"));
            	}
            }
			else {
				mProgressDialog.setMessage(Localization.get("profile.found", new String[]{""+done,""+total}));
			}
		}
	}
	
	public void done(boolean requireRefresh) {
		unlock();
		//TODO: We might have gotten here due to being called from the outside, in which
		//case we should manually start up the home activity
		
		if(Intent.ACTION_VIEW.equals(CommCareSetupActivity.this.getIntent().getAction())) {
			//Call out to CommCare Home
 	       Intent i = new Intent(getApplicationContext(), CommCareHomeActivity.class);
 	       i.putExtra(KEY_REQUIRE_REFRESH, requireRefresh);
 	       startActivity(i);
 	       finish();
 	       
 	       return;
		} else {
			//Good to go
	        Intent i = new Intent(getIntent());
	        i.putExtra(KEY_REQUIRE_REFRESH, requireRefresh);
	        setResult(RESULT_OK, i);
	        finish();
	        return;
		}
	}

	public void fail(NotificationMessage message) {
		fail(message, false);
	}
	
	public void fail(NotificationMessage message, boolean alwaysNotify) {	
		fail(message, alwaysNotify, true);
	}
	
	public void fail(NotificationMessage message, boolean alwaysNotify, boolean canRetry){
		unlock();
		Toast.makeText(this, message.getTitle(), Toast.LENGTH_LONG).show();
		
		retryCount++;
		
		if(retryCount > RETRY_LIMIT){
			canRetry = false;
		}
		
		if(isAuto || alwaysNotify) {
			CommCareApplication._().reportNotificationMessage(message);
		}
		if(isAuto) {
			done(false);
		} else {
			if(alwaysNotify) {
				setModeToError(Localization.get("notification.for.details.wrapper", new String[] {message.getDetails()}),canRetry);
			} else {
				setModeToError(message.getDetails(),canRetry);
				mainMessage.setText(message.getDetails());
			}
		}
	}

	// All final paths from the Update are handled here (Important! Some interaction modes should always auto-exit this activity)
	// Everything here should call one of: fail() or done() 
	
	public void reportSuccess(boolean appChanged) {
		this.dismissDialog(DIALOG_INSTALL_PROGRESS);
		
		//If things worked, go ahead and clear out any warnings to the contrary
		CommCareApplication._().clearNotifications("install_update");
		
		if(!appChanged) {
			Toast.makeText(this, Localization.get("updates.success"), Toast.LENGTH_LONG).show();
		}
		done(appChanged);
	}

	public void failMissingResource(UnresolvedResourceException ure, ResourceEngineOutcomes statusMissing) {
		this.dismissDialog(DIALOG_INSTALL_PROGRESS);
		fail(NotificationMessageFactory.message(statusMissing, new String[] {null, ure.getResource().getResourceId(), ure.getMessage()}), ure.isMessageUseful());
		
	}

	public void failBadReqs(int code, String vRequired, String vAvailable, boolean majorIsProblem) {
		this.dismissDialog(DIALOG_INSTALL_PROGRESS);

		String versionMismatch = Localization.get("install.version.mismatch", new String[] {vRequired,vAvailable});
		
		String error = "";
		if(majorIsProblem){
			error=Localization.get("install.major.mismatch");
		}
		else{
			error=Localization.get("install.minor.mismatch");
		}
		
		fail(NotificationMessageFactory.message(ResourceEngineOutcomes.StatusBadReqs, new String[] {null, versionMismatch, error}), true);
	}

	public void failUnknown(ResourceEngineOutcomes unknown) {
		this.dismissDialog(DIALOG_INSTALL_PROGRESS);
		
		fail(NotificationMessageFactory.message(unknown));
	}

	public void failWithNotification(ResourceEngineOutcomes statusfailstate) {
		this.dismissDialog(DIALOG_INSTALL_PROGRESS);
		fail(NotificationMessageFactory.message(statusfailstate), true);
	}
	
	
	
	//END exit paths
	
    //Don't ever lose this reference
    private static WakeLock wakelock;
    
    private void wakelock() {
    	unlock();
    	startAllowed = false;
    	PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
    	if(wakelock == null) {
    		wakelock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "CommCareAppInstall");
    	}
    	//CTS: We used to have a timeout here, but apparently Android straight up crashes if you acquire a timed wakelock
    	//and then release it before the timeout.
    	wakelock.acquire();
    }
    
    private void unlock() {
    	startAllowed = true;
    	if(wakelock != null && wakelock.isHeld()) {
    		wakelock.release();
    	}
    }
    
    @Override
    public void onBackPressed(){
        if(uiState == UiState.advanced) {
        	setModeToBasic();
        } else if(uiState == UiState.ready){
        	setModeToBasic();
        } else{
        	super.onBackPressed();
        }
    }
}
