/**
 * 
 */
package org.commcare.dalvik.activities;

import java.io.File;
import java.io.IOException;

import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.framework.ManagedUi;
import org.commcare.android.framework.UiElement;
import org.commcare.android.references.ArchiveFileRoot;
import org.commcare.android.tasks.UnzipTask;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.util.FileUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.PropertyUtils;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @author wspride
 *
 */

@ManagedUi(R.layout.screen_multimedia_inflater)
public class InstallArchiveActivity extends CommCareActivity<InstallArchiveActivity> {
	private static final int REQUEST_FILE_LOCATION = 1;

	public static final String EXTRA_FILE_DESTINATION = "ccodk_mia_filedest";

	@UiElement(value = R.id.screen_multimedia_inflater_prompt, locale="archive.install.prompt")
	TextView txtDisplayPrompt;

	@UiElement(value = R.id.screen_multimedia_install_messages, locale="archive.install.state.empty")
	TextView txtInteractiveMessages;

	@UiElement(R.id.screen_multimedia_inflater_location)
	EditText editFileLocation;

	@UiElement(R.id.screen_multimedia_inflater_filefetch)
	ImageButton btnFetchFiles;

	@UiElement(value = R.id.screen_multimedia_inflater_install, locale="archive.install.button")
	Button btnInstallArchive;

	boolean done = false;

	public static String TAG = "install-archive";

	private String currentRef;
	private String targetDirectory;

