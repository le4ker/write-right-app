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

/**
 * A class that keeps the usage statistics about the words that were typed
 * In the future, location and time will also be added.
 * @author Panos Sakkos
 */

public class Statistics
{
    private int usage;
    private long timestamp;

    public Statistics()
    {
        this.usage = 1;
        timestamp = System.currentTimeMillis();
    }
    
    
    public Statistics(int usage)
    {
        this.usage = usage;
        timestamp = System.currentTimeMillis();
    }

    public Statistics(int usage, long timestamp)
    {
    	this.usage = usage;
    	this.timestamp = timestamp;
    }
    
    /**
     * This method must be called when the word that the Statistics instance belongs is typed
     */

    public void WordTyped()
    {
        usage++;
        timestamp = System.currentTimeMillis();
    }

    /**
     * Returns how many times the word was typed
     */

    public int GetPopularity()
    {
        return usage;
    }
    
    public long GetTimestamp()
    {
    	return timestamp;
    }


    @Override public String toString()
    {
        return Integer.toString(usage);
    }
}
