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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

public class DataBaseHelper extends SQLiteOpenHelper
{	 
    private static String DB_PATH = "/data/data/panos.sakkos.softkeyboard.writeright/databases/";
    private static String DB_NAME = "writeright.db";
 
    private SQLiteDatabase myDataBase; 
    private final Context myContext;
 
	String []wordsColumns = new String[3];
	String []essentialsColumns = new String[2];
	
	/* upgrade stuff */
	
	private final int THRESHOLD = 1500;
	private final float CLEAN_PERCENTAGE = 0.3f;
	
    /**
     * Constructor
     * Takes and keeps a reference of the passed context in order to access to the application assets and resources.
     * @param context
     */
    public DataBaseHelper(Context context) 
    {
 
    	super(context, DB_NAME, null, 1);
        this.myContext = context;
        
        wordsColumns[0] = "word";
        wordsColumns[1] = "usage";
        wordsColumns[2] = "timestamp";
        
        essentialsColumns[0] = "k";
        essentialsColumns[1] = "continuous_successes";
    }	
 
  /**
     * Creates a empty database on the system and rewrites it with your own database.
     * */
    public void createDataBase() throws IOException
    {
    	boolean dbExist = checkDataBase();
 
    	if(dbExist == false)
    	{
        	this.getReadableDatabase().close();
 
        	try 
        	{
    			copyDataBase();
    		} 
        	catch (IOException e)
        	{ 
        		throw new Error("Error copying database");
        	}
    	}
 
    }
 
    /**
     * Check if the database already exist to avoid re-copying the file each time you open the application.
     * @return true if it exists, false if it doesn't
     */
    private boolean checkDataBase()
    {
    	SQLiteDatabase checkDB = null;
 
    	try
    	{
    		String myPath = DB_PATH + DB_NAME;
    		checkDB = SQLiteDatabase.openDatabase(myPath, null, SQLiteDatabase.OPEN_READWRITE);
    	}
    	catch(SQLiteException e)
    	{
    		//database does't exist yet.
    	}
 
    	if(checkDB != null)
    	{
    		checkDB.close();
    	}
 
    	return checkDB != null ? true : false;
    }
 
    /**
     * Copies your database from your local assets-folder to the just created empty database in the
     * system folder, from where it can be accessed and handled.
     * This is done by transfering bytestream.
     * */
    private void copyDataBase() throws IOException
    {
    	//Open your local db as the input stream
    	InputStream myInput = myContext.getAssets().open(DB_NAME);
 
    	// Path to the just created empty db
    	String outFileName = DB_PATH + DB_NAME;
 
    	//Open the empty db as the output stream
    	OutputStream myOutput = new FileOutputStream(outFileName);
 
    	//transfer bytes from the inputfile to the outputfile
    	byte[] buffer = new byte[1024];
    	int length;
    	while ((length = myInput.read(buffer))>0){
    		myOutput.write(buffer, 0, length);
    	}
 
    	//Close the streams
    	myOutput.flush();
    	myOutput.close();
    	myInput.close();
    }
 
    public void openDataBase() throws SQLException
    {
        String myPath = DB_PATH + DB_NAME;
    	myDataBase = SQLiteDatabase.openDatabase(myPath, null, SQLiteDatabase.OPEN_READWRITE); 
    }
 
    @Override
	public synchronized void close() 
    {
 	    if(myDataBase != null)
    		    myDataBase.close();
 
    	    super.close();
	}
 
	@Override
	public void onCreate(SQLiteDatabase db) 
	{
	}
 
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) 
	{
	}
 
	/* Helper methods to interact with the database */

	public Cursor GetEssentials()
	{
		return myDataBase.query("Essentials", essentialsColumns, null, null, null, null, null);		
	}
	
	public void UpdateEssentials(int k, int continuousSuccesses)
	{
		myDataBase.execSQL("UPDATE Essentials SET k=" + Integer.toString(k) + ", continuous_successes=" + Integer.toString(continuousSuccesses) + " WHERE _id=0");
	}
	
	public Cursor SelectAllWords()
	{
		return myDataBase.query("Words", wordsColumns, null, null, null, null, null);
	}
	
	public void UpdateWords(String word, Statistics statistics)
	{
		myDataBase.execSQL("UPDATE Words SET usage=" + statistics.GetPopularity() + ", timestamp='" + Long.toString(statistics.GetTimestamp()) + "' WHERE word='" + word + "'");
	}
	
	public void AddNewWord(String word)
	{
		int count = myDataBase.query("Words", wordsColumns, null, null, null, null, null).getCount();
		myDataBase.execSQL("INSERT INTO Words VALUES (" + Integer.toString(count) + ", '"+ word +"', 0, '0')");	
	}
	
	public void UpgradeDatabase()
	{
		Cursor cursor = myDataBase.query("Words", wordsColumns, null, null, null, null, null);
		
		if(cursor.getCount() >= THRESHOLD)
		{
			/* Delete CLEAN_PERCENTAGE % of the last recently used words*/			
			
	    	ArrayList<Long> timestamps = new ArrayList<Long>();    	
			
			while(cursor.moveToNext())
			{
				timestamps.add(Long.parseLong(cursor.getString(2)));
			}

			Collections.sort(timestamps);
			
			int threshold = (int) (cursor.getCount() * CLEAN_PERCENTAGE);
			for(int i = 0; i < threshold; i++)
			{
				DeleteWordBeforeTimestamp(timestamps.get(i));				
			}
		}
	}
	
	private void DeleteWordBeforeTimestamp(long timestamp)
	{
		myDataBase.execSQL("DELETE FROM Words WHERE timestamp ='" + Long.toString(timestamp) +"';");
	}
}










