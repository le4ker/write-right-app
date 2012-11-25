/*
 *    Alternative input method that resizes its virtual keys according to
 *    usage statistics by the user (when, where and which words were typed.
 *    Copyright (C) June 2011  Panagiotis Sakkos <panos.sakkos@gmail.com>
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Affero General Public License as
 *    published by the Free Software Foundation, either version 3 of the
 *    License, or (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package panos.sakkos.softkeyboard.writeright;

import android.content.Context;
import android.database.Cursor;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.Keyboard.Key;
import android.inputmethodservice.KeyboardView;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;

import panos.sakkos.softkeyboard.writeright.R;

public class SoftKeyboard extends InputMethodService implements KeyboardView.OnKeyboardActionListener 
{
    static final char NO_INPUT = 0;
    static final int CONTINOUS_DELETE_THRESHLOD = 3;
    private KeyboardView mInputView;
    
    private StringBuilder mComposing = new StringBuilder();
    private boolean mPredictionOn;
    private int mLastDisplayWidth;
    private boolean mCapsLock;
    private long mLastShiftTime;
    
    private LatinKeyboard mSymbolsKeyboard;
    private LatinKeyboard mSymbolsShiftedKeyboard;
    private LatinKeyboard mQwertyKeyboard;
    
    private LatinKeyboard mCurKeyboard;
    
    private String mWordSeparators;
    
    private Predictor predictor;
    private int initialKeyHeight, initialKeyWidth;  
    private int smallHeight, smallWidth;
    private int k = 26;
    private int continuousSuccesses = 0;
    private final int AGGRESIVE_THRESHOLD = 5;
    private int continuousDeleteHits = 0;
    
    private boolean unprobableKeysMoved = false;
    List<Character> probableKeys;

    private boolean landscape = false;
    OrientationEventListener myOrientationEventListener; 
    
    private boolean predictionCanceled = false;
    private Vibrator vibrator;    
    
    private DataBaseHelper myDbHelper;    
    
    private int editorInfo;
    private boolean autocomplete;
    
    /**
     * Main initialization of the input method component.  Be sure to call
     * to super class.
     */
    
    @Override public void onCreate() 
    {
        super.onCreate();
        Log.i("DEBUG", "onCreate");
        mWordSeparators = getResources().getString(R.string.word_separators);

        Init();
    }

    @Override public void onDestroy() 
    {
      Log.i("DEBUG", "onDestroy");
      OnClose();
      super.onDestroy();
    }
    
    
    /**
     * This is the point where you can do all of your UI initialization.  It
     * is called after creation and any configuration change.
     */
    @Override public void onInitializeInterface() 
    {
		Log.i("DEBUG", "onInitializeInterface");		

		if (mQwertyKeyboard != null) {
            // Configuration changes can happen after the keyboard gets recreated,
            // so we need to be able to re-build the keyboards if the available
            // space has changed.
            int displayWidth = getMaxWidth();
            if (displayWidth == mLastDisplayWidth) return;
            mLastDisplayWidth = displayWidth;

            /* keyboard width changed, do the proper initializations for the UI */
            
            if (getResources().getConfiguration().orientation == 1 && landscape == true)
            {
            	landscape = false;
                unprobableKeysMoved = false;
            }
            else if(getResources().getConfiguration().orientation == 2 && landscape == false)
            {
            	landscape = true;
                unprobableKeysMoved = false;
            }
            
		}
		
        mQwertyKeyboard = new LatinKeyboard(this, R.xml.qwerty);
        mSymbolsKeyboard = new LatinKeyboard(this, R.xml.symbols);
        mSymbolsShiftedKeyboard = new LatinKeyboard(this, R.xml.symbols_shift);

		GetkeyInitialSizes();        

		/* If the user changes orientation in the middle of typing a word, resize */
		
		if(mComposing.length() > 0)
		{
			ShrinkLessK(predictor.GetPredictions());
		}
    }
    
    /**
     * Called by the framework when your view for creating input needs to
     * be generated.  This will be called the first time your input method
     * is displayed, and every time it needs to be re-created such as due to
     * a configuration change.
     */
    @Override public View onCreateInputView() {    	
		Log.i("DEBUG", "onCreateInputView");		

		mInputView = (KeyboardView) getLayoutInflater().inflate(
                R.layout.input, null);
        mInputView.setOnKeyboardActionListener(this);
        mInputView.setKeyboard(mQwertyKeyboard);
        
        return mInputView;
    }

    /**
     * This is the main point where we do our initialization of the input method
     * to begin operating on an application.  At this point we have been
     * bound to the client, and are now receiving all of the detailed information
     * about the target of our edits.
     */
    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);

        Log.i("DEBUG", "onStartInput");		
        
        // Reset our state.  We want to do this even if restarting, because
        // the underlying state of the text editor could have changed in any way.
        
        //FUTURE REVIEW the line bellow messed my words when orientation changed
        //mComposing.setLength(0);
                
        mPredictionOn = false;
        
        // We are now going to initialize our state based on the type of
        // text being edited.
        
        editorInfo = attribute.inputType&EditorInfo.TYPE_MASK_CLASS;
        
        switch (attribute.inputType&EditorInfo.TYPE_MASK_CLASS) {
            case EditorInfo.TYPE_CLASS_NUMBER:
            case EditorInfo.TYPE_CLASS_DATETIME:
                // Numbers and dates default to the symbols keyboard, with
                // no extra features.
                mCurKeyboard = mSymbolsKeyboard;
                Log.i("DEBUG", "NUMBER OR DATETIME");
                break;
                
            case EditorInfo.TYPE_CLASS_PHONE:
                // Phones will also default to the symbols keyboard, though
                // often you will want to have a dedicated phone keyboard.
                mCurKeyboard = mSymbolsKeyboard;
                Log.i("DEBUG", "PHONE");
                break;
                
            case EditorInfo.TYPE_CLASS_TEXT:
                // This is general text editing.  We will default to the
                // normal alphabetic keyboard, and assume that we should
                // be doing predictive text (showing candidates as the
                // user types).
                mCurKeyboard = mQwertyKeyboard;
                mPredictionOn = true;
                
                // We now look for a few special variations of text that will
                // modify our behavior.
                int variation = attribute.inputType &  EditorInfo.TYPE_MASK_VARIATION;
                if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
                        variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                    // Do not display predictions / what the user is typing
                    // when they are entering a password.
                	Log.i("DEBUG", "PASSWORD");
                    mPredictionOn = false;
                }
                
                if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS 
                        || variation == EditorInfo.TYPE_TEXT_VARIATION_URI
                        || variation == EditorInfo.TYPE_TEXT_VARIATION_FILTER) {
                    // Our predictions are not useful for e-mail addresses
                    // or URIs.
                	Log.i("DEBUG", "MAIL OR URI");
                    mPredictionOn = false;
                }
                
                if ((attribute.inputType&EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
                    // If this is an auto-complete text view, then our predictions
                    // will not be shown and instead we will allow the editor
                    // to supply their own.  We only show the editor's
                    // candidates when in fullscreen mode, otherwise relying
                    // own it displaying its own UI.
                	Log.i("DEBUG", "AUTO COMPLETE");
                	autocomplete = true;
                    mPredictionOn = false;
                }
                else
                {
                	autocomplete = false;
                }
                
                // We also want to look at the current state of the editor
                // to decide whether our alphabetic keyboard should start out
                // shifted.
                updateShiftKeyState(attribute);
                break;
                
            default:
                // For all unknown input types, default to the alphabetic
                // keyboard with no special features.
                mCurKeyboard = mQwertyKeyboard;
                updateShiftKeyState(attribute);
        }
        
        // Update the label on the enter key, depending on what the application
        // says it will do.
        mCurKeyboard.setImeOptions(getResources(), attribute.imeOptions);
        
        if(mPredictionOn == false)
        {
            RestoreInitialSizes();
        }
    }

    /**
     * This is called when the user is done editing a field.  We can use
     * this to reset our state.
     */
    @Override public void onFinishInput() 
    {
        super.onFinishInput();
        
		Log.i("DEBUG", "onFinishInput");		
        
        // Clear current composing text and candidates.
        mComposing.setLength(0);
        
        // We only hide the candidates window when finishing input on
        // a particular editor, to avoid popping the underlying application
        // up and down if the user is entering text into the bottom of
        // its window.
        setCandidatesViewShown(false);
        
        mCurKeyboard = mQwertyKeyboard;
        if (mInputView != null) {
            mInputView.closing();
        }
        
        WordseparatorTyped();
    }
    
    @Override public void onStartInputView(EditorInfo attribute, boolean restarting) {
        super.onStartInputView(attribute, restarting);
        // Apply the selected keyboard to the input view.
		Log.i("DEBUG", "onStartInputView");		
        mInputView.setKeyboard(mCurKeyboard);
        mInputView.closing();
    }
    
    /**
     * Deal with the editor reporting movement of its cursor.
     */
    @Override public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd, int candidatesStart, int candidatesEnd) 
    {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        Log.i("DEBUG", "onupdateSelection");		        


/*
 * code version 2 for fixing the bug with the prediction enabling in "bad" textboxes after a wrd separator was typed ;).
 * Code bellow was moved here from onrelease method
 */
        
        if(PredictionFriendlyInput())
        {
			if(CursorIsAtTheEnd() == false && isWordSeparator(FirstCharacterBeforeCursor()) == false)
			{
				mPredictionOn = false;
				predictor.SetIdle();
				RestoreInitialSizes();
				Log.d("DEBUG", "CURSOR NOT AT END AND NOT separator; PREDICTOR SET TO IDLE");
			}
			else if(CursorIsAtTheEnd() && isWordSeparator(FirstCharacterBeforeCursor()))
			{
				mPredictionOn = true;
				predictor.SetNotIdle();
				Log.d("DEBUG", "CURSOR AT END AND separator; PREDICTOR SET TO IDLE");
			}	 
	
	        // If the current selection in the text view changes, we should
	        // clear whatever candidate text we have.
	        if (mComposing.length() > 0 && (newSelStart != candidatesEnd
	                || newSelEnd != candidatesEnd)) {
	            mComposing.setLength(0);
	            InputConnection ic = getCurrentInputConnection();
	            if (ic != null) {
	            	ic.finishComposingText();
	            }
	        }
        }
    }

    /**
     * Use this to monitor key events being delivered to the application.
     * We get first crack at them, and can either resume them or let them
     * continue to the app.
     */
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                // The InputMethodService already takes care of the back
                // key for us, to dismiss the input method if it is shown.
                // However, our keyboard could be showing a pop-up window
                // that back should dismiss, so we first allow it to do that.
                if (event.getRepeatCount() == 0 && mInputView != null) {
                    if (mInputView.handleBack()) {
                        return true;
                    }
                }
                break;
                
            case KeyEvent.KEYCODE_DEL:
                // Special handling of the delete key: if we currently are
                // composing text for the user, we want to modify that instead
                // of let the application to the delete itself.
                if (mComposing.length() > 0) {
                    onKey(Keyboard.KEYCODE_DELETE, null);
                    return true;
                }
                break;
                
            case KeyEvent.KEYCODE_ENTER:
                // Let the underlying text editor always handle these.
                return false;
                
            default:
        }
        
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Use this to monitor key events being delivered to the application.
     * We get first crack at them, and can either resume them or let them
     * continue to the app.
     */
    @Override public boolean onKeyUp(int keyCode, KeyEvent event) 
    {
        return super.onKeyUp(keyCode, event);
    }

    /**
     * Helper function to commit any text being composed in to the editor.
     */
    private void commitTyped(InputConnection inputConnection) 
    {
        if (mComposing.length() > 0) 
        { 
            inputConnection.commitText(mComposing, mComposing.length());
            mComposing.setLength(0);
            RestoreInitialSizes();
        }
    }

    /**
     * Helper to update the shift state of our keyboard based on the initial
     * editor state.
     */
    private void updateShiftKeyState(EditorInfo attr) {
        if (attr != null && mInputView != null && mQwertyKeyboard == mInputView.getKeyboard()) {
            int caps = 0;
            EditorInfo ei = getCurrentInputEditorInfo();
            if (ei != null && ei.inputType != EditorInfo.TYPE_NULL) {
                caps = getCurrentInputConnection().getCursorCapsMode(attr.inputType);
            }
            mInputView.setShifted(mCapsLock || caps != 0);
        }
    }
    
    /**
     * Helper to determine if a given character code is alphabetic.
     */
    private boolean isAlphabet(int code) 
    {
        return Character.isLetter(code);
    }
    
    /**
     * Helper to send a key down / key up pair to the current editor.
     */
    private void keyDownUp(int keyEventCode) 
    {
        getCurrentInputConnection().sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
        getCurrentInputConnection().sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
    }
    
    /**
     * Helper to send a character to the editor as raw key events.
     */
    private void sendKey(int keyCode) {
        switch (keyCode) {
            case '\n':
                keyDownUp(KeyEvent.KEYCODE_ENTER);
                break;
            default:
                if (keyCode >= '0' && keyCode <= '9') {
                    keyDownUp(keyCode - '0' + KeyEvent.KEYCODE_0);
                } else {
                    getCurrentInputConnection().commitText(String.valueOf((char) keyCode), 1);
                }
                break;
        }
    }

    /* Implementation of KeyboardViewListener */

    public void onKey(int primaryCode, int[] keyCodes) {
        if (isWordSeparator(primaryCode)) {
            // Handle separator
            if (mComposing.length() > 0) {
                commitTyped(getCurrentInputConnection());
            }
            sendKey(primaryCode);
            updateShiftKeyState(getCurrentInputEditorInfo());
        } else if (primaryCode == Keyboard.KEYCODE_DELETE) {
            handleBackspace();
        } else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
            handleShift();
        } else if (primaryCode == Keyboard.KEYCODE_CANCEL) {
            handleClose();
            return;
        } else if (primaryCode == LatinKeyboardView.KEYCODE_OPTIONS) {
            // Show a menu or somethin'
        } else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE
                && mInputView != null) {
            Keyboard current = mInputView.getKeyboard();
            if (current == mSymbolsKeyboard || current == mSymbolsShiftedKeyboard) {
                current = mQwertyKeyboard;
            } else {
                current = mSymbolsKeyboard;
            }
            mInputView.setKeyboard(current);
            if (current == mSymbolsKeyboard) {
                current.setShifted(false);
            }
        } else {
            handleCharacter(primaryCode, keyCodes);
        }
    }

    public void onText(CharSequence text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.beginBatchEdit();
        if (mComposing.length() > 0) {
            commitTyped(ic);
        }
        ic.commitText(text, 0);
        ic.endBatchEdit();
        updateShiftKeyState(getCurrentInputEditorInfo());
    }
        
    private void handleBackspace()
    {
        int length = mComposing.length();
        
        if(continuousDeleteHits > CONTINOUS_DELETE_THRESHLOD && isWordSeparator(FirstCharacterBeforeCursor()) == false && length != 0)
        {
        	//TODO delete the composing word
        	getCurrentInputConnection().deleteSurroundingText(GetLastWordBeforeCursor().length() + 1, 0);
        }
        else if (length > 1) 
        {
            mComposing.delete(length - 1, length);
            getCurrentInputConnection().setComposingText(mComposing, 1);
        }
        else if (length > 0)
        {
            mComposing.setLength(0);
            getCurrentInputConnection().commitText("", 0);
        }
        else
        {
            keyDownUp(KeyEvent.KEYCODE_DEL);
        }
        
        continuousDeleteHits++;
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    private void handleShift() {
        if (mInputView == null) {
            return;
        }
        
        Keyboard currentKeyboard = mInputView.getKeyboard();
        if (mQwertyKeyboard == currentKeyboard) {
            // Alphabet keyboard
            checkToggleCapsLock();
            mInputView.setShifted(mCapsLock || !mInputView.isShifted());
        } else if (currentKeyboard == mSymbolsKeyboard) {
            mSymbolsKeyboard.setShifted(true);
            mInputView.setKeyboard(mSymbolsShiftedKeyboard);
            mSymbolsShiftedKeyboard.setShifted(true);
        } else if (currentKeyboard == mSymbolsShiftedKeyboard) {
            mSymbolsShiftedKeyboard.setShifted(false);
            mInputView.setKeyboard(mSymbolsKeyboard);
            mSymbolsKeyboard.setShifted(false);
        }
    }
    
    private void handleCharacter(int primaryCode, int[] keyCodes) {
        if (isInputViewShown()) {
            if (mInputView.isShifted()) {
                primaryCode = Character.toUpperCase(primaryCode);
            }
        }
        if (isAlphabet(primaryCode) && mPredictionOn) {
            mComposing.append((char) primaryCode);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            updateShiftKeyState(getCurrentInputEditorInfo());
        } else {
            getCurrentInputConnection().commitText(
                    String.valueOf((char) primaryCode), 1);
        }
    }

    private void handleClose() {
        commitTyped(getCurrentInputConnection());
        requestHideSelf(0);
        mInputView.closing();
    }

    private void checkToggleCapsLock() {
        long now = System.currentTimeMillis();
        if (mLastShiftTime + 800 > now) {
            mCapsLock = !mCapsLock;
            mLastShiftTime = 0;
        } else {
            mLastShiftTime = now;
        }
    }
    
    private String getWordSeparators() 
    {
        return mWordSeparators;
    }
    
    public boolean isWordSeparator(int code) 
    {
        String separators = getWordSeparators();
        return separators.contains(String.valueOf((char)code));
    }
    
    public void swipeDown() 
    {
        handleClose();
    }
    
    /* On up, left and right swipe, the user cancels the current predictions and when he finishes
     * typing the word (when he types a word separator) the new word will be added 
     * to the predictor (see WordSeparatorTyped)
     */    

    public void swipeUp() 
    {
    	CancelPrediction();    
    }
    
    public void swipeRight() 
    {
    	CancelPrediction();
    }

    public void swipeLeft() 
    {
    	CancelPrediction();
    }
    
    public void onRelease(int primaryCode) 
    {
    	vibrator.vibrate(25);

    	/* Do nothing if the resize is disabled */
    	
    	if(mPredictionOn == false)
    	{
    		return;
    	}

    	if (primaryCode == Keyboard.KEYCODE_DELETE) 
        {
        	Log.d("DEBUG", "BACKSPACE PRESSED");         	
        	DeleteTyped();
        }

    	if (isWordSeparator(primaryCode)) 
        {
    		Log.d("DEBUG", "WORD SEPARATOR PRESSED");
        	WordseparatorTyped();
        }
        else if (Character.isLetter(primaryCode))
        {
    		Log.d("DEBUG", "CHARACTER PRESSED");
            CharacterTyped(primaryCode);   
        }
    	
    	/* Handle cursor not at the end of input and not last character word separator */
    }
    
    public void onPress(int primaryCode) 
    {    	 
    }

	private void DeleteTyped() 
	{
		char lastCharacter = FirstCharacterBeforeCursor(); 
		
		if(isWordSeparator(lastCharacter))
		{
			predictor.SetNotIdle();
			RestoreInitialSizes();
			Log.d("DEBUG", "WORD separator DETECTED; PREDICTOR NOT IDLE");			
		}
		else if(lastCharacter == NO_INPUT)
		{
			predictor.SetNotIdle();			
			RestoreInitialSizes();
			Log.d("DEBUG", "NO INPUT DETECTED; PREDICTOR NOT IDLE");			
		}
		else if(GetLastWordBeforeCursor().length() > 0)
		{
			/* "Backward" top k resize */
			
			BackwardShrinkLessK();
		}
		else
		{
			predictor.SetIdle();
			RestoreInitialSizes();
			Log.d("DEBUG", "PREDICTOR IDLE");			
		}
	}

	private void BackwardShrinkLessK() 
	{
		predictor.SetIdle(); predictor.SetNotIdle();
		for(int i = 0; i < GetLastWordBeforeCursor().length(); i++)
		{
			try
			{
				predictor.CharacterTyped(GetLastWordBeforeCursor().charAt(i));
			}
			catch(Exception ex)
			{
				Log.d("DEBUG", ex.getMessage());
			}
		}
		
		ShrinkLessK(predictor.GetPredictions());
	}

	private void CharacterTyped(int primaryCode) 
	{
		char character = (char) primaryCode; 
		
		try 
		{
			predictor.CharacterTyped(character);
		} 
		catch (Exception e) 
		{
		    Log.d("DEBUG", "PREDICTOR.CHARACTER_TYPED: FAILED");
		}

		HashMap<Character, Float> predictions = predictor.GetPredictions();

		if( predictions.isEmpty())
		{
			RestoreInitialSizes();
			Log.d("DEBUG", "NO PREDICTIONS RETURNED");
		}
		else
		{			
			ShrinkLessK(predictions);        
		}

		/* Restore the continuous delete hits variable */
		
		continuousDeleteHits = 0;		
	}

	private void WordseparatorTyped() 
	{		
		if(predictionCanceled)
		{
			Log.d("DEBUG", "NEW WORD TO BE ADDED TO PREDICTOR: " + GetLastWordBeforeCursor());
			predictor.LearnNewWord(GetLastWordBeforeCursor()); 
			predictionCanceled = false;
		}
		else
		{
			SuccessfullPrediction();
		}

		try 
		{
			predictor.WordTyped();
        	Log.i("DEBUG", "PREDICTOR NOT IDLE");
		}
		catch (Exception e) 
		{
			Log.e("DEBUG", "FAILED TO HANDLE separator");
		}

		/* Restore initial key sizes when a word is typed */
		
		RestoreInitialSizes();
		
		/* Restore the continuous delete hit variable */
		
		continuousDeleteHits = 0;
	}
    
	/* Checks for external memory read/write availability. If available
	 * and the clean_wordnet file does not exist, it copies it. If the predictor instance file
	 * doesn't exist, it creates it. In case of external memory read/write unavailability, raises 
	 * a keyboardInactive flag, in order to avoid exceptions etc. 
	 */
    
    private void Init()
    {    	
    	/* Create database if doesn't exist*/

        myDbHelper = new DataBaseHelper((Context) this);
 
        try 
        {
        	myDbHelper.createDataBase();
        	myDbHelper.openDataBase();
        }
        catch(Exception exception)
        {
        	Log.e("DEBUG", exception.getMessage());
        }    	

        /* Load predictor essentials */
        
        Cursor cursor = myDbHelper.GetEssentials();
        cursor.moveToNext();
        k = cursor.getInt(0);
        continuousSuccesses = cursor.getInt(1);

        /* Create and load the predictor */
        
        try 
    	{    		
            long start = SystemClock.currentThreadTimeMillis();
    		predictor = new Predictor(myDbHelper);
    		long elapsed = SystemClock.currentThreadTimeMillis() - start;
    		Log.i("DEBUG", "PREDICTOR CREATED IN " + Long.toString(elapsed) + " MILLISECONDS");
    	} 
    	catch (Exception e) 
    	{
    		Log.e("DEBUG", "LOAD PREDICTOR_INSTANCE: FAILED");
		}        
    	
    	vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }
    
    /* Gets the height and width that keys have at start, in order to 
     * be able to resize them at their initial size in the future
     */
    
    private void GetkeyInitialSizes()
    {    	
        smallWidth  = smallHeight = getMaxWidth() / 12;
    	initialKeyHeight = mQwertyKeyboard.getKeys().get(0).height;
    	initialKeyWidth = mQwertyKeyboard.getKeys().get(0).width;
    }
    
    /*
     * Restores every keys height to it's initial size. Used whenever a word is typed or
     * there is no prediction for next letter.
     */
    
    private void RestoreInitialSizes()
    {
    	boolean changed = false;
		
    	for(Key key : mQwertyKeyboard.getKeys())
    	{
        	if(KeyMustBeProccessed(key))
    		{
        		key.height = initialKeyHeight;
    			key.width = initialKeyWidth;
    		}    			

        	if( unprobableKeysMoved == true && KeyMustBeProccessed(key) && probableKeys.contains(new Character(key.label.charAt(0))) == false)
			{
        		if(landscape == false)
        		{
        			key.y -= initialKeyHeight / 3;
        		}
        		else
        		{
        			key.x -= getMaxWidth() / 50;
        		}
        		
        		changed = true;
			}
    	}
    	
    	if(changed)
    	{
    		unprobableKeysMoved = false;
    	}

    	if(mInputView != null)
    		mInputView.invalidateAllKeys();
    }
    
    private void ShrinkLessK(HashMap<Character, Float> predictions)
    {    	    	
    	/* Find top k next predicted letter */
    	
    	TreeSet<Float> sortedEvaluations = new TreeSet<Float>(predictions.values());    	
        sortedEvaluations.remove(new Float(0.0));

        /* Take top k evaluations */
        
        List<Float> topKEvaluations = new ArrayList<Float>();

        while(sortedEvaluations.size() > 0 && topKEvaluations.size() <= k)
        {
        	Float evaluation = sortedEvaluations.last();
        	sortedEvaluations.remove(evaluation);
        	topKEvaluations.add(evaluation);
        } 
                
    	RestoreInitialSizes();

        /* If there are no predictions, do not resize the keys */

        if(topKEvaluations.size() == 0)
        {
        	Log.d("DEBUG", "NO NON-ZERO VALUES FOUND");
        	return; 
        }
 
		List<Key> keys =  mQwertyKeyboard.getKeys();
		probableKeys = new ArrayList<Character>();

		/* Keep every probable next letter in the probableKeys list */

		for(int i = 0; i < topKEvaluations.size(); i++)
        {
        	for(Key key : keys)
        	{
        		if( KeyMustBeProccessed(key) && ProbabilitiesAreEqual(predictions.get(new Character(key.label.charAt(0))), topKEvaluations.get(i)))
        		{
        				probableKeys.add(new Character(key.label.charAt(0)));
        		}
        	}
        }
		
		/* Resize every key that it's letter is not in the probableKeys list */
		
		for(Key key : keys)
		{
			if(KeyMustBeProccessed(key) && probableKeys.contains(new Character(key.label.charAt(0))) == false)
			{
				if(landscape == false)
				{
					key.height = smallHeight;
					key.width = smallWidth;
				}
				else
				{
					key.height = key.width = getMaxWidth() / 15;
				}
				
				if( unprobableKeysMoved == false )
				{
		       		if(landscape == false)
		       		{
						key.y += initialKeyHeight / 3; 
		       		}
		       		else
		       		{
		       			key.x += getMaxWidth() / 50;
		       		}
				}
			}
		}
		
		unprobableKeysMoved = true;
		mInputView.invalidateAllKeys();

    }
    
    /* Saves the predictor instance */
    
    private void OnClose()
    {
    	try
    	{
       		predictor.Save();
   			Log.i("DEBUG", "PREDICTOR STATE SAVED");
   	    	myDbHelper.UpdateEssentials(k, continuousSuccesses);
   			Log.i("DEBUG", "ESSENTIALS UPDATED");
   			myDbHelper.UpgradeDatabase();
    		Log.i("DEBUG", "DATABASE UPGRADED");
       	}
    	catch(Exception ex)
   		{
   			Log.e("DEBUG", "ERROR WHILE SAVING DATA");
   		}    	
    }
    
    /* Helper to avoid arithmetic instability */
    
    private boolean ProbabilitiesAreEqual(Float probabilityA, Float probabilityB)
    {
    	return (double) (probabilityA.floatValue() * 10) == (double) (probabilityB.floatValue() * 10);
    }

    /* Helper which returns the last character at input */
    
    private char FirstCharacterBeforeCursor()
    {
    	InputConnection ic = getCurrentInputConnection();
    	CharSequence input =  ic.getTextBeforeCursor(100, 0);
    	
    	if(input.length() == 0)
    	{
    		Log.e("DEBUG", "NO INPUT");
    		return NO_INPUT;
    	}
    	else
    	{
//    		Log.e("DEBUG", Character.toString(input.charAt(ic.getTextBeforeCursor(100, 0).length() - 1)));

    		return input.charAt(ic.getTextBeforeCursor(100, 0).length() - 1);
    	}
    }
    
    private char FirstCharacterAfterCursor()
    {
    	InputConnection ic = getCurrentInputConnection();
    	CharSequence input =  ic.getTextAfterCursor(100, 0);
    
    	if(input.length() == 0)
    	{
    		Log.e("DEBUG", ".");
    		return '.'; //return a separator
    	}
    	else
    	{
    		Log.e("DEBUG", Character.toString(input.charAt(0)));
    		return input.charAt(0);
    	}
    }
    
    /* Helper that returns if the cursor is at the end of input */
    
    private boolean CursorIsAtTheEnd()
    {
    	InputConnection inputConnection = getCurrentInputConnection();
    	CharSequence textAfterCursor = inputConnection.getTextAfterCursor(100, 0);
    	return textAfterCursor.length() == 0;
    }
    
    /* Helper which determines if the key must be processed */
    
    private boolean KeyMustBeProccessed(Key key)
    {
    	return key.label != null && key.label.length() == 1 && Character.isLetter(key.label.charAt(0));
    }
    
    /* Helper which returns the last word before the cursor*/

    private String GetLastWordBeforeCursor()
    {
    	String input = getCurrentInputConnection().getTextBeforeCursor(100, 0).toString();
    	
    	if(input == null || input.length() == 0)
    	{
    		Log.d("DEBUG", "NO LAST WORD FOUND");
    		return null;
    	}

    	int lastSeparatorIndex = 0;
    	for(int i = 0; i < input.length() - 1; i++)
    	{
    		if(isWordSeparator(input.charAt(i)))
    		{
    			lastSeparatorIndex = i;
    		}
    	}
    	
    	return input.substring(lastSeparatorIndex, input.length()).trim();
    }
    
    /* Helper to cancel the current prediction */
    
	private void CancelPrediction() 
	{
		predictionCanceled = true;
    	predictor.PredictionCanceled();
    	RestoreInitialSizes();
    	FailedPrediction();
	}

	/* Helpers to decide aggressiveness according to user's satisfaction */

	private void FailedPrediction() 
	{
		if(k < 26)
		{
			k++;
			Log.i("DEBUG", "PREDICTOR IS NOW LESS AGGRESSIVE");
		}
		
		continuousSuccesses = 0;
		Log.i("DEBUG", "FAILED PREDICTION");
	}
	
	private void SuccessfullPrediction()
	{
		continuousSuccesses++;
		
		if(continuousSuccesses == AGGRESIVE_THRESHOLD)
		{
			continuousSuccesses = 0;
			
			if(k > 1)
			{
				k--;
				Log.i("DEBUG", "PREDICTOR IS NOW MORE AGGRESSIVE");
			}
		}
		
		Log.i("DEBUG", "SUCCESSFUL PREDICTION");		
	}

	private boolean PredictionFriendlyInput()
	{
		return editorInfo == EditorInfo.TYPE_CLASS_TEXT && autocomplete == false;
	}

}




