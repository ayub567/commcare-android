/**
 * 
 */
package org.commcare.android.application;

import java.io.File;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Hashtable;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.commcare.android.R;
import org.commcare.android.database.CommCareDBCursorFactory;
import org.commcare.android.database.CommCareOpenHelper;
import org.commcare.android.database.DbHelper;
import org.commcare.android.database.EncryptedModel;
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.models.Case;
import org.commcare.android.models.FormRecord;
import org.commcare.android.models.Referral;
import org.commcare.android.models.User;
import org.commcare.android.references.JavaFileRoot;
import org.commcare.android.references.JavaHttpRoot;
import org.commcare.android.tasks.ExceptionReportTask;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.CommCareExceptionHandler;
import org.commcare.android.util.CryptUtil;
import org.commcare.android.util.ODKPropertyManager;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.reference.RootTranslator;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.PropertyManager;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.storage.Persistable;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteException;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * @author ctsims
 *
 */
public class CommCareApplication extends Application {
	
	public static final int STATE_UNINSTALLED = 0;
	public static final int STATE_UPGRADE = 1;
	public static final int STATE_READY = 2;
	public static final int STATE_CORRUPTED = 4;
	
	private int dbState;
	private int resourceState;
	
	private byte[] key;

	private static CommCareApplication app;
	
	private AndroidCommCarePlatform platform;
	
	private static SQLiteDatabase database; 
	
	private SharedPreferences appPreferences;

	@Override
	public void onCreate() {
		super.onCreate();
		
		Thread.setDefaultUncaughtExceptionHandler(new CommCareExceptionHandler(Thread.getDefaultUncaughtExceptionHandler()));
		
		PropertyManager.setPropertyManager(new ODKPropertyManager());
		
		createPaths();
		setRoots();
		
		CommCareApplication.app = this;
		
        appPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		
		int[] version = getCommCareVersion();
		platform = new AndroidCommCarePlatform(version[0], version[1], this);
		
		//The fallback in case the db isn't installed 
		resourceState = STATE_UNINSTALLED;
		
		initializeGlobalResources();
		
		if(dbState != STATE_UNINSTALLED) {
			CursorFactory factory = new CommCareDBCursorFactory();
			database = new CommCareOpenHelper(this, factory).getWritableDatabase();
		}
	}
	
	public void logIn(byte[] symetricKey) {
		this.key = symetricKey;
		
		if(database != null && database.isOpen()) {
			database.close();
		}
		
		CursorFactory factory = new CommCareDBCursorFactory(this.encryptedModels(), this.getDecrypter());
		database = new CommCareOpenHelper(this, factory).getWritableDatabase();
	}
	
