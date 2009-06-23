/* 
 * Copyright (C) 2008 OpenIntents.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openintents.countdown.activity;

import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.List;

import org.openintents.countdown.AlarmService;
import org.openintents.countdown.R;
import org.openintents.countdown.R.id;
import org.openintents.countdown.R.layout;
import org.openintents.countdown.R.string;
import org.openintents.countdown.activitypicker.DialogHostingActivity;
import org.openintents.countdown.db.Countdown.Durations;
import org.openintents.countdown.util.CountdownUtils;
import org.openintents.countdown.util.NotificationState;
import org.openintents.countdown.widget.DurationPicker;
import org.openintents.intents.AutomationIntents;
import org.openintents.utils.DateTimeFormater;
import org.openintents.utils.SDKVersion;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TimePicker;

/**
 * A generic activity for editing a note in a database.  This can be used
 * either to simply view a note {@link Intent#ACTION_VIEW}, view and edit a note
 * {@link Intent#ACTION_EDIT}, or create a new note {@link Intent#ACTION_INSERT}.  
 */
public class CountdownEditorActivity extends Activity {
    private static final String TAG = "CountdownEditorActivity";
    private static final boolean debug = true;
    
    // This is our state data that is stored when freezing.
    private static final String ORIGINAL_CONTENT = "origContent";

    // Identifiers for our menu items.
    private static final int REVERT_ID = Menu.FIRST;
    private static final int DISCARD_ID = Menu.FIRST + 1;
    private static final int MENU_DELETE = Menu.FIRST + 2;
    private static final int MENU_PICK_RINGTONE = Menu.FIRST + 3;
    private static final int MENU_CHANGE_COUNTDOWN_MODE = Menu.FIRST + 4;
    private static final int MENU_SET_AUTOMATION = Menu.FIRST + 5;

    // The different distinct states the activity can be run in.
    private static final int STATE_EDIT = 0;
    private static final int STATE_INSERT = 1;

    private static final int MSG_UPDATE_DISPLAY = 1;
    
    static final int REQUEST_CODE_RINGTONE = 1;
	static final int REQUEST_CODE_PICK_AUTOMATION_TASK = 2;
    static final int REQUEST_CODE_PICK_SHORTCUT = 3;
	static final int REQUEST_CODE_SET_AUTOMATION_TASK = 4;
    static final int REQUEST_CODE_SET_SHORTCUT = 5;
    static final int REQUEST_CODE_SET_APPLICATION = 6;

	static final int DIALOG_ID_SET_DATE = 1;
	static final int DIALOG_ID_SET_TIME = 2;
    static final int DIALOG_CREATE_SHORTCUT = 3;
    
    private int mState;
    private Uri mUri;
    private Cursor mCursor;
    private EditText mText;
    private String mOriginalContent;
    
	private long mDuration;
	private long mDeadline;
	private long mUserDeadline;
	private long mUserDeadlineTemporary;
	private long mDurationOld;
	private boolean mDurationModified;
	private boolean mUserDeadlineModified;
    
    private DurationPicker mDurationPicker;
    private Button mStart;
    private Button mStop;
    private Button mModify;
    private Button mCont;
    private Button mDismiss;
    private TextView mCountdownView;
    
    private LinearLayout mDateSetter;
	private Button mSetDate;
	private Button mSetTime;
    
    private CheckBox mRingtoneView;
    private CheckBox mVibrateView;
    private CheckBox mAutomateCheckBox;
    private Button mAutomateButton;
    private ImageView mAutomateImage;
    private TextView mAutomateTextView;
    
    private long mRing;
    private Uri mRingtoneUri;
    private long mVibrate;
    private int mRingtoneType;
    private long mAutomate;
    private Intent mAutomateIntent;
    private String mAutomateText;
    
    private long UNCHECKED = 0;
    private long CHECKED = 1;
    
    // private boolean mStartCountdown;
    
    private int mCountdownState;
    private static final int STATE_COUNTDOWN_IDLE = 1;
    private static final int STATE_COUNTDOWN_RUNNING = 2;
    private static final int STATE_COUNTDOWN_MODIFY = 3;
    private static final int STATE_COUNTDOWN_DISMISS = 4;
    
    /**
     * Whether to set a specific date (deadline) or
     * a duration to set the countdown.
     */
    private int mCountdownMode;
    private static final int MODE_SET_DATE = 11;
    private static final int MODE_SET_DURATION = 12;
    
	private Calendar mCalendar = Calendar.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		CountdownUtils.setLocalizedStrings(this);
		
        final Intent intent = getIntent();

        mCountdownState = STATE_COUNTDOWN_IDLE;
        mCountdownMode = MODE_SET_DURATION;
        mRingtoneType = RingtoneManager.TYPE_ALL;
        mAutomate = UNCHECKED;
        
        // Do some setup based on the action being performed.

        final String action = intent.getAction();
        
