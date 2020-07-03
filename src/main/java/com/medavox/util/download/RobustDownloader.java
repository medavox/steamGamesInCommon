package com.medavox.util.download;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import java.net.URL;
import java.net.SocketTimeoutException;
import java.net.SocketException;
import java.net.UnknownHostException;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;


import org.jsoup.Jsoup;
import org.jsoup.HttpStatusException;

public class RobustDownloader
{
    /*This is the hardest part to make reliable.
     * The internet is an unreliable place.*/
    /**Downloads files.
     * @param src The URL to download
     * @param pageMode if true, URL points to an html page; return its contents as a string.
     * If false, save the file at the URL to disk.
     * @return in pageMode, returns the contents of the html page at the URL. Otherwise returns "success"*/
    public static String download(DownloadAction da)
    {
        int retries = 0;
        String src = da.getSource();
        int RETRY_LIMIT = da.getRetryLimit();

    retry:
        while(true)//keep trying to download the page until successful,
        {//retrying on SocketTimeoutException or IOException
            try
            {//do whatever specific download magic needs to be tried
                return da.download();
            }
            catch(Exception e)
            {
				System.err.println(e);
                //System.err.println(e.getClass().getSimpleName());
                System.err.println("causing URL:"+src);
                //System.err.println("details:"+e);
                HttpCodeAction hca = HttpCodeAction.fromException(e);
                if(hca == null)
                {//this is just a normal Exception
                //we would've let old leh handle it, but we left it behind in tr
                    e.printStackTrace();
                    System.exit(1);
                }
                //System.err.println("TIMEOUT getting "+src+"; trying again...");
                //System.err.print("FAILED to get "+src+"\n");//filenotfound
                
                if(e instanceof SocketException
                || e instanceof org.jsoup.HttpStatusException)
                {//could be a malformed utf url; do further setup to check
					int statusCode = ((HttpStatusException)e).getStatusCode();
					if( statusCode == 502 || statusCode == 400)//malformed redirect request
					{//caused by java's UTF-16 over UTF-8 HTTP header bug
						//replace the redirect URL with the manually-redirected one
						//System.err.println("link before fix: "+src);
						src = UrlTester.redirectUTF(src);
						//bug:we're only changing the src variable outside the downloadAction, not the one inside it
						//which is actually used for the downloading.
						//options:
						//1. make DownloadAction mutable, and change it (yugh)
						//2. create a new DownloadAction here, with the new src String
						//System.err.println("link after fix:  "+src);
						//System.err.println("DownloadAction type:  "+da.getClass());
						if(da instanceof PageDownloadAction)
						{
							int timeOut = ((PageDownloadAction)da).getTimeout();
							da = new PageDownloadAction(src, timeOut);
							RETRY_LIMIT = da.getRetryLimit();
						}
					}
                }
                //if the overridden errorCallback says to, exit here
                if (da.errorCallback(e, hca))
                {
					break retry;
				}
                switch(hca)
                {
                    case RETRY:
                        System.err.print("retrying...\n");
                        continue retry;
                    
                    case LIMITED_RETRY:
                        if(retries < 3)
                        {
                            retries++;
                            System.err.print("retrying "+retries+" of "+RETRY_LIMIT+"...\n");
                            continue retry;
                        }
                        //fall through to MOVE_ON case, if retries >= 3
                    
                    case MOVE_ON:
                        System.err.println("FAILED "+(retries > 1 ? retries+" times" : "")+" to get "+src+"; moving on...");
                        retries = 0;
                        
                        break retry;
                    
                    default://panic or anything else = panic
                        e.printStackTrace();
                        System.exit(1);
                }
            }
        }
        return "error";
    }
}
