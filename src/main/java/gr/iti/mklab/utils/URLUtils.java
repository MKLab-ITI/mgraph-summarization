package gr.iti.mklab.utils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;

public class URLUtils {

	public static String expand(String shortUrl) throws IOException {
		int redirects = 0;
		HttpURLConnection connection;
		while(true && redirects < 4) {
			try {
				URL url = new URL(shortUrl);
				connection = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY); 
				connection.setInstanceFollowRedirects(false);
				connection.setReadTimeout(2000);
				connection.connect();
				String expandedURL = connection.getHeaderField("Location");
				if(expandedURL == null) {
					return shortUrl;
				}
				else {
					shortUrl = expandedURL;
					redirects++;
				}    
			}
			catch(Exception e) {
				return null;
			}
		}
		return shortUrl;
    }
	
}