        if (Intent.ACTION_EDIT.equals(action) || Intent.ACTION_VIEW.equals(action)) {
            // Requested to edit: set that state, and the data being edited.
            mState = STATE_EDIT;
            mUri = intent.getData();
            
            cancelThisNotification();
            
        } else if (Intent.ACTION_INSERT.equals(action)) {
            // Requested to insert: set that state, and create a new entry
            // in the container.
            mState = STATE_INSERT;
            
            // Prepare default values
            ContentValues cv = new ContentValues();
            cv.put(Durations.RING, CHECKED);
            cv.put(Durations.RINGTONE, RingtoneManager.getDefaultUri(mRingtoneType).toString());
            cv.put(Durations.VIBRATE, CHECKED);
            

            if (intent.hasExtra(AutomationIntents.EXTRA_ACTIVITY_INTENT)) {
            	// Set default action from extra:
            	// (this has been set in SetCountdownActivity)
            	cv.put(Durations.AUTOMATE, CHECKED);
            	mAutomateIntent = (Intent) intent.getParcelableExtra(AutomationIntents.EXTRA_ACTIVITY_INTENT);
            	cv.put(Durations.AUTOMATE_INTENT, mAutomateIntent.toURI());
            }
            
            mUri = getContentResolver().insert(intent.getData(), cv);
            intent.setAction(Intent.ACTION_EDIT);
            intent.setData(mUri);
            setIntent(intent);

            // If we were unable to create a new note, then just finish
            // this activity.  A RESULT_CANCELED will be sent back to the
            // original activity if they requested a result.
            if (mUri == null) {
                Log.e(TAG, "Failed to insert new note into " + getIntent().getData());
                finish();
                return;
            }

            // The new entry was created, so assume all will end well and
            // set the result to be returned.
            setResult(RESULT_OK, (new Intent()).setAction(mUri.toString()));

        } else {
            // Whoops, unknown action!  Bail.
            Log.e(TAG, "Unknown action, exiting");
            finish();
            return;
        }

        // Set the layout for this activity.  You can find it in res/layout/note_editor.xml
        setContentView(R.layout.countdown_editor);
        
        // The text view for our note, identified by its ID in the XML file.
        mText = (EditText) findViewById(R.id.title);
        
        mDurationPicker = (DurationPicker) findViewById(R.id.durationpicker);
        
        // Reset to 0 to show 2-digit numbers.
        mDurationPicker.setCurrentMinute(0);
        mDurationPicker.setCurrentSecond(0);
        
        mCountdownView = (TextView) findViewById(R.id.countdown);
        
        mDateSetter = (LinearLayout) findViewById(R.id.datesetter);

		mSetDate = (Button) findViewById(R.id.set_date);
		mSetDate.setOnClickListener(new View.OnClickListener() {

			public void onClick(View v) {
				showDialog(DIALOG_ID_SET_DATE);
			}
		});

		mSetTime = (Button) findViewById(R.id.set_time);
		mSetTime.setOnClickListener(new View.OnClickListener() {

			public void onClick(View v) {
				showDialog(DIALOG_ID_SET_TIME);
			}
		});

        mStart = (Button) findViewById(R.id.start);
        mStart.setOnClickListener(new View.OnClickListener() {

			
			public void onClick(View arg0) {
				start();
			}
        	
        });
        
        mStop = (Button) findViewById(R.id.stop);
        mStop.setOnClickListener(new View.OnClickListener() {

			
			public void onClick(View arg0) {
				stop();
			}
        	
        });

        mModify = (Button) findViewById(R.id.modify);
        mModify.setOnClickListener(new View.OnClickListener() {

			
			public void onClick(View arg0) {
				modify();
			}
        	
        });

        mCont = (Button) findViewById(R.id.cont);
        mCont.setOnClickListener(new View.OnClickListener() {

			
			public void onClick(View arg0) {
				cont();
			}
        	
        });

        mDismiss = (Button) findViewById(R.id.dismiss);
        mDismiss.setOnClickListener(new View.OnClickListener() {

			
			public void onClick(View arg0) {
				dismiss();
			}
        	
        });
        
        mRingtoneView = (CheckBox) findViewById(R.id.ringtone);
        
