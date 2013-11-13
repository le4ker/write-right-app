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

import java.util.HashMap;

import android.database.Cursor;

/**
 * Implementation of a trainable prefix tree
 * @author Panos Sakkos
 */

public class Trie
{
    private int size;
    private int popularity;
    private HashMap<Character, Trie> subTries;

    public Trie()
    {
        size = 1;
        popularity = 0;

        subTries = new HashMap<Character, Trie>();
        
        for(char character : Predictor.latinLetters)
        {
    		subTries.put(character, null);
        	
        }
    }

    /**
     * Adds an new word in the prefix tree
     * @param word The word that will be inserted
     */

    public void Add(String word)
    {
        /* The word must be valid, because it's loaded from the Clean Wordnet file */
        
        assert ValidWord(word);

        /*Ignore capitals */

        word = word.toLowerCase();

        InnerAdd(word);
    }

    public void LoadFromDB(Cursor cursor)
    {
    	String word;
    	while(cursor.moveToNext())
    	{
    		word = cursor.getString(0);
    		this.Add(word);
    	}
    	
    	cursor.moveToFirst();
    }

    /**
     * Clears the prefix tree from any added words and information
     */

    public void Clear()
    {
        subTries.clear();
    }

    /**
     * Returns the size of the prefix subtree with root the given parameter letter
     * @param letter The char that indicates the target prefix subtree
     * @return
     */

    public int GetSubTrieSize(char letter)
    {
        /* Ignore case sensitivity */

        letter = Character.toLowerCase(letter);

        return subTries.get(new Character(letter)) == null ? 0 : ((Trie)subTries.get(new Character(letter))).Size();
    }

    /**
     * Returns the number of the words that the prefix tree contains
     * @return
     */
    
    public int Size()
    {
        return size;
    }

    /**
     * Informs the prefix tree that the given word was typed in the past and how many times.
     * The word given must be added in the past in the prefix tree
     * @param word The word that was typed
     * @param times How many times the word was typed
     */

     public void WasTyped(String word, int times)
     {
        if(word == null || word.length() == 0)
        {
            popularity += times;
            return;
        }

        popularity += times;

	/* Ignore case sensitivity */

        word = word.toLowerCase();
        
        char firstChar = word.charAt(0);
        String postfix = word.substring(1);

        /* The word typed in the past, must exist in the Trie */

        assert subTries.get(new Character(firstChar)) != null;

        ((Trie) subTries.get(new Character(firstChar))).WasTyped(postfix, times);
     }

     /**
      * Returns the prefix tree with root the character given as parameter
      * @param characterTyped Indicates the target prefix subtree
      * @return The prefix subtree that was indicated
      */

     public Trie GetSubTrie(char characterTyped)
     {
        /* Ignore case sensitivity */

         characterTyped = Character.toLowerCase(characterTyped);

         return ((Trie) subTries.get(new Character(characterTyped)));
     }

     public int GetPopularity(char nextLetter)
     {
        /* Ignore case sensitivity */

        nextLetter = Character.toLowerCase(nextLetter);

        return subTries.get(nextLetter) != null ? ((Trie) subTries.get(nextLetter)).GetPopularity() : 0;
     }

    /**
     * The actual Add method of the prefix tree. The public void Add(string word) is a wrapper that
     * checks for validity and converts the word to lower case and gives the word as parameter to this method
     * @param word The word that will be added
     */

    private void InnerAdd(String word)
    {
        if(word == null || word.length() == 0)
        {
            return;
        }

        char firstChar = word.charAt(0);
        String postfix = word.substring(1);

        if( subTries.get(firstChar) == null) //REVIEW
        {
            subTries.put(firstChar, new Trie());
        }

        ((Trie)subTries.get(firstChar)).InnerAdd(postfix);
        size++;
    }

    /**
     * Searches the prefix tree for the given word (for debug purpose)
     * @param word The word to be searched
     * @return If the word was found
     */

    public boolean Search(String word)
    {
        if(word == null || word.length() == 0)
        {
            return true;
        }

        char firstChar = word.charAt(0);

        if( subTries.containsKey(firstChar) == false)
        {
            return false;
        }
        else
        {
            String postfix = word.substring(1);
            return ((Trie)subTries.get(firstChar)).Search(postfix);
        }
    }

    /**
     * Returns the popularity of this node
     * @return
     */

    private int GetPopularity()
    {
        return popularity;
    }

    public static boolean ValidWord(String word)
    {
        if (word.contains("0"))
            return false;
        if (word.contains("1"))
            return false;
        if (word.contains("2"))
            return false;
        if (word.contains("3"))
            return false;
        if (word.contains("4"))
            return false;
        if (word.contains("5"))
            return false;
        if (word.contains("6"))
            return false;
        if (word.contains("7"))
            return false;
        if (word.contains("8"))
            return false;
        if (word.contains("9"))
            return false;
        if (word.contains("'"))
            return false;
        if (word.contains("."))
            return false;
        if (word.contains("-"))
            return false;
        if (word.contains("_"))
            return false;
        if (word.contains("/"))
            return false;
        if (word.contains("("))
            return false;
        if (word.contains(")"))
            return false;

        return true;
    }
}
