package gr.iti.mklab.dao;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;

import com.twitter.Extractor;

import gr.iti.mklab.models.Item;

public class FileDAO {

	
//	public static String parse(String url) {
//		try {
//			String expandedUrl = expand(url);
//			Response response = Jsoup.connect(expandedUrl).execute();
//			Document doc = response.parse();
//			
//			// extract username
//			String mediaURL = doc.select(".media").attr("data-url");
//			if(mediaURL != null) {
//				mediaURL = mediaURL.replaceAll(":large", "");
//			}
//			
//			return mediaURL;
//		} catch (IOException e) {
//			e.printStackTrace();
//			return null;
//		}
//	}
	
//	public static void process(Item item) {
//		String text = item.getText();
//		
//		text = text.replaceAll( "([\\ud800-\\udbff\\udc00-\\udfff])", "");
//		text = text.replaceAll( "([^\\x00-\\x7f-\\x80-\\xad])", " ");
//		item.setCleanText(text);
//	
//		text = Jsoup.parse(text).text();
//		text = text.replaceAll("&", " and ");
//		
//		//text = text.replaceAll("&amp;", " and ");
//		//text = text.replaceAll("&lt;", " ");
//		//text = text.replaceAll("&gt;", " ");
//		//text = text.replaceAll("&quot;", " ");
//		
//		if(posExtractor != null) {
//			List<TaggedToken> posTags = posExtractor.getPosTags(text);
//			String posTagsString = posExtractor.posTagsString(posTags);
//			Set<String> pn = posExtractor.getProperNouns(posTags);
//		
//			item.addProperNouns(pn);
//			item.setPosTags(posTagsString);	
//		}
//		
//		if(entitiesExtractor != null) {
//			try {
//				Collection<NamedEntity> entities = entitiesExtractor.extractEntities(text);
//				item.addNamedEntities(entities);
//			} catch (Exception e) {	}
//		}
//	}
	
	public static Map<String, Item> getItems(String filename) throws FileNotFoundException, IOException {
		
		Extractor extractor = new Extractor();
		SimpleDateFormat formatter = new SimpleDateFormat("KK:mm a - dd MMM yyyy");

		Iterable<String> lines = IOUtils.readLines(new FileInputStream(filename));
		Iterator<String> it = lines.iterator();
		
		Map<String, Item> items = new HashMap<String, Item>();
		
		int c = 0;
		while(it.hasNext()) {
			if((++c)%100000==0) {
				System.out.println(c + " items");
			}
			
			String line = it.next();
			String[] parts = line.split("\t");
			
			int status = Integer.parseInt(parts[0]);
			Boolean  suspended = Boolean.parseBoolean(parts[1]);
			Boolean  parsingError = Boolean.parseBoolean(parts[2]);
			Boolean  otherError = Boolean.parseBoolean(parts[3]);
			
			String type = parts[5];
			
			if(status==200 && !suspended && !parsingError && !otherError) {
				
				Item item = new Item();
				
				String itemId = parts[6];
				item.setId(itemId);
				
				String username = parts[7];
				item.setUsername(username);
				
				String text = parts[8];
				item.setText(text);
				
				int reposts = Integer.parseInt(parts[10]);
				item.setReposts(reposts);
				
				String strDate = parts[9];
				try {
					Date date = formatter.parse(strDate);
					item.setPublicationTime(date.getTime());
				} catch (ParseException e) {
					System.out.println("Cannot parse date " + parts[9]+ " for tweet " + itemId);
					continue;
				}
				
				List<String> urls = extractor.extractURLs(text);
				item.setUrls(urls);
				
				String mediaStr = parts[13];
				if(mediaStr != null && !mediaStr.equals("null")) {
					Map<String, String> map = new HashMap<String, String>();
					String[] mediaUrls = mediaStr.split(",");
					for(String mediaURL : mediaUrls) {
						mediaURL = mediaURL.replaceAll(":large", "");
						Pattern pattern = Pattern.compile(".*media/(.*?)\\..*");
						Matcher matcher = pattern.matcher(mediaURL);
						if (matcher.find()) {
						    String mId = matcher.group(1);
						    map.put(mId, mediaURL);
						}
					}
					item.setMediaUrls(map);
					
				}
				
				List<String> hashtags = extractor.extractHashtags(text);
				item.setHashtags(hashtags);

				if(type.equals("Rp")) {
					String repliesStr = parts[12];
					String[] repliesSequence = repliesStr.split(",");
					String inReplyId = repliesSequence[repliesSequence.length-1];
	
					item.setInReplyId(inReplyId);
				}
				else if(type.equals("R")) {
					String reference = parts[12];
					item.setReference(reference);
					item.setOriginal(false);
				}
				
				items.put(itemId, item);
			}
		}
		
		return items;
	}

//	public static String expand(String shortUrl) throws IOException {
//		int redirects = 0;
//		HttpURLConnection connection;
//		while(true && redirects < 3) {
//			try {
//				URL url = new URL(shortUrl);
//				connection = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY); 
//				connection.setInstanceFollowRedirects(false);
//				connection.setReadTimeout(2000);
//				connection.connect();
//				String expandedURL = connection.getHeaderField("Location");
//				if(expandedURL == null) {
//					return shortUrl;
//				}
//				else {
//					shortUrl = expandedURL;
//					redirects++;
//				}    
//			}
//			catch(Exception e) {
//				return null;
//			}
//		}
//		return shortUrl;
//    }
	
}
