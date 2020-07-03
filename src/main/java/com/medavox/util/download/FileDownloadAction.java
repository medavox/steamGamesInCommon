package com.medavox.util.download;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;
import java.io.File;

import java.net.URL;


public class FileDownloadAction extends DownloadAction
{
    File destFile;
    public FileDownloadAction(String src, File destFile)
    {
        super(src);
        this.destFile = destFile;
    }

    public String download() throws Exception
    {
        URL url = new URL(src);//Open URL Stream
        InputStream in = url.openStream();
        
        OutputStream out = new BufferedOutputStream(new FileOutputStream(destFile));
        //new chunk downloading seems faster
        byte[] chunk = new byte[4096];//chunk size - currently 4KB
        int bytesRead = 0;
        while ((bytesRead = in.read(chunk)) != -1)
        {
            out.write(chunk, 0, bytesRead);
        }
        in.close();
        out.close();
        return "success";
    }
}