	/* (non-Javadoc)
	 * @see org.commcare.android.framework.CommCareActivity#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if(this.getIntent().hasExtra("archive-ref")) {
			currentRef = this.getIntent().getStringExtra("archive-ref");
			editFileLocation.setText(currentRef);
			InstallArchiveActivity.this.createArchive(editFileLocation.getText().toString());
		}

		btnFetchFiles.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				//Go fetch us a file path!
				Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
				intent.setType("file/*");
				try {
					startActivityForResult(intent, REQUEST_FILE_LOCATION);
				} catch(ActivityNotFoundException e) {
					Toast.makeText(InstallArchiveActivity.this, Localization.get("archive.install.no.browser"), Toast.LENGTH_LONG).show();
				}
			}
		});


		btnInstallArchive.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {

				InstallArchiveActivity.this.createArchive(editFileLocation.getText().toString());

			}

		});
	}

	public void createArchive(String filepath){
		currentRef = filepath;

		UnzipTask<InstallArchiveActivity> mUnzipTask = new UnzipTask<InstallArchiveActivity>() {

			@Override
			protected void deliverResult( InstallArchiveActivity receiver, Integer result) {
				Log.d(TAG, "delivering unzip result");
				if(result > 0){
					receiver.onUnzipSuccessful(result);
					return;
				} else {
					//assume that we've already set the error message, but make it look scary
					receiver.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
				}
			}

			@Override
			protected void deliverUpdate(InstallArchiveActivity receiver, String... update) {
				Log.d(TAG, "delivering unzip upate");
				receiver.updateProgress(CommCareTask.GENERIC_TASK_ID, update[0]);
				receiver.txtInteractiveMessages.setText(update[0]);
			}

			@Override
			protected void deliverError(InstallArchiveActivity receiver, Exception e) {
				Log.d(TAG, "unzip deliver error: " + e.getMessage());
				receiver.txtInteractiveMessages.setText(Localization.get("archive.install.error", new String[] {e.getMessage()}));
				receiver.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
			}
		};

		String readDir = currentRef;
		File mFile = new File(currentRef);
		File parent = mFile.getParentFile();
		//String fn = parent.toString() + "/" + ARCHIVE_UNZIP_LOCATION;
		String targetDirectory = getTargetFolder();;
		FileUtil.deleteFileOrDir(targetDirectory);

		mUnzipTask.connect(InstallArchiveActivity.this);
		Log.d(TAG, "executing task with: " + targetDirectory + " , " + readDir);
		mUnzipTask.execute(readDir, targetDirectory);

	}

	protected void onUnzipSuccessful(Integer result) {

		int lastIndex = currentRef.lastIndexOf("/");
		int dotIndex = currentRef.lastIndexOf(".");

		String fileNameString = currentRef.substring(lastIndex, dotIndex);

		String fileDestination = getTargetFolder();
		
        ArchiveFileRoot afr = CommCareApplication._().getArchiveFileRoot();
        String mGUID = afr.addArchiveFile(fileDestination);

		File mFile = new File(fileDestination+"/"+fileNameString);
		
		//need to copy evetything down a level to get the correct structure

		try {
			FileUtil.copyFileDeep(mFile, new File(fileDestination));
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		String ref = "jr://archive/" + mGUID + "/profile.xml";

		Intent i = new Intent(getIntent());
		i.putExtra("archive-ref", ref);
		setResult(RESULT_OK, i);
		finish();

	}

	public void updateProgress(int done, int total, int phase) {
		updateProgress(CommCareTask.GENERIC_TASK_ID, Localization.get("profile.found", new String[]{""+done,""+total}));
	}

	public void reportSuccess(boolean appChanged) {
		//If things worked, go ahead and clear out any warnings to the contrary
		CommCareApplication._().clearNotifications("install_update");

		if(!appChanged) {
			Toast.makeText(this, Localization.get("updates.success"), Toast.LENGTH_LONG).show();
		}
		done(appChanged);
	}

	public void done(boolean requireRefresh) {
		//TODO: We might have gotten here due to being called from the outside, in which
		//case we should manually start up the home activity
		Intent i = new Intent(getApplicationContext(), CommCareHomeActivity.class);
		i.putExtra(CommCareSetupActivity.KEY_REQUIRE_REFRESH, requireRefresh);
		startActivity(i);
		finish();
		return;
	}


	/*
	 * (non-Javadoc)
	 * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if(requestCode == REQUEST_FILE_LOCATION) {
			if(resultCode == Activity.RESULT_OK) {
				String filePath = intent.getData().getPath();
				editFileLocation.setText(filePath);
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.commcare.android.framework.CommCareActivity#onResume()
	 */
	@Override
	protected void onResume() {
		super.onResume();
		evalState();
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onCreateDialog(int)
	 */
	@Override
	protected Dialog onCreateDialog(int id) {

		if(id == UnzipTask.UNZIP_TASK_ID) {
			ProgressDialog progressDialog = new ProgressDialog(this);
			progressDialog.setTitle("Unzipping Files");
			progressDialog.setMessage("Unzipping...");
			return progressDialog;
		}
		return null;
	}

	private void evalState() {
		if(done) {
			txtInteractiveMessages.setText(Localization.get("archive.install.state.done"));
			this.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification);
			btnInstallArchive.setEnabled(false);
			return;
		}

		String location = editFileLocation.getText().toString();
		if("".equals(location)) {
			txtInteractiveMessages.setText(Localization.get("archive.install.state.empty"));
			this.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification);
			btnInstallArchive.setEnabled(false);
			return;
		}

		if(!(new File(location)).exists()) {
			txtInteractiveMessages.setText(Localization.get("archive.install.state.invalid.path"));
			this.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
			btnInstallArchive.setEnabled(false);
			return;
		}

		else {
			txtInteractiveMessages.setText(Localization.get("archive.install.state.ready"));
			this.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification);
			btnInstallArchive.setEnabled(true);
			return;
		}
	}

	/* (non-Javadoc)
	 * @see org.commcare.android.tasks.templates.CommCareTaskConnector#taskCancelled(int)
	 */
	@Override
	public void taskCancelled(int id) {
		txtInteractiveMessages.setText(Localization.get("archive.install.cancelled"));
		this.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
	}

	public String getTargetFolder(){
		if(targetDirectory != null){
			return targetDirectory;
		}
		targetDirectory = Environment.getExternalStorageDirectory().toString() + "/Android/data/"+ getPackageName() +"/files/app/"+PropertyUtils.genUUID();
		return targetDirectory;
	}

}