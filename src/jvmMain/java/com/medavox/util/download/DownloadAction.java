package com.medavox.util.download;

/**base class for code implementing download functionality.
 * If it downloads something, it should extend this.*/
public abstract class DownloadAction
{
	public String src;
	public DownloadAction(String src)
	{
		this.src = src;
	}
	public String getSource()
	{
		return src;
	}
	public abstract String download() throws Exception;
	
	/**Provides a mechanism for code to run upon an error.
	 * Override this if you need to perform some tasks during a failed request
	 * @param e the causing the exception
	 * @param hca the derived HttpCodeAction, letting you know what's going to be done
		@return true if you want to stop the retry process, false otherwise.*/
	public boolean errorCallback(Exception e, HttpCodeAction hca)
	{
		return false;
	}
	public int getRetryLimit()
	{
		return 3;
	}
}
