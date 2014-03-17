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

import java.util.HashMap;
import android.database.Cursor;
import android.util.Log;

/**
 * The class that produces the predictions for the next letter
 * @author Panos Sakkos
 */

public class Predictor
{
    private Trie root;
    private HashMap<String, Statistics> knowledge;

    /* Current word typed */

    private String word;
    private int wordsTyped;

    /*Prefix tree for the current word typed */

    private Trie subTrie;
    public static char[] latinLetters = {
                            'a','b', 'c', 'd', 'e', 'f', 'g',
		               	    'h','i', 'j', 'k', 'l', 'm', 'n',
		             	    'o', 'p', 'q', 'r', 's', 't', 'u',
		               	    'v', 'w', 'x', 'y', 'z'
				   			};
    private int personalizationFactor = 1;
    private boolean unknownWord;

    private boolean idle = false;
    DataBaseHelper db;
    
    public Predictor(DataBaseHelper db)
    {
    	this.db = db;
    	Cursor cursor = db.SelectAllWords();
    	
    	root = new Trie();
    	root.LoadFromDB(cursor);
    	
    	cursor = db.GetSubLanguage();
    	root.LoadSubLanguageFromDB(cursor);

		knowledge = new HashMap<String, Statistics>();

        wordsTyped = 0;

        word = "";
        subTrie = root;
        unknownWord = false;    	
        cursor = db.SelectAllWords();
        String key;
        while(cursor.moveToNext())
        {
        	key = cursor.getString(0);
        	Statistics statistics = new Statistics(cursor.getInt(1), Long.parseLong(cursor.getString(2)));
        	
        	knowledge.put(key, statistics);
        }
        
        GetTrained();
    }
    
    /**
     * Returns a dictionary containing the probability of each possible next character
     * @return containing pairs of possible next letter and it's probability to be typed
     */

    public HashMap<Character, Float> GetPredictions()
    {
        int popularity;
        int postfixesCounter;
        float evaluation;

        float evaluationSum = 0;

        /* If subTrie is null, then the word being typed is not in the dictionary */

        if(subTrie == null || idle)
        {
            unknownWord = true;
            return new HashMap<Character, Float>();
        }

        /* Compute total evaluation amount */

        for(char possibleNextLetter : latinLetters)
        {
            popularity = subTrie.GetPopularity(possibleNextLetter);
            postfixesCounter = subTrie.GetSubTrieSize(possibleNextLetter);

            evaluation = Evaluate(popularity, postfixesCounter);

            evaluationSum += evaluation;
        }

        HashMap<Character, Float> predictions = new HashMap<Character, Float>();

        /* If there are no predictions */

        if(evaluationSum == 0)
        {
            for(char letter : latinLetters)
            {
                predictions.put(new Character(letter), new Float(0.0));
            }

            return predictions;
        }

        else
        {
            for(char possibleNextLetter : latinLetters)
            {
            	if (subTrie.IsInSubLanguage(possibleNextLetter))
            	{
	                predictions.put(new Character(possibleNextLetter), (float) 1.0f);       
	                Log.d("DEBUG", new Character(possibleNextLetter) + " is in sublanguage!");
            	}
            	else
            	{
	                popularity = subTrie.GetPopularity(possibleNextLetter);
	                postfixesCounter = subTrie.GetSubTrieSize(possibleNextLetter);
	
	                evaluation = Evaluate(popularity, postfixesCounter);
	
	                /* Normalize evaluation in order to express probability */
	
	                predictions.put(new Character(possibleNextLetter), (float) Math.round(evaluation / evaluationSum * 100) / 100 );
            	}
            }
        }

        return predictions;
    }

    /**
     * Informs the predictor that the parameter letter is typed. In order to
     * give right predictions this method must be called whenever  there is a
     * new input, even if this input is space
     * @param character
     */