        mRingtoneView.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

			
			public void onCheckedChanged(CompoundButton view, boolean checked) {
				setRing(checked);
			}
        	
        });
        
        mVibrateView = (CheckBox) findViewById(R.id.vibrate);

        mVibrateView.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

			
			public void onCheckedChanged(CompoundButton view, boolean checked) {
				setVibrate(checked);
			}

        	
        });

        mAutomateCheckBox = (CheckBox) findViewById(R.id.automate_checkbox);

        mAutomateCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

			
			public void onCheckedChanged(CompoundButton view, boolean checked) {
				setAutomate(checked);
			}

        	
        });
        
        mAutomateButton = (Button) findViewById(R.id.automate_button);

        mAutomateButton.setOnClickListener(new View.OnClickListener() {

			
			public void onClick(View arg0) {
				startAutomateTest();
			}
        	
        });
        
        mAutomateTextView = (TextView) findViewById(R.id.automate_text);
        
        mAutomateImage = (ImageView) findViewById(R.id.automate_image);
        
        // Extend clickable area of checkbox to image and description:
        View.OnClickListener listener = new View.OnClickListener() {
			public void onClick(View arg0) {
				mAutomateCheckBox.performClick();
			}
        };
        
        mAutomateImage.setOnClickListener(listener);
        mAutomateTextView.setOnClickListener(listener);
        
        // Get the countdown!
        mCursor = managedQuery(mUri, Durations.PROJECTION, null, null, null);

        // If an instance of this activity had previously stopped, we can
        // get the original text it started with.
        if (savedInstanceState != null) {
            mOriginalContent = savedInstanceState.getString(ORIGINAL_CONTENT);
        }
        
        
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.v(TAG, "onResume()");

        IntentFilter filter = new IntentFilter(NotificationState.ACTION_NOTIFICATION_STATE_CHANGED);
        registerReceiver(mReceiver, filter);

		DateTimeFormater.getFormatFromPreferences(this);
        
        // If we didn't have any trouble retrieving the data, it is now
        // time to get at the stuff.
        if (mCursor != null) {
            // Make sure we are at the one and only row in the cursor.
            mCursor.moveToFirst();

            // Modify our overall title depending on the mode we are running in.
            if (mState == STATE_EDIT) {
                setTitle(getText(R.string.title_edit));
            } else if (mState == STATE_INSERT) {
                setTitle(getText(R.string.title_create));
            }

            // This is a little tricky: we may be resumed after previously being
            // paused/stopped.  We want to put the new text in the text view,
            // but leave the user where they were (retain the cursor position
            // etc).  This version of setText does that for us.
            String title = mCursor.getString(mCursor.getColumnIndexOrThrow(Durations.TITLE));
            mText.setTextKeepState(title);
            
            // If we hadn't previously retrieved the original text, do so
            // now.  This allows the user to revert their changes.
            if (mOriginalContent == null) {
                mOriginalContent = title;
            }
            
            mDuration = mCursor.getLong(mCursor.getColumnIndexOrThrow(Durations.DURATION));
            mUserDeadline = mCursor.getLong(mCursor.getColumnIndexOrThrow(Durations.USER_DEADLINE_DATE));
            
            if (mUserDeadline > 0) {
            	mCountdownMode = MODE_SET_DATE;
            	mUserDeadlineTemporary = mUserDeadline;
            } else {
            	mCountdownMode = MODE_SET_DURATION;
                mDurationPicker.setDuration(mDuration);
            }
    		
            mDeadline = mCursor.getLong(mCursor.getColumnIndexOrThrow(Durations.DEADLINE_DATE));
            
            mRing = mCursor.getLong(mCursor.getColumnIndexOrThrow(Durations.RING));
            //Log.i(TAG, "onResume Ring: " + mRing);

            String uristring = mCursor.getString(mCursor.getColumnIndexOrThrow(Durations.RINGTONE));
            //Log.i(TAG, "onResume Ringtone: " + uristring);
            if (uristring != null) {
            	mRingtoneUri = Uri.parse(uristring);
            } else {
            	mRingtoneUri = null;
            }

            mAutomate = mCursor.getLong(mCursor.getColumnIndexOrThrow(Durations.AUTOMATE));
            //Log.i(TAG, "onResume Ring: " + mRing);

            uristring = mCursor.getString(mCursor.getColumnIndexOrThrow(Durations.AUTOMATE_INTENT));
            //Log.i(TAG, "onResume Ringtone: " + uristring);
            if (uristring != null) {
            	try {
					mAutomateIntent = Intent.getIntent(uristring);
				} catch (URISyntaxException e) {
					mAutomateIntent = null;
				}
            } else {
            	mAutomateIntent = null;
            }

            mAutomateText = mCursor.getString(mCursor.getColumnIndexOrThrow(Durations.AUTOMATE_TEXT));
            
            mVibrate = mCursor.getLong(mCursor.getColumnIndexOrThrow(Durations.VIBRATE));

            mDeadline = mCursor.getLong(mCursor.getColumnIndexOrThrow(Durations.DEADLINE_DATE));
            
            updateCheckboxes();
            updateViews();
    		
        } else {
            setTitle(getText(R.string.error_title));
            mText.setText(getText(R.string.error_message));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Save away the original text, so we still have it if the activity
        // needs to be killed while paused.
        outState.putString(ORIGINAL_CONTENT, mOriginalContent);
    }

    @Override
    protected void onPause() {
        super.onPause();

        unregisterReceiver(mReceiver);

        // The user is going somewhere else, so make sure their current
        // changes are safely saved away in the provider.  We don't need
        // to do this if only editing.
        if (mCursor != null) {
            
            ContentValues values = new ContentValues();

            long now = System.currentTimeMillis();
            
            // Bump the modification time to now.
            values.put(Durations.MODIFIED_DATE, now);

            String text = mText.getText().toString();
            values.put(Durations.TITLE, text);
            

    		// Set the current time.
            if (mCountdownMode == MODE_SET_DURATION) {
            	mDuration = mDurationPicker.getDuration();
            	mUserDeadline = 0;
            } else {
            	mDuration = 0;
            	mUserDeadline = mUserDeadlineTemporary;
            }
    		values.put(Durations.DURATION, mDuration);
    		values.put(Durations.USER_DEADLINE_DATE, mUserDeadline);
    		
    		//if (mStartCountdown) {
        		values.put(Durations.DEADLINE_DATE, mDeadline);
        		
    		//}
        		
        	values.put(Durations.RING, mRing);
        	Log.i(TAG, "Ring: " + mRing);
        	
        	String uristring = null;
        	if (mRingtoneUri != null) {
        		uristring = mRingtoneUri.toString();
        	}
        	values.put(Durations.RINGTONE, uristring);
        	Log.i(TAG, "Ringtone: " + uristring);
        	
        	values.put(Durations.VIBRATE, mVibrate);
        	Log.i(TAG, "Vibrate: " + mVibrate);
    		
            // Commit all of our changes to persistent storage. When the update completes
            // the content provider will notify the cursor of the change, which will
            // cause the UI to be updated.
            getContentResolver().update(mUri, values, null, null);
        }
        
        // Cancel notifications
        mHandler.removeMessages(MSG_UPDATE_DISPLAY);
        
        // Cancel sound or vibrator notifications
        cancelThisNotification();
    }
    
    

    @Override
	protected void onDestroy() {
		super.onDestroy();

	}

	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        menu.add(0, MENU_CHANGE_COUNTDOWN_MODE, 0, R.string.menu_set_date)
                .setShortcut('1', 'c')
                .setIcon(android.R.drawable.ic_menu_recent_history);
        
        menu.add(1, MENU_PICK_RINGTONE, 0, R.string.menu_pick_ringtone)
                .setShortcut('2', 'd')
                .setIcon(android.R.drawable.ic_menu_manage);
        
        menu.add(0, MENU_SET_AUTOMATION, 0, R.string.menu_set_action)
	        .setShortcut('3', 's')
	        .setIcon(android.R.drawable.ic_menu_set_as);

        // Build the menus that are shown when editing.
        if (mState == STATE_EDIT) {
            menu.add(0, MENU_DELETE, 0, R.string.menu_delete)
                    .setShortcut('3', 'd')
                    .setIcon(android.R.drawable.ic_menu_delete);
        

        // Build the menus that are shown when inserting.
        } else {
            menu.add(0, MENU_DELETE, 0, R.string.menu_delete)
                    .setShortcut('3', 'd')
                    .setIcon(android.R.drawable.ic_menu_delete);
        }

        // If we are working on a full note, then append to the
        // menu items for any other activities that can do stuff with it
        // as well.  This does a query on the system for any activities that
        // implement the ALTERNATIVE_ACTION for our data, adding a menu item
        // for each one that is found.
        Intent intent = new Intent(null, getIntent().getData());
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, CountdownEditorActivity.class), null, intent, 0, null);
    
        return true;
    }

	
    @Override
	public boolean onPrepareOptionsMenu(Menu menu) {
    	
    	MenuItem item = menu.findItem(MENU_CHANGE_COUNTDOWN_MODE);
    	if (mCountdownMode == MODE_SET_DURATION) {
    		item.setTitle(R.string.menu_set_date);
    	} else {
    		item.setTitle(R.string.menu_set_duration);
    	}
    	
    	if (mCountdownState == STATE_COUNTDOWN_IDLE || mCountdownState == STATE_COUNTDOWN_MODIFY) {
    		item.setEnabled(true);
    	} else {
    		item.setEnabled(false);
    	}
    	
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle all of the possible menu actions.
        switch (item.getItemId()) {
        case MENU_DELETE:
            deleteNote();
            finish();
            break;
        case DISCARD_ID:
            cancelNote();
            break;
        case REVERT_ID:
            cancelNote();
            break;
        case MENU_PICK_RINGTONE:
        	pickRingtone();
        	break;
        case MENU_CHANGE_COUNTDOWN_MODE:
        	changeCountdownMode();
        	break;
        case MENU_SET_AUTOMATION:
        	setAutomation();
        	break;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Take care of canceling work on a note.  Deletes the note if we
     * had created it, otherwise reverts to the original text.
     */
    private final void cancelNote() {
        if (mCursor != null) {
            if (mState == STATE_EDIT) {
                // Put the original note text back into the database
                mCursor.close();
                mCursor = null;
                ContentValues values = new ContentValues();
                //values.put(Durations.NOTE, mOriginalContent);
                getContentResolver().update(mUri, values, null, null);
            } else if (mState == STATE_INSERT) {
                // We inserted an empty note, make sure to delete it
                deleteNote();
            }
        }
        setResult(RESULT_CANCELED);
        finish();
    }

    /**
     * Take care of deleting a note.  Simply deletes the entry.
     */
    private final void deleteNote() {
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
            getContentResolver().delete(mUri, null, null);
            mText.setText("");
        }
    }
    
    private final void start() {
    	mCountdownState = STATE_COUNTDOWN_RUNNING;
		
    	long now = System.currentTimeMillis();
    	
    	if (mCountdownMode == MODE_SET_DURATION) {
    		mDuration = mDurationPicker.getDuration();
    		mUserDeadline = 0;
        	mDeadline = now + mDuration;
    	} else {
    		//mDuration = mDurationPicker.getDuration();
    		mDuration = 0;
    		mUserDeadline = mUserDeadlineTemporary;
        	mDeadline = mUserDeadlineTemporary;
        	mUserDeadlineModified = true;
    	}
    	//finish();

    	setAlarm(mDeadline);
    	
    	updateViews();
    }

    private final void stop() {
    	mCountdownState = STATE_COUNTDOWN_IDLE;
		
    	//mStartCountdown = false;
    	
    	//long now = System.currentTimeMillis();
        //mDuration = mDurationPicker.getDuration();
		
    	mDeadline = 0;
    	//finish();

    	cancelAlarm();
    	mHandler.removeMessages(MSG_UPDATE_DISPLAY);
    	
    	updateViews();
    }

    /**
     * Modify the current time.
     * Note that the countdown continues to run in the background.
     * One can see this by pressing the "back" button, then entering again.
     */
    private final void modify() {
    	mCountdownState = STATE_COUNTDOWN_MODIFY;
		
    	// Set current time to modify timer temporarily
    	long now = System.currentTimeMillis();
        
    	//if (mCountdownMode == MODE_SET_DURATION) {
	    	long temporaryDuration = mDeadline - now;
	    	
	    	if (temporaryDuration < 0) {
	    		temporaryDuration = 0;
	    	}
	    	
	    	
	    	mDurationPicker.setDuration(temporaryDuration);
    	//} else {
    		// nothing to change for 
    	//}
    	
    	mOriginalDuration = mDuration;
    	mOriginalUserDeadline = mUserDeadline;
    	
    	updateViews();
    }
    
    long mOriginalDuration;
    long mOriginalUserDeadline;

    private final void cont() {
    	mCountdownState = STATE_COUNTDOWN_RUNNING;
    	
    	long now = System.currentTimeMillis();
    	if (mCountdownMode == MODE_SET_DURATION) {
	        mDuration = mDurationPicker.getDuration();
			
	    	mDeadline = now + mDuration;
    	} else {
    		mDeadline = mUserDeadlineTemporary;
    	}
    	
    	// Set original duration
    	mDuration = mOriginalDuration;
    	mDurationPicker.setDuration(mDuration);
    	
    	if (mOriginalUserDeadline > 0) {
    		// If user was in User mode before, we modify the deadline:
    		mUserDeadline = mDeadline;
    		mUserDeadlineTemporary = mDeadline;
    		
    		// switch back to original mode
    		mCountdownMode = MODE_SET_DATE;
    	} else {
    		mCountdownMode = MODE_SET_DURATION;
    	}

    	cancelAlarm();
    	mHandler.removeMessages(MSG_UPDATE_DISPLAY);
    	
    	setAlarm(mDeadline);
    	updateViews();
    }
    
    private final void dismiss() {
    	mCountdownState = STATE_COUNTDOWN_IDLE;
    	
        cancelThisNotification();

    	updateViews();
    }
    
    private final void changeCountdownMode() {
		long now = System.currentTimeMillis();
		
    	if (mCountdownMode == MODE_SET_DURATION) {
    		mCountdownMode = MODE_SET_DATE;
    		
    		if (mDurationOld != mDurationPicker.getDuration()) {
    			mDurationModified = true;
    		}
    		
    		if (mDurationModified || mUserDeadlineTemporary <= 0) {
	    		long duration = mDurationPicker.getDuration();
	    		// Convert duration to date:
	    		mUserDeadlineTemporary = now + duration;
	    		mUserDeadlineModified = false;
    		}
    		
    	} else {
    		mCountdownMode = MODE_SET_DURATION;
    		
    		mDurationOld = mDurationPicker.getDuration();
    		
    		if (mUserDeadlineModified || mDurationOld <= 0) {
	    		// Convert date to duration:
	    		long duration = mUserDeadlineTemporary - now;
	    		if (duration < 0) {
	    			duration = 0;
	    		}
	
	            mDurationPicker.setDuration(duration);
    		}
    		mDurationModified = false;
    	}
    	
    	updateViews();
    }

	void setAutomation() {
		
        //Intent intent = new Intent(Intent.ACTION_PICK);
        //intent.setType("*/*");
        
//		Intent intent = new Intent(AutomationIntents.ACTION_EDIT_AUTOMATION_SETTINGS);
		
//		startActivityForResult(intent, REQUEST_CODE_SET_AUTOMATION);
		

        showDialog(DIALOG_CREATE_SHORTCUT);
	}

	void startAutomateTest() {
		if (mAutomateIntent != null) {
			try {
				Log.i(TAG, "Start intent: " + mAutomateIntent.toURI());
				//startActivity(mAutomateIntent);
				
				// For Settings:
				startActivityForResult(mAutomateIntent, REQUEST_CODE_PICK_AUTOMATION_TASK);
			} catch (ActivityNotFoundException e) {
				// TODO
			}
		}
	}

	private void cancelThisNotification() {
		cancelNotification(this, mUri);
        NotificationState.stop(mUri);
	}
    
    public void setAlarm(long time) {
    	CountdownUtils.setAlarm(this, mUri, time);
    }
    
    public void cancelAlarm() {
    	CountdownUtils.cancelAlarm(this, mUri);
    }
    
    /**
     * Cancel notification (if it exists).
     * @param uri
     */
    public static void cancelNotification(Context context, Uri uri) {

        int notification_id = Integer.parseInt(uri.getLastPathSegment());
        
        // look up the notification manager service
        NotificationManager nm = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);

        // cancel the notification that we started in IncomingMessage
        nm.cancel(notification_id);
        
        // stop service for wake lock:
        Intent serviceIntent = new Intent(context, AlarmService.class);
        context.stopService(serviceIntent);
    }
    
    
    public void updateViews() {
    	updateCountdown();
		
		updateButtons();
		
		//updateCheckboxes();
		
		updateAutomate();
    }

	/**
	 * 
	 */
	private void updateCountdown() {
		long now = System.currentTimeMillis();
		
		long delta = mDeadline - now;
		
		//mDurationView.setText("" + CountdownUtils.getDurationString(mDuration));

		if (NotificationState.isActive(mUri)) {
			Log.v(TAG, "isActive");
			// Show dismiss button
			mCountdownState = STATE_COUNTDOWN_DISMISS;
			
			// show red 0:00:00
			setSettingVisibility(View.INVISIBLE);
			mCountdownView.setVisibility(View.VISIBLE);
			mCountdownView.setText("" + CountdownUtils.getDurationString(0));
			mCountdownView.setTextColor(0xffff0000);

		} else if (mCountdownState == STATE_COUNTDOWN_MODIFY) {
			setSettingVisibility(View.VISIBLE);
			mCountdownView.setVisibility(View.INVISIBLE);
		} else if (delta > 0) {
			//mDurationView.setText("");
			mCountdownState = STATE_COUNTDOWN_RUNNING;
			setSettingVisibility(View.INVISIBLE);
			mCountdownView.setVisibility(View.VISIBLE);
			mCountdownView.setText("" + CountdownUtils.getDurationString(delta));
			mCountdownView.setTextAppearance(this, android.R.style.TextAppearance_Large);
			mCountdownView.setTextSize(64);

    		mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_UPDATE_DISPLAY), 1000);
    		
    		if (delta < 2000) {
    			// Save the text as the notification may go off soon:
    	        ContentValues values = new ContentValues();
    	    	values.put(Durations.TITLE, mText.getText().toString());
    	    	
    	        getContentResolver().update(mUri, values, null, null);
    	        mCursor.requery();
    		}
		}/* else if (delta > -3000) {
			mCountdownState = STATE_COUNTDOWN_RUNNING;
			mDurationPicker.setVisibility(View.INVISIBLE);
			mCountdownView.setVisibility(View.VISIBLE);
			mCountdownView.setText("" + CountdownUtils.getDurationString(0));
			mCountdownView.setTextColor(0xffff0000);

    		mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_UPDATE_DISPLAY), 1000);
		} */else {
			mCountdownState = STATE_COUNTDOWN_IDLE;
			setSettingVisibility(View.VISIBLE);
			mCountdownView.setVisibility(View.INVISIBLE);
		}

	}
	
	private void setSettingVisibility(int visible) {
		if (mCountdownMode == MODE_SET_DURATION) {
			mDurationPicker.setVisibility(visible);
			mDateSetter.setVisibility(View.INVISIBLE);
		} else {
			// MODE_SET_DATE
			mDurationPicker.setVisibility(View.INVISIBLE);
			mDateSetter.setVisibility(visible);
			if (visible == View.VISIBLE) {
				mSetDate.setText(DateTimeFormater.mDateFormater
						.format(mUserDeadlineTemporary));
				mSetTime.setText(DateTimeFormater.mTimeFormater
						.format(mUserDeadlineTemporary));
			}
		}
	}
    
    private void updateButtons() {
		mStart.setVisibility(View.GONE);
		mStop.setVisibility(View.GONE);
		mModify.setVisibility(View.GONE);
		mCont.setVisibility(View.GONE);
		mDismiss.setVisibility(View.GONE);
    	switch (mCountdownState) {
    	case STATE_COUNTDOWN_IDLE:
    		mStart.setVisibility(View.VISIBLE);
    		break;
    	case STATE_COUNTDOWN_RUNNING:
    		mStop.setVisibility(View.VISIBLE);
    		mModify.setVisibility(View.VISIBLE);
    		break;
    	case STATE_COUNTDOWN_MODIFY:
    		mCont.setVisibility(View.VISIBLE);
    		break;
    	case STATE_COUNTDOWN_DISMISS:
    		mDismiss.setVisibility(View.VISIBLE);
    		break;
    	}
    }

    private void updateCheckboxes() {
    	mRingtoneView.setChecked(mRing == CHECKED);
    	
    	Ringtone ring = RingtoneManager.getRingtone(this, mRingtoneUri);
    	String ringname = ring.getTitle(this);
    	String s = getString(R.string.ringtone, ringname);
    	mRingtoneView.setText(s);
    	
    	mVibrateView.setChecked(mVibrate == CHECKED);
    	
    	mAutomateCheckBox.setChecked(mAutomate == CHECKED);
    }
    
    void updateAutomate() {
    	PackageManager pm = getPackageManager();
    	
    	if (mAutomateIntent != null) {
    		List<ResolveInfo> ri = pm.queryIntentActivities(mAutomateIntent, PackageManager.MATCH_DEFAULT_ONLY);

    		if (ri != null && ri.size() > 0) {
    			String description = ri.get(0).activityInfo.loadLabel(pm).toString();
        		
        		if (!TextUtils.isEmpty(mAutomateText)) {
        			description = mAutomateText;
        		}

    			//mAutomateTextView.setText(getString(R.string.action, description));
    			mAutomateTextView.setText(description);

    			Drawable icon = ri.get(0).activityInfo.loadIcon(pm);
        		mAutomateImage.setBackgroundDrawable(icon);
        		
        		if (mAutomateIntent.hasExtra(AutomationIntents.EXTRA_BROADCAST_INTENT)) {
        			mAutomateButton.setText(R.string.settings);
        		} else {
        			mAutomateButton.setText(R.string.test);
        		}
        		mAutomateButton.setVisibility(View.VISIBLE);
        		return;
    		}
    	}
    	
    	// if we arrive here, no valid intent is specified.
    	mAutomateTextView.setText(getString(R.string.action, ""));
    	mAutomateButton.setVisibility(View.GONE);
    }
    
    private void setRing(boolean checked) {
    	if (checked) {
			mRing = CHECKED;
		} else {
			mRing = UNCHECKED;
		}

        ContentValues values = new ContentValues();
    	values.put(Durations.RING, mRing);
    	
        getContentResolver().update(mUri, values, null, null);
        mCursor.requery();
    }
    

	/**
	 * @param checked
	 */
	private void setVibrate(boolean checked) {
		if (checked) {
			mVibrate = CHECKED;
		} else {
			mVibrate = UNCHECKED;
		}

        ContentValues values = new ContentValues();
    	values.put(Durations.VIBRATE, mVibrate);
    	
        getContentResolver().update(mUri, values, null, null);
        mCursor.requery();
	}

	/**
	 * @param checked
	 */
	private void setAutomate(boolean checked) {
		if (checked) {
			mAutomate = CHECKED;
		} else {
			mAutomate = UNCHECKED;
		}

        ContentValues values = new ContentValues();
    	values.put(Durations.AUTOMATE, mAutomate);
    	
        getContentResolver().update(mUri, values, null, null);
        mCursor.requery();
        
        if (mAutomate == CHECKED && mAutomateIntent == null) {
        	// Ask for suitable action.
        	setAutomation();
        }
	}
    
    private void pickRingtone() {
		Intent i = new Intent();
		i.setAction(RingtoneManager.ACTION_RINGTONE_PICKER);
		
		i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, mRingtoneUri);
		i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
		i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, mRingtoneType);
		
		startActivityForResult(i, REQUEST_CODE_RINGTONE);
    }
    

	@Override
	protected Dialog onCreateDialog(int id) {

		// We have to provide a current date on creation, otherwise
		// random dates are shown when the dialog first appears.
		mCalendar.setTimeInMillis(System.currentTimeMillis());
		int year = mCalendar.get(Calendar.YEAR);
		int month = mCalendar.get(Calendar.MONTH);
		int day = mCalendar.get(Calendar.DAY_OF_MONTH);
		int hour = mCalendar.get(Calendar.HOUR_OF_DAY);
		int minute = mCalendar.get(Calendar.MINUTE);

		switch (id) {
		case DIALOG_ID_SET_DATE:
			return new DatePickerDialog(this, mDateSetListener, year,
					month, day);
		case DIALOG_ID_SET_TIME:
			return new TimePickerDialog(this, mTimeSetListener, hour,
					minute, DateTimeFormater.mUse24hour);

        case DIALOG_CREATE_SHORTCUT:
            return new SelectTaskDialog().createDialog(this);
		}
		return null;
	}

	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		
		switch (id) {
		case DIALOG_ID_SET_DATE:
			mCalendar.setTimeInMillis(mUserDeadlineTemporary);

			((DatePickerDialog) dialog).updateDate(
					mCalendar.get(Calendar.YEAR),
					mCalendar.get(Calendar.MONTH), mCalendar
							.get(Calendar.DAY_OF_MONTH));
			break;
		case DIALOG_ID_SET_TIME:
			mCalendar.setTimeInMillis(mUserDeadlineTemporary);

			((TimePickerDialog) dialog).updateTime(mCalendar
					.get(Calendar.HOUR_OF_DAY), mCalendar.get(Calendar.MINUTE));
			break;
		}
	}

	private DatePickerDialog.OnDateSetListener mDateSetListener = new DatePickerDialog.OnDateSetListener() {

		public void onDateSet(DatePicker view, int year, int monthOfYear,
				int dayOfMonth) {
			//long startDate = mCursor.getLong(COLUMN_INDEX_START);
			mCalendar.setTimeInMillis(mUserDeadlineTemporary);
			int hour = mCalendar.get(Calendar.HOUR_OF_DAY);
			int minute = mCalendar.get(Calendar.MINUTE);
			mCalendar.setTimeInMillis(0); // Reset milliseconds
			mCalendar.set(year, monthOfYear, dayOfMonth, hour, minute);
			mUserDeadlineTemporary = mCalendar.getTimeInMillis();
			
			mUserDeadlineModified = true;
			
			updateViews();
/*
			updateDatabase();
			ContentValues values = new ContentValues();
			values.put(Job.START_DATE, millis);
			getContentResolver().update(mUri, values, null, null);
			updateFromCursor();
			*/
		}
	};

	private TimePickerDialog.OnTimeSetListener mTimeSetListener = new TimePickerDialog.OnTimeSetListener() {

		public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
			//long startDate = mCursor.getLong(COLUMN_INDEX_START);
			mCalendar.setTimeInMillis(mUserDeadlineTemporary);
			int year = mCalendar.get(Calendar.YEAR);
			int month = mCalendar.get(Calendar.MONTH);
			int day = mCalendar.get(Calendar.DAY_OF_MONTH);
			mCalendar.setTimeInMillis(0); // Reset milliseconds
			mCalendar.set(year, month, day, hourOfDay, minute);
			mUserDeadlineTemporary = mCalendar.getTimeInMillis();

			mUserDeadlineModified = true;
			
			updateViews();
/*
			ContentValues values = getContentValues();
			values.put(Job.START_DATE, millis);
			updateDatabase(values);
			updateFromCursor();
			*/
		}
	};

	
	/** Handle the process of updating the timer */
	Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			if (msg.what == MSG_UPDATE_DISPLAY) {
				// Update
				updateViews();
	            
			}
		}
	};
	
	BroadcastReceiver mReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			// Update internal state:
			Log.v(TAG, "onReceive()");

			updateViews();
		}
		
	};

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
		//Log.i(TAG, "onActivityResult: " + requestCode + ", " + resultCode);
		//Log.i(TAG, "data: " + data.toString());

		if (resultCode == RESULT_OK) {
			switch(requestCode) {
			case REQUEST_CODE_RINGTONE:
				setRingtone(data);
				break;
			case REQUEST_CODE_PICK_AUTOMATION_TASK:
				startActivityForResult(data, REQUEST_CODE_SET_AUTOMATION_TASK);
				break;
			case REQUEST_CODE_PICK_SHORTCUT:
				addShortcut(data);
				break;
			case REQUEST_CODE_SET_AUTOMATION_TASK:
	            addAutomationTask(data);
	            break;
			case REQUEST_CODE_SET_SHORTCUT:
				addShortcutTask(data);
				break;
			case REQUEST_CODE_SET_APPLICATION:
				addApplicationTask(data);
	            break;
			}
		}

		checkValidAutomateIntent();
		
	}

	private void setRingtone(Intent intent) {
		Bundle bundle = intent.getExtras();
		mRingtoneUri = (Uri) bundle.get(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
		Log.i(TAG, "New ringtone: " + mRingtoneUri);
		
		ContentValues values = new ContentValues();

		values.put(Durations.RING, CHECKED);
		
		String uristring = null;
		if (mRingtoneUri != null) {
			uristring = mRingtoneUri.toString();
		}
		values.put(Durations.RINGTONE, uristring);
		
		//Log.i(TAG, "Uri: " + mUri.toString());
		
		// Commit all of our changes to persistent storage. When the update completes
		// the content provider will notify the cursor of the change, which will
		// cause the UI to be updated.
		getContentResolver().update(mUri, values, null, null);
		
		mCursor.requery();
	}

	private void addAutomationTask(Intent intent) {
		ContentValues values = new ContentValues();

		values.put(Durations.AUTOMATE, CHECKED);
		
		if (intent != null) {
			mAutomateIntent = new Intent(intent);
			//mAutomateIntent.setAction(Intent.ACTION_VIEW);
			values.put(Durations.AUTOMATE_INTENT, mAutomateIntent.toURI());
			Log.i(TAG, "Received automation intent: " + mAutomateIntent.toURI());
			
			if (mAutomateIntent.hasExtra(AutomationIntents.EXTRA_DESCRIPTION)) {
				mAutomateText = mAutomateIntent.getStringExtra(AutomationIntents.EXTRA_DESCRIPTION);
				Log.i(TAG, "Received description: " + mAutomateText);
				values.put(Durations.AUTOMATE_TEXT, mAutomateText);
			}
			
			Log.i(TAG, "Uri: " + mAutomateIntent.toURI());
			
		    // Commit all of our changes to persistent storage. When the update completes
		    // the content provider will notify the cursor of the change, which will
		    // cause the UI to be updated.
		    getContentResolver().update(mUri, values, null, null);
		    
		    mCursor.requery();
		} else {
			// Not a valid intent
			checkValidAutomateIntent();
		}
	}
	
	void addShortcut(Intent intent) {
		// Handle case where user selected "Applications"
		String applicationName = getResources().getString(R.string.group_applications);
		String shortcutName = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
		
		if (applicationName != null && applicationName.equals(shortcutName)) {
			Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
			mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
			
			Intent pickIntent = new Intent(Intent.ACTION_PICK_ACTIVITY);
			pickIntent.putExtra(Intent.EXTRA_INTENT, mainIntent);

            if (SDKVersion.SDKVersion < 3) {
            	if (debug) Log.i(TAG, "Compatibility mode for ActivityPicker");
                // SDK 1.1 backward compatibility:
                // We launch our own version of ActivityPicker:
                pickIntent.setClass(this, DialogHostingActivity.class);
                pickIntent.putExtra(DialogHostingActivity.EXTRA_DIALOG_ID, 
                			DialogHostingActivity.DIALOG_ID_ACTIVITY_PICKER);
            } else {
            	if (debug) Log.i(TAG, "Call system ActivityPicker");
            }
            
			startActivityForResult(pickIntent, REQUEST_CODE_SET_APPLICATION);
		} else {
			startActivityForResult(intent, REQUEST_CODE_SET_SHORTCUT);
		}
	}

	private void addShortcutTask(Intent intent) {
		ContentValues values = new ContentValues();

		values.put(Durations.AUTOMATE, CHECKED);
		
		if (intent != null) {
			mAutomateIntent = intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
			mAutomateText = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
			
			/// TODO: Save bitmap somewhere?
			// Bitmap bitmap = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON);
			 
			//mAutomateIntent = new Intent(intent);
			//mAutomateIntent.setAction(Intent.ACTION_VIEW);
			values.put(Durations.AUTOMATE_INTENT, mAutomateIntent.toURI());
			values.put(Durations.AUTOMATE_TEXT, mAutomateText);
			
			Log.i(TAG, "automate intent: " + mAutomateIntent.toURI());
			//Log.i(TAG, "Uri: " + mUri.toString());
			
		    // Commit all of our changes to persistent storage. When the update completes
		    // the content provider will notify the cursor of the change, which will
		    // cause the UI to be updated.
		    getContentResolver().update(mUri, values, null, null);
		    
		    mCursor.requery();
		} else {
			// Not a valid intent
			checkValidAutomateIntent();
		}
	}

	private void addApplicationTask(Intent intent) {
		ContentValues values = new ContentValues();

		values.put(Durations.AUTOMATE, CHECKED);
		
		if (intent != null) {
			mAutomateIntent = new Intent(intent);
			//mAutomateIntent.setAction(Intent.ACTION_VIEW);
			values.put(Durations.AUTOMATE_INTENT, mAutomateIntent.toURI());
			

			PackageManager pm = getPackageManager();
    		List<ResolveInfo> ri = pm.queryIntentActivities(mAutomateIntent, 0);
    		
    		mAutomateText = "";
    		if (ri != null && ri.size() > 0) {
    			mAutomateText = ri.get(0).activityInfo.loadLabel(pm).toString();
    		}
    		
    		values.put(Durations.AUTOMATE_TEXT, mAutomateText);
			
			Log.i(TAG, "addApplicationTask: " + mAutomateIntent.toURI());
			//Log.i(TAG, "Uri: " + mUri.toString());
			
		    // Commit all of our changes to persistent storage. When the update completes
		    // the content provider will notify the cursor of the change, which will
		    // cause the UI to be updated.
		    getContentResolver().update(mUri, values, null, null);
		    
		    mCursor.requery();
		} else {
			// Not a valid intent
			checkValidAutomateIntent();
		}
	}
	
	void checkValidAutomateIntent() {
		if (mAutomateIntent == null) {
			// Unset check box.
			setAutomate(false);
		}
	}
	
}