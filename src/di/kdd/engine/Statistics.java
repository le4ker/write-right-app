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

package di.kdd.engine;

/**
 * A class that keeps the usage statistics about the words that were typed
 * In the future, location and time will also be added.
 * @author Panos Sakkos
 */

import java.util.List;
import java.util.ArrayList;

public class Statistics
{
    private int usage;
    private List<Long> timestamps;

    public Statistics()
    {
        this.usage = 1;
        timestamps = new ArrayList<Long>();
        timestamps.add(0, Long.valueOf((System.currentTimeMillis())));
    }
    
    
    public Statistics(int usage)
    {
        this.usage = usage;
        timestamps = new ArrayList<Long>();
        timestamps.add(0, Long.valueOf((System.currentTimeMillis())));
    }

    public Statistics(int usage, long timestamp)
    {
    	this.usage = usage;
        timestamps = new ArrayList<Long>();
        timestamps.add(0, Long.valueOf(timestamp));
    }
    
    /**
     * This method must be called when the word that the Statistics instance belongs is typed
     */

    public void WordTyped()
    {
        usage++;
        timestamps.add(0, Long.valueOf((System.currentTimeMillis())));
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
    	return timestamps.get(0);
    }

    public List<Long> GetTimestamps()
    {
    	return timestamps;
    }

    public void AddTimestamp(long timestamp)
    {
    	timestamps.add(0, Long.valueOf(timestamp));
    }
    
    @Override public String toString()
    {
        return Integer.toString(usage);
    }
}
