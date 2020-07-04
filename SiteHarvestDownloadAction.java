package com.medavox.siteharvesting;

import com.medavox.util.download.FileDownloadAction;
import com.medavox.util.download.HttpCodeAction;

import java.io.File;


public class SiteHarvestDownloadAction extends FileDownloadAction
{
    private SiteHarvester tr;
    public SiteHarvestDownloadAction(String src, SiteHarvester tr)
    {//are you fucking happy now, java?
        super(src, new File(tr.outputFolderName + (tr.tagPerDirMode ? File.separator + tr.URLtag : "" ) + (tr.searchMode ? File.separator + tr.searchQuery : "" ) + File.separator + src.substring(src.lastIndexOf("/")+1, src.length())));
        String searchOutputDir = (tr.searchMode ? File.separator + tr.searchQuery : "" );
        String tagDir = (tr.tagPerDirMode ? File.separator + tr.URLtag : "" );
        
        //this.destFile = new File(tr.outputFolderName + tagDir + searchOutputDir + File.separator + name);
        
        this.src = src;
        this.tr = tr;
    }

    public String download() throws Exception
    {
        int indexname = src.lastIndexOf("/");
        String name = src.substring(indexname+1, src.length());
        
        String finalDest = tr.outputFolderName + File.separator + name;
        
        String ret = super.download();
        
        tr.localImages.put(tr.getImageHash(name), finalDest);
        System.out.println("downloaded "+finalDest);
        return ret;
    }

	@Override
    public boolean errorCallback(Exception e, HttpCodeAction hca)
    {
        if(hca == HttpCodeAction.MOVE_ON)
        {//download failed; remove pre-emptive count of this file
            tr.imagesDownloaded--;
        }
        return false;
    }
    
    @Override
    public int getRetryLimit()
    {
        return tr.RETRY_LIMIT;
    }

}
