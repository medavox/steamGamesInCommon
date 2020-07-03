package com.medavox.util.io;

import java.util.Set;
import java.util.Scanner;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.File;
import java.io.FileReader;

public class StringUtils
{
    
    public static String[]findURLsInDoc(String page, Pattern reggie, Set<String> postURLs)
    {
        Set<String> matches = new HashSet<String>();//store in a set to remove exact duplicates
        
        Matcher cherche = reggie.matcher(page);
        while(cherche.find())//if we found something
        {
            String match = page.substring(cherche.start(), cherche.end());
            //System.out.println("match found:"+match);
            
            //only add post if its URL hasn't been seen before
            if(postURLs != null)
            {
				if (!postURLs.contains(match))
				{
					matches.add(match);
					postURLs.add(match);
				}
            }
            else
            {
				matches.add(match);
			}
        }
        //cast the set to an array, and return
        String[] retval = new String[matches.size()];
        return matches.toArray(retval);
    }
    
    /**Find all substrings matching the supplied regex Pattern, in the supplied string.
     * Finds one or more URLs in the supplied document, according to supplied regex.
     * @param page the string to search in
     * @param reg the regular expression to search for
     * @return a list of matching substrings*/
    public static String[] findURLsInDoc(String page, Pattern reg)
    {
        return findURLsInDoc(page, reg, null);
    }
    
    /**Reads the supplied (plaintext) file as a string and returns it.
     * @File f the supplied file. This MUST be a plaintext file. 
     * @return the contents of the file, as a String.*/
    public static String fileToString(File f)
    {
        if(!f.isFile())
        {
            throw new IllegalArgumentException("Supplied File object must represent an actual file.");
        }
        try
        {
            FileReader fr = new FileReader(f);
            char[] tmp = new char[(int)f.length()];
            char c;
            int j = 0;
            for(int i = fr.read(); i != -1; i = fr.read())
            {
                c = (char)i;
                tmp[j] = c;
                j++;
            }
            fr.close();
            String ret = new String(tmp);
            return ret;
        }
        catch(Exception e)
        {
            System.err.println("failed to read file: \""+f.getName()+"\"!");
            e.printStackTrace();
            return "";
        }
    }
    
    /**get a boolean response from a yes-or-no question. True for yes, false for no.
     * @param questionText the text to display as the question.*/
    public static boolean askQuestion(String questionText)
    {
        //String[] yesses = {"ye", "ya", "yu"};
        //String[] noes = {"no", "na"};
        //String[] validResponses
        Scanner sc = new Scanner(System.in);
        String reply = "";
        while(!reply.toLowerCase().equals("y")
        && !reply.toLowerCase().startsWith("ye") 
        && !reply.toLowerCase().startsWith("ya") 
        && !reply.toLowerCase().startsWith("yu") 
        && !reply.equalsIgnoreCase("n")
        && !reply.toLowerCase().startsWith("no")
        && !reply.toLowerCase().startsWith("na") )
        {//keep asking until they give a valid response
            System.out.print(questionText);
            reply = sc.nextLine();
        }
        if(reply.length() == 0
        || reply.toLowerCase().equals("y")
        || reply.toLowerCase().startsWith("ye") 
        || reply.toLowerCase().startsWith("ya") 
        || reply.toLowerCase().startsWith("yu") )
        {
            return true;
        }
        else if(reply.equalsIgnoreCase("n")
        || reply.toLowerCase().startsWith("no")
        || reply.toLowerCase().startsWith("na"))
        {
            return false;
        }
        else
        {//if they managed to break this,
        //then they probably aren't even interested in the question itself, so it's a no
            System.out.println("you broke a simple yes-or-no question! Well done!\nDo you, or do you want to work in QA?");
            return false;
        }
    }
}
