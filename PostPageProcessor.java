package com.medavox.siteharvesting;


import java.util.List;
import java.util.LinkedList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.jsoup.select.Elements;
import org.jsoup.nodes.Element;
import org.jsoup.Jsoup;

import com.medavox.util.io.StringUtils;
import com.medavox.util.download.RobustDownloader;
import com.medavox.util.download.PageDownloadAction;

/**Looks for media links in a post page.
 * Gets the URLs of all images on a post page
     * Now handles videos too!*/
public class PostPageProcessor extends Thread
{
    String postURL;
    String[] links;
    SiteHarvester tr;
    int i;//our post number on the current post-page
    public PostPageProcessor(String postURL, int i, SiteHarvester tr)
    {
        this.postURL = postURL;
        this.tr = tr;
        this.i = i;
    }
    
    /**After the thread has finished processing, this is called to get the resulting data.*/
    public String[] getLinks()
    {
        return links;
    }

    public void run()
    {
        String postPage = RobustDownloader.download(new PageDownloadAction(postURL, tr.TIMEOUT));
        if(postPage.equals("error"))//failed to retrieve post page,
        {//and other error catching failed
            System.err.println("ERROR: failed to download "+postURL);
            return;//move on
        }
        
        if(SiteHarvester.originalsOnly)
        {
            try
            {
                //warning: the following line does not work with custom URLs!
                //System.out.println("blog name :"+blogName);
                if(!isOriginalPost(postPage, blogName))
                {
                    links = new String[0];//initialise links to empty array
                    return;//exit early if we found nothing to process
                }
            }
            catch(Exception e )
            {
                System.err.println("error on post ("+postURL+")");
                e.printStackTrace();
            }
        }
        
        if(SiteHarvester.searchMode)
        {
            String page = getBody(postPage);
            page = page.toLowerCase();
            String sqLow = SiteHarvester.searchQuery.toLowerCase();
            if(!page.contains(sqLow))
            {//this page doesn't contain the search string, so
                links = new String[0];//initialise links to empty array
                return;//exit early since we didn't find the required search term
            }
        }
        //String imgPrint = "";
        String[] imgLinks = new String[0];
		Element bawdy = Jsoup.parse(postPage).body();
		String vidConLink = getVideoPageUrl(postPage, bawdy);
        
		if(tr.authorHashMode)
		{ //remove links not containing the right author hash
			List<String> authorHashLinks = new LinkedList<String>();
			//System.out.println("links before:"+links.length);
			for(String linkUrl : links)
			{	
				String ah = getAuthorHash(linkUrl);
				//System.out.println("link URL: "+linkUrl);
				//System.out.println("author hash: "+ah);
				if(ah.equals(tr.authorHash))
				{
					authorHashLinks.add(linkUrl);
				}
			}
			String[] newLinks = new String[authorHashLinks.size()];
			links = authorHashLinks.toArray(newLinks);
			//System.out.println("links after:"+links.length);
		}

        //totalURLsFound += mediaLinks[i].length;
        String equiWidth = ("    " + i).substring((""+i).length());
        //if there's some vid and/or img links, print how many, iff not, print "files: 0"
        //System.out.println("post "+equiWidth+" ("+postURL+") "+(il > 0 ? "images: "+il : "")+vidPrint+(vl <1 && il < 1 ? "files:  0" : ""));
        //no longer print lines about posts containing no images or videos
        /*if(vl > 0 || imgLinks.length > 0)
        {
            System.out.println("post "+equiWidth+" ("+postURL+") "+imgPrint+" "+vidPrint);
        }*/
        /*if(links.length > 0)
        {
			System.out.println("post "+equiWidth+" ("+postURL+") links: "+links.length);
		}*/
    }
}
