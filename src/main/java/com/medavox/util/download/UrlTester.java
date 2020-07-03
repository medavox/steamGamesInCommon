package com.medavox.util.download;

import java.net.*;
import java.io.*;
import java.nio.charset.*;
import java.util.*;

import com.medavox.util.io.Bytes;


/**Manually de-garbles redirection URLs encoded in UTF-8, which Java mis-handles.
 * 
 * When receiving a redirect location (from the HTTP header field "Location") encoded in UTF-8,
 * Java messes up and double-encodes it into UTF-16 over UTF-8
 * (each UTF-8 byte is treated as an ASCII character, which is re-encoded into UTF-16).
 * 
 * The bug is in  java.net.HttpURLConnection.getHeaderField(String). 
 * Java.lang.String is almost always encoded in UTF-16, so there's no way to get the "real",
 * un-garbled "Location" headerfield value using getHeaderField(),
 *  without intercepting the HTTP header bytes manually. Or perhaps changing String's encoding.
 * 
 * In the meantime, I need to be able to handle redirects encoded in UTF-8.
 * So this class de-garbles the badly-encoded String returned by getHeaderField(String),
 * then percent encodes it and returns it. This makes it palatable for use.
 * In other words, redirectUTF(Strings) handles redirection URLs encoded in UTF-8,
 * which Java can't do.*/
public class UrlTester
{
    public static PrintStream o = System.out;
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    
    
    /**The main point of this class.*/
    public static String redirectUTF(String urlShort)
    {
        try
        {
            URL url = new URL(urlShort);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setInstanceFollowRedirects(false);//don't rely on native redirection support
            
            conn.connect();
            int status = conn.getResponseCode();
            if(status != 301)
            {
				if(status == 200)
				{
					//System.err.println("HTTP 200 OK response received! I smell a rat...");
					return urlShort;
				}
				else
				{
					throw new Exception("received HTTP status code \""+status+"\" != 301!");
				}
            }
            
            String redirUrl = conn.getHeaderField("Location");
            
            //todo:find out which string to put in the getBytes() method: "UTF-8", or "UTF-16"
            byte[] converted = fixUTF(redirUrl.getBytes());
            redirUrl = new String(converted, "UTF-8");//de-garble mishandled UTF-8 string
            
            redirUrl = redirUrl.substring(0, redirUrl.length()-4);//remove "#_=_" from end
            redirUrl = percentEncodeUTF(redirUrl);//percent-encode any non-ASCII bytes
            return redirUrl;
        }
        catch(Exception e)
        {
            e.printStackTrace();
            return "";
        }
    }
    
    /**I THINK this method converts UTF-8 bytes wrongly re-encoded into pairs of UTF-16 bytes,
     * but Unicode is so confusing, I'm even sure if that's the name for it.*/
    public static byte[] fixUTF(byte[] bytes)
    {
        int i = 0;
        byte[] ret = new byte[bytes.length];
        int retLen = 0;
        while( i < bytes.length)
        {
            /**
             * for every pair of bytes where byte0 matches 110xxxxx and byte1 matches 10xxxxxx
             * (which can either be described as a 2-byte UTF-8 codepoint,
             * or possibly a UTF-16 2-byte encoding (i'm not sure),
             * 
             * take the lower 2 bits of byte0 (the lower 6 would normally contain data,
             * but in this case bits 5-3 inclusive (3 bits) are just zero padding
             * to make our wrongly re-encoded 8 UTF-8 bits fit
             * into the 11-bit data space of 2-byte UTF-8/UTF-16)
             * and the lower 6 bits of byte1,
             * and add the lower bits of byte0 into the higher 2 bits of byte1.
             * 
             * eg:
             * 
             * byte0 = 110nnnMM
             * byte1 = 10PPPPPP
             * 
             * where n = the padding zeros, which could contain data in another scenario
             * 
             * new byte = MMPPPPPP*/
            if( Bytes.testBit(7, bytes[i])
            &&  Bytes.testBit(6, bytes[i])
            && !Bytes.testBit(5, bytes[i])
            &&  Bytes.testBit(7, bytes[i+1])
            && !Bytes.testBit(6, bytes[i+1]))//if bit matches 110xxxxx && nextbyte matches 10xxxxxx
            {
                byte newByte = (byte)(bytes[i] << 6);
                byte part2 = (byte)(bytes[i+1] & (byte)0x3F);
                newByte = (byte)(newByte | part2);
                ret[retLen] = newByte;
                i+=2;
                retLen++;
            }
            else//sod validation, it's too late for that
            {
                ret[retLen] = bytes[i];
                retLen++;
                i++;
            }
        }
        byte[] shortret = new byte[retLen];
        //copy shortened byte array
        for(int j = 0; j < retLen; j++)
        {
            shortret[j] = ret[j];
        }
        return shortret;
    }
    
    /**For every non-ASCII byte (unsigned byte > 0x7F),
     * replace byte with bytes representing this string: "%XX", 
     * where XX is the byte's value as hexadecimal.
     * eg byte 0x58 becomes "%58".getBytes() */
    public static String percentEncodeUTF(String in)
    {
        try
        {
            byte[] byc = in.getBytes("UTF-8");
            byte[] percentByte = "%".getBytes("UTF-8");
            byte[] proc = new byte[byc.length*3];
            int retLen = 0;
            int i = 0;
            
            if(percentByte.length > 1)
            {
                o.println("AAARRRGHHHHH");
            }
            while(i < byc.length)
            {
                if(Bytes.testBit(7, byc[i]))//if bit matches 1xxxxxxx, ie is not ascii/UTF-8 single-byte encoding
                {
                    proc[retLen] = percentByte[0];
                    proc[retLen+1] = byteToHex(byc[i]).getBytes()[0];
                    proc[retLen+2] = byteToHex(byc[i]).getBytes()[1];
                    retLen += 3;
                }
                else//sod validation, it's too late for that
                {
                    proc[retLen] = byc[i];
                    retLen++;
                }
                i++;
            }
            byte[] retty = new byte[retLen];
            for(int j = 0; j < retLen; j++)
            {
                retty[j] = proc[j];
            }
            return new String(retty, "UTF-8");
        }
        catch(UnsupportedEncodingException uee)
        {
            uee.printStackTrace();
            System.exit(1);
        }
        return "";//never reached
    }
    
    public static String byteToHex(byte byt)
    {
        char[] hexChars = new char[2];
        int v = byt & 0xFF;
        hexChars[0] = hexArray[v >>> 4];
        hexChars[1] = hexArray[v & 0x0F];

        return new String(hexChars);
    }
}
