package com.medavox.util.download;

import org.jsoup.Jsoup;

public class PageDownloadAction extends DownloadAction
{
    private int timeout;
    public PageDownloadAction(String src, int timeout)
    {
        super(src);
        this.timeout = timeout;
    }
    public String download() throws Exception
    {
        return Jsoup.connect(src).timeout(timeout).get().toString();
    }
    
    public int getTimeout()
    {
		return timeout;
	}
}
