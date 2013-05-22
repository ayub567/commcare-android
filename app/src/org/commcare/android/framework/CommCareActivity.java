/**
 * 
 */
package org.commcare.android.framework;

import java.lang.reflect.Field;
import java.util.Vector;

import org.commcare.android.database.user.models.ACase;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.tasks.templates.CommCareTaskConnector;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.activities.CommCareHomeActivity;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.util.CommCareSession;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.NoLocalizedTextException;

import android.annotation.TargetApi;
import android.app.ActionBar.LayoutParams;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.HorizontalScrollView;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * Base class for CommCareActivities to simplify 
 * common localization and workflow tasks
 * 
 * @author ctsims
 *
 */
public abstract class CommCareActivity<R> extends Activity implements CommCareTaskConnector<R> {
	
	protected final static int DIALOG_PROGRESS = 32;
	protected final static String DIALOG_TEXT = "cca_dialog_text";
	
	CommCareTask currentTask;

	@Override
	@TargetApi(14)
	protected void onCreate(Bundle savedInstanceState) {
		//TODO: We can really handle much of this framework without needing to 
		//be a superclass.
		super.onCreate(savedInstanceState);
		if(this.getClass().isAnnotationPresent(ManagedUi.class)) {
			this.setContentView(this.getClass().getAnnotation(ManagedUi.class).value());
			loadFields();
		}
	    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
	    	getActionBar().setDisplayShowCustomEnabled(true);

	    	//getActionBar().setDisplayShowHomeEnabled(true);
			//getActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
	    }
	    
	    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
	    	//getActionBar().setHomeButtonEnabled(true);
	    }
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	        case android.R.id.home:
	        	try { 
	        		CommCareApplication._().getCurrentSession().clearState();
	        	} catch(SessionUnavailableException sue) {
	        		// probably won't go anywhere with this
	        	}
	            // app icon in action bar clicked; go home
	            Intent intent = new Intent(this, CommCareHomeActivity.class);
	            startActivity(intent);
	            return true;
	        default:
	            return super.onOptionsItemSelected(item);
	    }
	}
	
	private void loadFields() {
		CommCareActivity oldActivity = null;
		Object o = this.getLastNonConfigurationInstance();
		if(o instanceof CommCareActivity) {
			oldActivity = (CommCareActivity)o;
		}
		Class c = this.getClass();
		for(Field f : c.getDeclaredFields()) {
			if(f.isAnnotationPresent(UiElement.class)) {
				UiElement element = f.getAnnotation(UiElement.class);
				try{
					f.setAccessible(true);
					
					try {
						View v = this.findViewById(element.value());
						f.set(this, v);
						
						if(oldActivity != null) {
							View oldView = (View)f.get(oldActivity);
							if(oldView != null) {
								if(v instanceof TextView) {
									((TextView)v).setText(((TextView)oldView).getText());
								}
								v.setVisibility(oldView.getVisibility());
								continue;
							}
						}
						
						if(element.locale() != "") {
							if(v instanceof TextView) {
								((TextView)v).setText(Localization.get(element.locale()));
							} else {
								throw new RuntimeException("Can't set the text for a " + v.getClass().getName() + " View!");
							}
						}
					} catch (IllegalArgumentException e) {
						e.printStackTrace();
						throw new RuntimeException("Bad Object type for field " + f.getName());
					} catch (IllegalAccessException e) {
						throw new RuntimeException("Couldn't access the activity field for some reason");
					}
				} finally {
					f.setAccessible(false);
				}
			}
		}
	}
	
	protected boolean isTopNavEnabled() {
		return false;
	}
	

	/* (non-Javadoc)
	 * @see android.app.Activity#onResume()
	 */
	@Override
	@TargetApi(11)
	protected void onResume() {
		super.onResume();
	    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
//	    	TextView tv = new TextView(this);
//	    	tv.setText(getTitle(this, getActivityTitle()));
	    	getActionBar().setCustomView(getTitleView(this, getActivityTitle()), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
	    	this.setTitle("");
	    	getActionBar().setDisplayShowHomeEnabled(false);
	    } else {
	    	this.setTitle(getTitle(this, getActivityTitle()));
	    }
		
		Object o = this.getLastNonConfigurationInstance();
		if(o != null && o instanceof CommCareActivity) {
			//Time to reconnect with our roots
			CommCareActivity a = (CommCareActivity)o;
			this.connectTask(a.currentTask);
		}
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onDestroy()
	 */
	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onRetainNonConfigurationInstance()
	 */
	@Override
	public final Object onRetainNonConfigurationInstance() {
		return this;
	}
	
	
	protected void updateProgress(int taskId, String updateText) {
		Bundle b = new Bundle();
		b.putString(DIALOG_TEXT, updateText);
		this.showDialog(taskId, b);
	}

	/* (non-Javadoc)
	 * @see org.commcare.android.tasks.templates.CommCareTaskConnector#connectTask(org.commcare.android.tasks.templates.CommCareTask)
	 */
	@Override
	public <A, B, C> void connectTask(CommCareTask<A, B, C, R> task) {
		currentTask = task;
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.commcare.android.tasks.templates.CommCareTaskConnector#getReceiver()
	 */
	@Override
	public R getReceiver() {
		return (R)this;
	}
	
	/**
	 * Override these to control the UI for your task
	 */

	/* (non-Javadoc)
	 * @see org.commcare.android.tasks.templates.CommCareTaskConnector#startBlockingForTask()
	 */
	@Override
	public void startBlockingForTask(int id) {
		this.showDialog(id);
	}

	/* (non-Javadoc)
	 * @see org.commcare.android.tasks.templates.CommCareTaskConnector#stopBlockingForTask()
	 */
	@Override
	public void stopBlockingForTask(int id) {
		this.dismissDialog(id);
	}
	
    
    /* (non-Javadoc)
	 * @see android.app.Activity#onPrepareDialog(int, android.app.Dialog, android.os.Bundle)
	 */
	@Override
	protected void onPrepareDialog(int id, Dialog dialog, Bundle args) {
		super.onPrepareDialog(id, dialog, args);
		if(dialog instanceof ProgressDialog) {
			if(args != null && args.containsKey(CommCareActivity.DIALOG_TEXT)) {
				((ProgressDialog)dialog).setMessage(args.getString(CommCareActivity.DIALOG_TEXT));
			}
		}
	}
	

	/* (non-Javadoc)
	 * @see org.commcare.android.tasks.templates.CommCareTaskConnector#taskCancelled(int)
	 */
	@Override
	public void taskCancelled(int id) {
		// TODO Auto-generated method stub
		
	}
	
	public void TransplantStyle(TextView target, int resource) {
		//get styles from here
		TextView tv = (TextView)View.inflate(this, resource, null);
		int[] padding = {target.getPaddingLeft(), target.getPaddingTop(), target.getPaddingRight(),target.getPaddingBottom() };

		target.setTextColor(tv.getTextColors().getDefaultColor());
		target.setTypeface(tv.getTypeface());
		target.setBackgroundDrawable(tv.getBackground());
		target.setPadding(padding[0], padding[1], padding[2], padding[3]);
	}
	
	public String getActivityTitle() {
		return null;
	}
	
	public static String getTopLevelTitleName(Context c) {
		String topLevel = null;
		try {
			topLevel = Localization.get("app.display.name");
			return topLevel;
		} catch(NoLocalizedTextException nlte) {
        	//nothing, app display name is optional for now.
        }
		
		return c.getString(org.commcare.dalvik.R.string.title_bar_name);
	}
	
	public static String getTitle(Context c, String local) {
		String topLevel = getTopLevelTitleName(c);
		
		String[] stepTitles = new String[0];
		try {
			stepTitles = CommCareApplication._().getCurrentSession().getHeaderTitles();
			
			//See if we can insert any case hacks
			int i = 0;
			for(String[] step : CommCareApplication._().getCurrentSession().getSteps()){
				try {
				if(CommCareSession.STATE_DATUM_VAL.equals(step[0])) {
					//Haaack
					if("case_id".equals(step[1])) {
						ACase foundCase = CommCareApplication._().getUserStorage(ACase.STORAGE_KEY, ACase.class).getRecordForValue(ACase.INDEX_CASE_ID, step[2]);
						stepTitles[i] = Localization.get("title.datum.wrapper", new String[] { foundCase.getName()});
					}
				}
				} catch(Exception e) {
					//TODO: Your error handling is bad and you should feel bad
				}
				++i;
			}
			
		} catch(SessionUnavailableException sue) {
			
		}
		
		String returnValue = topLevel;
		
		for(String title : stepTitles) {
			if(title != null) {
				returnValue += " > " + title;
			}
		}
		
		if(local != null) {
			returnValue += " > " + local;
		}
		return returnValue;
	}
	
	public View getTitleView(Context c, String local) {
		RelativeLayout layout = new RelativeLayout(c);
		layout.setGravity(Gravity.CENTER_VERTICAL);
		
		// we have to do this walk backwards, actually
		
		String topLevel = getTopLevelTitleName(c);
		LayoutInflater li = this.getLayoutInflater();
		
		int currentId = -1;
		
		int newId = addElementToTitle(li, layout, local, org.commcare.dalvik.R.layout.component_title_breadcrumb, currentId, null);
		
		if(newId != -1) { currentId = newId;}
		
		String[] stepTitles = new String[0];
		try {
			stepTitles = CommCareApplication._().getCurrentSession().getHeaderTitles();
			
			//See if we can insert any case hacks
			Vector<String[]> v = CommCareApplication._().getCurrentSession().getSteps();
			for(int i = v.size() -1 ; i >= 0; i--){
				String[] step = v.elementAt(i);
				final int currentStep = i;
				final int currentStepSize = v.size();
			
				OnClickListener stepBackListener = new OnClickListener() {

					@Override
					public void onClick(View arg0) {
						
						int stepsToTake = currentStepSize - currentStep - 1;
						
						try{
							for(int i = 0 ; i < stepsToTake ; i++) {
								CommCareApplication._().getCurrentSession().stepBack();
								int currentStepSize = CommCareApplication._().getCurrentSession().getSteps().size();
								
								//Take at _most_ currentSteps back, or stop when we've reached
								//current step minus 1
								if(currentStepSize == 0  || currentStepSize < currentStep) {
									break;
								}
							}
							
							CommCareActivity.this.finish();
						} catch(SessionUnavailableException sue) {
							
						}
					}
					
				};
			
				

				try {
				if(CommCareSession.STATE_DATUM_VAL.equals(step[0])) {
					//Haaack
					if("case_id".equals(step[1])) {
						ACase foundCase = CommCareApplication._().getUserStorage(ACase.STORAGE_KEY, ACase.class).getRecordForValue(ACase.INDEX_CASE_ID, step[2]);
						stepTitles[i] = foundCase.getName();
						newId = addElementToTitle(li, layout, stepTitles[i], org.commcare.dalvik.R.layout.component_title_breadcrumb_case, currentId, stepBackListener);
						if(newId != -1) { currentId = newId;}
						continue;
					}
				}
				} catch(Exception e) {
					//TODO: Your error handling is bad and you should feel bad
				}
				newId = addElementToTitle(li, layout, stepTitles[i], org.commcare.dalvik.R.layout.component_title_breadcrumb, currentId, stepBackListener);
				if(newId != -1) { currentId = newId;}
			}
			
		} catch(SessionUnavailableException sue) {
			
		}
		
		addElementToTitle(li, layout, topLevel, org.commcare.dalvik.R.layout.component_title_breadcrumb, currentId, new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				try{
					CommCareApplication._().getCurrentSession().clearState();
				} catch(SessionUnavailableException sue) {
					
				}
				
				CommCareActivity.this.finish();
			}
			
		});
		
		//Add the app icon
		TextView iconBearer = ((TextView)layout.getChildAt(layout.getChildCount() - 1));
		
		iconBearer.setCompoundDrawablesWithIntrinsicBounds(org.commcare.dalvik.R.drawable.icon,0,0,0);
		iconBearer.setCompoundDrawablePadding(this.getResources().getDimensionPixelSize(org.commcare.dalvik.R.dimen.title_logo_pad));
		
		
		HorizontalScrollView scroller = new HorizontalScrollView(this);
		scroller.addView(layout, new HorizontalScrollView.LayoutParams(HorizontalScrollView.LayoutParams.WRAP_CONTENT,HorizontalScrollView.LayoutParams.MATCH_PARENT));
		scroller.setFillViewport(true);
		
		return scroller;
	}
	
	private int addElementToTitle(LayoutInflater inflater, RelativeLayout title, String element, int type, int peer, OnClickListener action) {
		int newViewId = org.commcare.dalvik.R.id.component_title_breadcrumb_text + title.getChildCount() + 1;
		if(element != null) {
			View titleBreadcrumb = inflater.inflate(type, title, true);
			
			TextView text = (TextView)titleBreadcrumb.findViewById(org.commcare.dalvik.R.id.component_title_breadcrumb_text);
			
			if(action != null && isTopNavEnabled()) {
				text.setOnClickListener(action);
			}
			
			text.setText(element);
			//Is there a "random ID" or something we can use for this?
			text.setId(newViewId);
			
			if(peer != -1) {
				View peerView = title.findViewById(peer);
				
				RelativeLayout.LayoutParams layout = (RelativeLayout.LayoutParams)peerView.getLayoutParams();
				layout.addRule(RelativeLayout.RIGHT_OF, newViewId);
			}
			return newViewId;
		}
		return -1;
	}
}