    public void CharacterTyped(char character) throws Exception
    {
    	/* Ignore invalid input */
    	
    	if(Trie.ValidWord((new Character(character)).toString()) == false)
    	{
            Log.d("DEBUG", "CHARACTER IGNORED");
    		return;
    	}

        word += character;

        /* If subTrie is null, then the word being typed is not in the dictionary */

        if (subTrie != null)
        {
        	subTrie = subTrie.GetSubTrie(character);
        }
        else
        {
        	/* Unknown word detected! */

        	unknownWord = true;
        }
    }

    /**
     * If the user cancels the prediction, this method MUST be called in order
     * to inform the prediction infrastructure
     */

    public void PredictionCanceled()
    {
        word = "";
        subTrie = root;
        unknownWord = false;
        idle = true;
    }

    /**
     * Trains the prefix tree with the knowledge in order to give back
     * personalized data
     */

    private void GetTrained()
    {
        for(Object key : knowledge.keySet())
        {
            Statistics value = (Statistics) knowledge.get(key);
            root.WasTyped(key.toString(), value.GetPopularity());
            wordsTyped += value.GetPopularity();
        }
    }

    /**
     * This method is called by the CharacterTyped method when the typed
     * character is space (' '), in order to store the knowledge drained from this action
     */

    public void WordTyped() throws Exception
    {
    	if(word == null || word.length() == 0)
    		return;

        word = word.toLowerCase();

        if(knowledge.containsKey(word) == false)
        {
            Statistics statistics = new Statistics();
            knowledge.put(word, statistics);
        }
        else
        {
            ((Statistics) knowledge.get(word)).WordTyped();
        }

        /* If the word that was typed is known */

        if (unknownWord == false)
        {
            /* Train the Trie with the new knowledge */

            root.WasTyped(word, 1);
        }
        else
        {
            /* If the word is unknown, add it to the Trie */

            root.Add(word);

            /* Add word to wordnet */

            AddNewWordToWordNet();
        }

        wordsTyped++;

        word = "";
        subTrie = root;
        unknownWord = false;
    }


    /**
     * Given the popularity of a possible next letter and the number of the
     * postfixes that start with this letter, returns an evaluation
     * @param popularity Indicates the popularity of the possible next letter
     * @param prefixesCounter Indicates the number of the postfixes that start
     * with the target possible next letter
     * @return Evaluation for the target possible next letter
     */

    private float Evaluate(int popularity, int prefixesCounter)
    {
        float usageRatio = personalizationFactor * wordsTyped / root.Size();

        /* If there are more typed words than the stored words (usageRatio > 1 ),
        * the evaluation is computed based only on letter's popularity
        */

        if (usageRatio > 1)
        {
            usageRatio = 1;
        }

        return usageRatio * popularity + (1 - usageRatio) * prefixesCounter;
    }

    private void AddNewWordToWordNet() throws Exception
    {
    	db.AddNewWord(word);
        Log.d("DEBUG", "NEW WORD ADDED TO WORDNET");        	
    }

    private void AddNewWordToWordNet(String newWord) throws Exception
    {
    	/* prevent bad use */
    	
    	if(knowledge.containsKey(newWord))
    		return;

    	db.AddNewWord(newWord);
        Log.d("DEBUG", "NEW WORD ADDED TO WORDNET");        	
    }
        
    public void Save()
    {
        for(Object key : knowledge.keySet())
        {
        	if(knowledge.get(key).GetPopularity() > 0)
        	{
        		db.UpdateWords(key.toString(), knowledge.get(key));
        	}
        }    	
    }
    
    public void SetNotIdle()
    {
    	idle = false;
    	word = "";
        subTrie = root;
        unknownWord = false;
    }
    
    public void SetIdle()
    {
    	word = "";
    	idle = true;
    }
    
    public boolean IsIdle()
    {
    	return idle;
    }
    
    public void LearnNewWord(String newWord)
    {    	
        Statistics statistics = new Statistics();
        knowledge.put(newWord, statistics);
        root.Add(newWord);

        try 
        {
			AddNewWordToWordNet(newWord);
		}
        catch (Exception e) 
        {
        	Log.d("DEBUG", "ERROR WHILE ADDING NEW WORD TO WORDNET AFTER PREDICTION CANCELLING");
		}
    }
}
