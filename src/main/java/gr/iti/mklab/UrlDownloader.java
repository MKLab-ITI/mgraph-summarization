package gr.iti.mklab;

import gr.iti.mklab.dao.MorphiaDAO;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.utils.Image;
import gr.iti.mklab.utils.ImageExtractor;
import gr.iti.mklab.utils.URLUtils;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.xml.sax.InputSource;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

import de.l3s.boilerpipe.BoilerpipeProcessingException;
import de.l3s.boilerpipe.document.TextDocument;
import de.l3s.boilerpipe.document.TextDocumentStatistics;
import de.l3s.boilerpipe.estimators.SimpleEstimator;
import de.l3s.boilerpipe.extractors.ArticleExtractor;
import de.l3s.boilerpipe.extractors.CommonExtractors;
import de.l3s.boilerpipe.sax.BoilerpipeSAXInput;


public class UrlDownloader {	

	public static void main(String...args) throws ClassNotFoundException {
		
		try {
			
			MongoClient client = new MongoClient("160.40.50.207");
			DB db = client.getDB("ICMR2015");
			DBCollection wpCollection = db.getCollection("Webpages");
			
			Map<String, Item> itemsMap = new HashMap<String, Item>();
			MorphiaDAO<Item> dao = new MorphiaDAO<Item>("", "ICMR2015", Item.class);
			Iterator<Item> it = dao.iterator();
			while(it.hasNext()) {
				Item item = it.next();
				itemsMap.put(item.getId(), item);
			}
			System.out.println(itemsMap.size() + " items");
			
			Integer id = 0, i = 0;
			for(Item item : itemsMap.values()) {	
				
				if((i++)%100 == 0) {
					System.out.println(i + "/" + itemsMap.size() + " items processed");
				}
				
				String tweetid = item.getId();
				List<String> urls = item.getUrls();
				for(String url : urls) {
					if(url.contains("pic.twitter.com")) {
						continue;
					}
					
					String expandedUrl = URLUtils.expand(url);
					
					byte[] content = fetch(id.toString(), expandedUrl);
					if(content != null) {
						DBObject wp = parse(content, expandedUrl);
						if(wp == null)
							continue;
						
						wp.put("id", id);
						wp.put("tweetid", tweetid);
						wp.put("url", url);
						wp.put("expandedUrl", expandedUrl);
						
						wpCollection.save(wp);
						id = id + 1;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	      
	}

	public static void downloadMedia() throws FileNotFoundException, IOException {
		List<String> lines = IOUtils.readLines(new FileInputStream("/disk1_data/Datasets/Events2012/additionalMedia.tsv"));
		for(String line : lines) {
			String[] parts = line.split("\t");
			
			String imageId = parts[0];
		    String imageUrl = parts[2];
		    
		    System.out.println(lines.indexOf(line) + " Fetch: " + imageId + " = > " + imageUrl);
		    fetch(imageId, imageUrl);
		}
	}
	
	public static byte[] fetch(String id, String url) {
		try {
			
			InputStream input = new URL(url).openStream();
			
			byte[] content = IOUtils.toByteArray(input);
			
			//File file = new File(outputDir, id);
			//OutputStream output = new FileOutputStream(file);
			//ByteArrayInputStream bis = new ByteArrayInputStream(content);
			//IOUtils.copy(bis, output);
			//output.close();
			
			return content;
			
			/* 
			Class.forName("com.mysql.jdbc.Driver");
			Connection connection = DriverManager.getConnection(dbUrl);
			Statement statement = connection.createStatement();
			
			ResultSet resultSet = statement.executeQuery("SELECT * FROM images WHERE type='twitter'");
			
			while (resultSet.next()) {
				String imageId = resultSet.getString("imageId");
			    String imageUrl = resultSet.getString("url");
			    
			    System.out.println("imageId: " + imageId);
			    System.out.println("imageUrl: " + imageUrl);
			    
			    fetch(imageId, imageUrl);
			    
			    try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					System.out.println(e.getMessage());
				}
			}
			
			resultSet.close();
		    statement.close();
		    connection.close();
		    */
			
		} catch (MalformedURLException e) {
			System.out.println(e.getMessage());
			return null;
		} catch (IOException e) {
			System.out.println(e.getMessage());
			return null;
		}
	}
	
	public static DBObject parse(byte[] content, String url) {  
	  	try { 
	  		
	  		DBObject article = new BasicDBObject();
	  		
	  		ArticleExtractor _articleExtractor = CommonExtractors.ARTICLE_EXTRACTOR;
	  	    ArticleExtractor _extractor = CommonExtractors.ARTICLE_EXTRACTOR;
	  	    SimpleEstimator _estimator = SimpleEstimator.INSTANCE;
	  	    
	  		InputSource articelIS1 = new InputSource(new ByteArrayInputStream(content));
	  		InputSource articelIS2 = new InputSource(new ByteArrayInputStream(content));
	  		
		  	TextDocument document = null, imgDoc = null;

	  		document = new BoilerpipeSAXInput(articelIS1).getTextDocument();
	  		imgDoc = new BoilerpipeSAXInput(articelIS2).getTextDocument();
	  		
	  		TextDocumentStatistics dsBefore = new TextDocumentStatistics(document, false);
	  		synchronized(_articleExtractor) {
	  			_articleExtractor.process(document);
	  		}
	  		synchronized(_extractor) {
	  			_extractor.process(imgDoc);
	  		}
	  		TextDocumentStatistics dsAfter = new TextDocumentStatistics(document, false);
	  		
	  		boolean isLowQuality = _estimator.isLowQuality(dsBefore, dsAfter);
	  		if(isLowQuality)
	  			return null;
	  		
	  		String title = document.getTitle();
	  		
	  		if(title == null) {
	  			return null;
	  		}
	  		String text = document.getText(true, false);
	  		
	  		article.put("title", title);
	  		article.put("text", text);
	  		
	  		List<DBObject> media = extractImages(imgDoc, url, content);		
	  		article.put("media", media);
	  		article.put("numOfMedia", media.size());
	  		
			return article;
			
	  	} catch(Exception ex) {
	  		ex.printStackTrace();
	  		return null;
	  	}
	}
	
	public static List<DBObject> extractImages(TextDocument document, String base, byte[] content) 
			throws IOException, BoilerpipeProcessingException {
		
		List<DBObject> images = new ArrayList<DBObject>();
		
		ImageExtractor _imageExtractor = ImageExtractor.INSTANCE;
		InputSource imageslIS = new InputSource(new ByteArrayInputStream(content));
  		List<Image> detectedImages = _imageExtractor.process(document, imageslIS);
  		for(Image image  : detectedImages) {
  			//Integer w = -1, h = -1;
  			//try {
  			//	String width = image.getWidth().replaceAll("%", "");
  			//	String height = image.getHeight().replaceAll("%", "");
  			//	w = Integer.parseInt(width);
  			//	h = Integer.parseInt(height);
  			//}
  			//catch(Exception e) {
  				// filter images without size
  			//	continue;
  			//}
  			
  			// filter small images
  			//if(image.getArea() < 40000 || w < 200  || h < 200) 
			//	continue;

			String src = image.getSrc();
			URL url = null;
			try {
				url = new URL(new URL(base), src);
				if(url.toString().length() > 200)
					continue;
				if(src.endsWith(".gif") || url.getPath().endsWith(".gif"))
					continue;	
			} catch (Exception e) {
				continue;
			}
			
			String alt = image.getAlt();
			
			// Create image unique id. Is this a good practice? 
			Integer imageHash = (url.hashCode() & 0x7FFFFFFF);
			
			DBObject img = new BasicDBObject();
			img.put("id", imageHash.toString());
			img.put("url", url.toString());
			img.put("title", alt);
			img.put("id", imageHash.toString());
			
			images.add(img);
		}
  		return images;
	}
	
}
