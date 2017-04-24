package gr.iti.mklab;

import gr.iti.mklab.analysis.TextAnalyser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.tidy.Tidy;
import org.xml.sax.SAXException;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

public class SundanceDataset {

	public static Set<String> getBoostingTerms() {
		Set<String> terms = new HashSet<String>();
		try {
			MongoClient client = new MongoClient("160.40.50.207");
		
			DB db = client.getDB("Sundance2013");
			DBCollection collection = db.getCollection("Movies");
			
			
			DBCursor cursor = collection.find();
			while(cursor.hasNext()) {
				DBObject obj = cursor.next();
				
				String title = (String) obj.get("title");
				String cast = (String) obj.get("cast");
			
				try {
					if(title != null)
						terms.addAll(TextAnalyser.getNgrams(title));
				} catch (IOException e) { 
					
				}
				try {
					if(cast != null)
						terms.addAll(TextAnalyser.getNgrams(cast));
				} catch (IOException e) { 
					
				}
			}
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}		
		return terms;
	}
	
	public static void extractMovies() throws ParserConfigurationException, SAXException, IOException, XPathExpressionException {
				
		MongoClient client = new MongoClient("160.40.50.207");
		DB db = client.getDB("Sundance2013");
		DBCollection collection = db.getCollection("Movies");
		
		String urlStr = "http://www.sundance.org/festival/release/2013-sundance-film-festival-announces-films-in-u.s.-and-world-competitions-/";

		URL oracle = new URL(urlStr);  
		URLConnection yc = oracle.openConnection();  
		InputStream is = yc.getInputStream();  
		is = oracle.openStream();  
		Tidy tidy = new Tidy();  
		tidy.setQuiet(true);  
		tidy.setShowWarnings(false);  
		Document tidyDOM = tidy.parseDOM(is, null);  
		
		XPathFactory xPathfactory = XPathFactory.newInstance();
		XPath xpath = xPathfactory.newXPath();
		XPathExpression expr = xpath.compile("//div[@id='content']/div[@class='base entry']/p/strong/em/text()");
		
		NodeList nl = (NodeList) expr.evaluate(tidyDOM, XPathConstants.NODESET);
		for(int index=0; index<nl.getLength(); index++) {
			DBObject mv = new BasicDBObject();
			
			Node node = nl.item(index);
			String title = node.getNodeValue();
			mv.put("title", title);
			
			Node parent = node.getParentNode().getParentNode().getParentNode();
			System.out.println(title);
			NodeList childs = parent.getChildNodes();
			String description = childs.item(1).getNodeValue();
			mv.put("description", description);
			
			System.out.println(description);
			if(childs.getLength()>2) {
				String cast = childs.item(2).getFirstChild().getNodeValue();
				if(cast.matches(".*Cast:.*")) {
					System.out.println(cast);
					mv.put("cast", cast);
				}
			}
			System.out.println("===============================================");
			collection.insert(mv);
		}
	}
	
	public static void main(String[] args) throws XPathExpressionException, ParserConfigurationException, SAXException, IOException {
		extractMovies();
	}

}
