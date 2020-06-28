package com.medavox.siteharvesting;

import com.medavox.util.download.*;
/**Downloads an image from the supplied URL, and saves it to disk, in the supplied named folder.
 * @param outputFolderName the name for the folder to save images to.
 *     Program will halt if there is a file with that name.
 * @param src URL of image to download. Downloaded image will have this filename.
 * @return true if image with the derived filename already exists, false otherwise */
public class DownloadThread extends Thread
{
    SiteHarvester tr;
    int waitTime;
    
    public DownloadThread(SiteHarvester tr, int waitTime)
    {
        this.tr = tr;
        this.waitTime = waitTime;
    }
    
    public DownloadThread(SiteHarvester tr)
    {
        this.tr = tr;
        waitTime = 250;
    }
    
    public void run()
    {
        while(tr.downloadThreadsEnabled)
        {
            //spins until there is something in the queue
            if(!tr.downloadQueue.isEmpty())
            {
                String next = tr.downloadQueue.remove();
                
                SiteHarvestDownloadAction td = new SiteHarvestDownloadAction(next, tr);
                
                RobustDownloader.download(td);
                if(tr.finishFlag)//while we're still downloading at the end, give some indication files left
                {
                    System.out.println("Files left in queue:"+tr.downloadQueue.size());
                }
            }
            else if(tr.finishFlag)//this thread's been told to exit when the download queue is empty 
            {
                break;
            }
            else//wait before checking again, so as not to waste CPU cycles
            {
                try
                {
                    sleep(waitTime);
                }
                catch(InterruptedException ie)
                {
                    System.err.println("Can't a thread catch a few ms sleep around here?!");
                    ie.printStackTrace();
                }
            }
        }
    }
}

