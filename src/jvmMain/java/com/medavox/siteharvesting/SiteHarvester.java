package com.medavox.siteharvesting;

import java.io.*;
import org.jsoup.nodes.*;
import org.jsoup.select.*;
import org.jsoup.*;
import java.io.PrintStream;
import java.net.*;
import java.util.regex.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.medavox.util.io.StringUtils;
import com.medavox.util.download.RobustDownloader;
import com.medavox.util.download.PageDownloadAction;


public class SiteHarvester
{
    //final String downloadFolder = "blogs";
    private PrintStream o = System.out;
    //commandline argument options
    static boolean originalsOnly = false;
    static boolean videosOnly = false;
    static boolean updatesMode = false;
    static boolean searchMode = false;
    static boolean tagPerDirMode = false;
    static boolean authorHashMode = false;
    static String authorHash = "";
    static String searchQuery = "";
    static String URLtag = "";
    
    int imagesDownloaded = 0;
    final int RETRY_LIMIT = 3;
    final int MAX_CONSECUTIVE_FRUITLESS_PAGES = 20;
    final int MAX_DEJA_VU_PAGES = 3;
    final int MIN_POSTS_PER_PAGE = 5;
    final int TIMEOUT = 6000;
    int DOWNLOAD_THREADS = 8;//can be reduced if we're in a mode where downloads are rare !normally 8
    int consecutiveFruitlessPages = 0;
    int dejaVuPages = 0;
    Map<String, String> localImages = new Hashtable<String, String>();
    Map<String, String> blacklist = new Hashtable<String, String>();
    String outputFolderName;
    Queue<String> downloadQueue = new ConcurrentLinkedQueue<String>();
    
    /*When true, each download thread will exit when download queue is empty, and they've finished their last download*/
    boolean finishFlag = false;
    
    /*When false, each download thread will finish as soon as its current download has ended.*/
    boolean downloadThreadsEnabled = true;
    private static boolean isFarmCall = false;
    

    public File guaranteeFolder(String... folders)
    {
        String folderName = folders[0];
        for(int i = 1; i < folders.length; i++)
        {
            folderName += File.separator + folders[i];
        }
        File folder = new File(folderName);
        if(!folder.exists())//check folder exists
        {
            System.out.println(folderName+" did not exist.");
            folder.mkdir();
        }
        if(!folder.isDirectory())//check folder name is not already used by a file
        {
            lazyExceptionHandler(new IOException("our output folder name is already used by a file!"));
        }
        return folder;
    }
    
    public SiteHarvester(URL url)
    {
        guaranteeFolder("blogs");
		//check for (and possibly create) output folder
        //check for local copies of images from previous runs

        //handle page number parsing here
        int pageNum = 1;//if we don't detect otherwise, default to page 1
        int startNum = 1;
        String raidURL = "";
        if(url.getPath().contains("/page/"))
        {
            String pageNumString = url.getPath().substring(url.getPath().indexOf("/page/")+6);
            try
            {
                pageNum = Integer.parseInt(pageNumString);
                startNum = pageNum;
            }
            catch(NumberFormatException nfe)
            {
                System.err.println("ERROR: "+url+" is a Malformed URL!");
                lazyExceptionHandler(nfe);
            }
            raidURL = url.toString().substring(0, url.toString().indexOf("/page/"));
        }

        if(raidURL.endsWith("/"))//strip trailing / if there's no /page/
        {
            raidURL = raidURL.substring(0, url.toString().length()-1);
        }
        
        if(raidURL.length() == 0)
        {
            raidURL = url.toString();
        }
        
        //start the media download threads
        DownloadThread[] downloaders = new DownloadThread[DOWNLOAD_THREADS];
        for(int i = 0; i < DOWNLOAD_THREADS; i++)
        {
            downloaders[i] = new DownloadThread(this);
            downloaders[i].start();
        }
        
        o.println("------ P A G E  "+pageNum+"  ------");
        //run main "raid" functionality on every valid /page/number
        while(raid(raidURL+"/page/"+pageNum) >= 1)
        {
            pageNum++;
            o.println("------ P A G E  "+pageNum+"  ------");
            o.println("(files downloaded/ing so far: "+(imagesDownloaded-downloadQueue.size())+")");
            o.println("(downloads in queue: "+downloadQueue.size()+")");
            //o.println("(files downloaded so far: "+imagesDownloaded+")");
        }
        
        //signal download threads to close once queue is empty
        finishFlag = true;
        try
        {
            for(int i = 0; i < DOWNLOAD_THREADS; i++)
            {
                downloaders[i].join();
            }
        }
        catch(InterruptedException e)
        {
            lazyExceptionHandler(e);
        }
    }