	public Cipher getEncrypter() {
		SecretKeySpec spec = new SecretKeySpec(key, "AES");
		
		try{
			Cipher encrypter = Cipher.getInstance("AES");
			encrypter.init(Cipher.ENCRYPT_MODE, spec);
			return encrypter;
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	public Cipher getDecrypter() {
		try {
			SecretKeySpec spec = new SecretKeySpec(key, "AES");
			Cipher decrypter = Cipher.getInstance("AES");
			decrypter.init(Cipher.DECRYPT_MODE, spec);
		
			return decrypter;
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	public SecretKey createNewSymetricKey() {
		return CryptUtil.generateSymetricKey(CryptUtil.uniqueSeedFromSecureStatic(key));
	}
	
	
	public int versionCode() {
		try {
			PackageManager pm = this.getPackageManager();
			PackageInfo pi = pm.getPackageInfo("org.commcare.android", 0);
			return pi.versionCode;
		} catch(NameNotFoundException e) {
			throw new RuntimeException("Android package name not available.");
		}
	}
    
	public int[] getCommCareVersion() {
		return this.getResources().getIntArray(R.array.commcare_version);
	}

	public AndroidCommCarePlatform getCommCarePlatform() {
		return platform;
	}
	
	public int getDatabaseState() {
		return dbState;
	}
	public int getAppResourceState() {
		return resourceState;
	}
	
	public void initializeGlobalResources() {
		dbState = initDb();
		if(dbState != STATE_UNINSTALLED) {
			resourceState = initResources();
		}
	}
	
	public String getPhoneId() {
		TelephonyManager manager = (TelephonyManager)this.getSystemService(TELEPHONY_SERVICE);
		String imei = manager.getDeviceId();
		return imei;
	}
	
	public SharedPreferences preferences() {
		return appPreferences;
	}
	
	public String fsPath(String relative) {
		return storageRoot() + relative;
	}
    
    private void createPaths() {
    	String[] paths = new String[] {GlobalConstants.FILE_CC_ROOT, GlobalConstants.FILE_CC_INSTALL, GlobalConstants.FILE_CC_UPGRADE, GlobalConstants.FILE_CC_CACHE, GlobalConstants.FILE_CC_SAVED, GlobalConstants.FILE_CC_PROCESSED, GlobalConstants.FILE_CC_INCOMPLETE};
    	for(String path : paths) {
    		File f = new File(fsPath(path));
    		if(!f.exists()) {
    			f.mkdirs();
    		}
    	}
    }
	
	private void setRoots() {
		JavaHttpRoot http = new JavaHttpRoot();
		
		JavaFileRoot file = new JavaFileRoot(storageRoot());

		ReferenceManager._().addReferenceFactory(http);
		ReferenceManager._().addReferenceFactory(file);
		ReferenceManager._().addRootTranslator(new RootTranslator("jr://resource/",GlobalConstants.RESOURCE_PATH));
	}
	
	private String storageRoot() {
		//We used to use the external storage directory, which apparently _DESTROYS YOUR FILES ON UPGRADE_. Do not
		//switch back until we can figure out how to correct for that.
		return getApplicationContext().getFilesDir().toString();
		//return Environment.getExternalStorageDirectory().toString() + "/Android/data/"+ this.getPackageName() +"/files/";
	}
	
	private int initResources() {
		try {
			//Now, we need to identify the state of the application resources
			AndroidCommCarePlatform platform = CommCareApplication._().getCommCarePlatform(); 
			ResourceTable global = platform.getGlobalResourceTable();
			//TODO: This, but better.
			Resource profile = global.getResourceWithId("commcare-application-profile");
			if(profile != null && profile.getStatus() == Resource.RESOURCE_STATUS_INSTALLED) {
				platform.initialize(global);
				Localization.setLocale(Localization.getGlobalLocalizerAdvanced().getAvailableLocales()[0]);
				return STATE_READY;
			} else{
				return STATE_UNINSTALLED;
			}
		}
		catch(Exception e) {
			Log.i("FAILURE", "Problem with loading");
			Log.i("FAILURE", "E: " + e.getMessage());
			e.printStackTrace();
			ExceptionReportTask ert = new ExceptionReportTask();
			ert.execute(e);
			return STATE_CORRUPTED;
		}
	}
	
	public void upgrade() {
		//Now, we need to identify the state of the application resources
		AndroidCommCarePlatform platform = CommCareApplication._().getCommCarePlatform(); 
		ResourceTable global = platform.getGlobalResourceTable();
		//TODO: This, but better.
		Resource profile = global.getResourceWithId("commcare-application-profile");
		if(profile != null && profile.getStatus() == Resource.RESOURCE_STATUS_INSTALLED) {
			try {
				platform.upgrade(global, platform.getUpgradeResourceTable(), appPreferences.getString("default_app_server", getString(R.string.default_app_server)));
			} catch (UnfullfilledRequirementsException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else{
			//App isn't properly installed/prepared yet.
		}
		platform.initialize(global);
	}

	private int initDb() {
		SQLiteDatabase database;
		try {
			database = new CommCareOpenHelper(this).getWritableDatabase();
			database.close();
			return STATE_READY;
		} catch(SQLiteException e) {
			//Only thrown in DB isn't there
			return STATE_UNINSTALLED;
		}
	}
	
	public <T extends Persistable> SqlIndexedStorageUtility<T> getStorage(String storage, Class<T> c) {
		DbHelper helper;
		if(key != null) {
			helper = new DbHelper(this.getApplicationContext(), getEncrypter()) {
				@Override
				public SQLiteDatabase getHandle() {
					CursorFactory factory = new CommCareDBCursorFactory(encryptedModels(), getDecrypter());
					if(database == null || !database.isOpen()) {
						database = (new CommCareOpenHelper(this.c, factory)).getWritableDatabase();
					}
					return database;
				}
				
			};
		} else {
			helper = new DbHelper(this.getApplicationContext()) {
				@Override
				public SQLiteDatabase getHandle() {
					CursorFactory factory = new CommCareDBCursorFactory();
					if(database == null || !database.isOpen()) {
						database = new CommCareOpenHelper(this.c, factory).getWritableDatabase();
					}
					return database;
				}
				
			};
		}
		return new SqlIndexedStorageUtility<T>(storage, c, helper);
	}
	
	private Hashtable<String, EncryptedModel> encryptedModels() {
		Hashtable<String, EncryptedModel> models = new Hashtable<String, EncryptedModel>();
		models.put(Case.STORAGE_KEY, new Case());
		models.put(Referral.STORAGE_KEY, new Case());
		models.put(FormRecord.STORAGE_KEY, new Case());
		return models;
	}


	@Override
	public void onLowMemory() {
		super.onLowMemory();
	}

	@Override
	public void onTerminate() {
		super.onTerminate();
	}

	public static CommCareApplication _() {
		return app;
	}

	/**
	 * This method is a shortcut to wiping out the profile/suite/xforms/etc, and 
	 * regathering the application resources in the case of something bad happening.
	 * 
	 * It may mess up the app, so it shouldn't be called upon trivially.
	 */
	public boolean resetApplicationResources() {
		ResourceTable global = platform.getGlobalResourceTable();
		global.clear();
		String profile = appPreferences.getString("default_app_server", this.getString(R.string.default_app_server));
		try {
			platform.init(profile, global, false);
			return true;
		} catch (UnfullfilledRequirementsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		} catch (UnresolvedResourceException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * This method wipes out all local user data (users, referrals, etc) but leaves
	 * application resources in place.
	 * 
	 * It makes no attempt to make sure this is a safe operation when called, so
	 * it shouldn't be used lightly.
	 */
	public void clearUserData() {
		//First clear anything that will require the user's key, since we're going to wipe it out!
		getStorage(Referral.STORAGE_KEY, Referral.class).removeAll();
		getStorage(Case.STORAGE_KEY, Case.class).removeAll();
		
		//TODO: We should really be wiping out the _stored_ instances here, too
		getStorage(FormRecord.STORAGE_KEY, FormRecord.class).removeAll();
		
		//Now we wipe out the user entirely
		getStorage(User.STORAGE_KEY, User.class).removeAll();
		
		//We should be clear of anything that requires the storage key anymore, so we can now dump
		//user credentials.
		platform.logout();
		
		//Should be good to go. The app'll log us out now that there's no user details in memory
	}

	public String getCurrentVersionString() {
		PackageManager pm = this.getPackageManager();
		PackageInfo pi;
		try {
			pi = pm.getPackageInfo("org.commcare.android", 0);
		} catch (NameNotFoundException e) {
			e.printStackTrace();
			return "ERROR! Incorrect package version requested";
		}
		int[] versions = this.getCommCareVersion();
		String ccv = "";
		for(int vn: versions) {
			if(ccv != "") {
				ccv +=".";
			}
			ccv += vn;
		}
		
		String buildDate = getString(R.string.app_build_date);
		String buildNumber = getString(R.string.app_build_number);
		
		return "CommCare ODK, version \"" + pi.versionName + "\"(" + pi.versionCode+ "). CommCare Version " +  ccv + ". Build #" + buildNumber + ", built on: " + buildDate;
		
	}
}
