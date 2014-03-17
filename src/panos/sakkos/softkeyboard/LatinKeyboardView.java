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

package panos.sakkos.softkeyboard;

import android.content.Context;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.inputmethodservice.Keyboard.Key;
import android.util.AttributeSet;

public class LatinKeyboardView extends KeyboardView {

    static final int KEYCODE_OPTIONS = -100;

    public LatinKeyboardView(Context context, AttributeSet attrs) 
    {
        super(context, attrs);
    }

    public LatinKeyboardView(Context context, AttributeSet attrs, int defStyle) 
    {
        super(context, attrs, defStyle);
    }

    @Override
    protected boolean onLongPress(Key key) 
    {
        if (key.codes[0] == Keyboard.KEYCODE_CANCEL) 
        {
            getOnKeyboardActionListener().onKey(KEYCODE_OPTIONS, null);
            return true;
        } 
        else 
        {
            return super.onLongPress(key);
        }
    }
}