    /**The main per-page raid functionality. Called on each page of posts.
     * @return number of posts found on this page*/
    public int raid(String url)
    {
        String doc = RobustDownloader.download(new PageDownloadAction(url, TIMEOUT));
        //o.println("page length:"+doc.length());
        //o.println("page:"+doc);
        //o.println("saving page to disk:");
        //String saveMsg = download(url, false);


        String host = url.substring(0, url.indexOf("/", 7));//get basic host, eg sd.example.com
        //o.println("host:"+host);
        Pattern pat = Pattern.compile(host+"/post/[0-9]{8,13}");//regex to find post URLs
        String[] posts = StringUtils.findURLsInDoc(doc, pat);//get post-page links

        o.println("got "+posts.length+" posts");
        
        if(posts.length < 1)
        {
            return (originalsOnly ? -1 : 0);//exit early if we found nothing to process
            //return 0;
        }
        
        String[][] mediaPageLinks = new String[posts.length][];// >= 1 image URLs from each page
        //look for media files on post pages in parallel
        PostPageProcessor[] ppps = new PostPageProcessor[posts.length];
        for(int i = 0; i < posts.length; i++)
        {
            ppps[i] = new PostPageProcessor(posts[i], i+1, this);
            ppps[i].start();
        }
        for(int i = 0; i < ppps.length; i++)//wait for all threads to die before continuing
        {//prevents the next method trying to process data that isn't there yet
            try
            {
                ppps[i].join();
                mediaPageLinks[i] = ppps[i].getLinks();
            }
            catch(InterruptedException e)
            {
                lazyExceptionHandler(e);
            }
        }

        if(posts.length < MIN_POSTS_PER_PAGE && noDupURLs.length == 0)
        {
            consecutiveFruitlessPages++;
            if(consecutiveFruitlessPages == MAX_CONSECUTIVE_FRUITLESS_PAGES)
            {
                System.out.println("Fruitless Loop detected! Exiting loop...");
                return -1;
            }
        }
        else
        {
            consecutiveFruitlessPages = 0;
        }
        //pre-emptively add to the running download count, to avoid threads doing it (messy)
        //threads now DECREMENT the global counter if a download permanently fails
        imagesDownloaded += noDupURLs.length;

        //download harvested media file URLs parallely, but not TOO parallely
        for(int i = 0; i < noDupURLs.length; i++)
        {
            downloadQueue.add(noDupURLs[i]);
        }
        /*if(originalsOnly && mediaPageLinks.length == 0)
        {
            return -1;
        }*/
        return mediaPageLinks.length;
    }
    
    public void raidSinglePostPage(String url)
    {
        PostPageProcessor ott = new PostPageProcessor(url, 1, this);
        ott.start();
        try
        {
			ott.join();
			String[] item = ott.getLinks();
			//downloadQueue.add(item[0]);
			
			SiteHarvestDownloadAction td = new SiteHarvestDownloadAction(item[0], this);
			RobustDownloader.download(td);
		}
		catch(InterruptedException e)
		{
			lazyExceptionHandler(e);
		}
    }


    static void leh(Exception e)
    {
        lazyExceptionHandler(e);
    }
    /**Hooray for the Lazy Exception Handler! Even its acronym sounds half-arsed!*/
    static void lazyExceptionHandler(Exception e)
    {
        System.err.println("ERROR! meh, just dump it and exit.");
        e.printStackTrace();
        System.exit(1);
    }
}
