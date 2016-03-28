package gr.iti.mklab.utils;

import java.io.File;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XMLUtils {

	public static void loafFromXML(String filename) throws Exception {
		File fXmlFile = new File(filename);
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc = dBuilder.parse(fXmlFile);
		
		doc.getDocumentElement().normalize();
		NodeList nList = doc.getElementsByTagName("tweet");
		
		int en = 0;
		
		for (int index = 0; index < nList.getLength(); index++) {
			Node nNode = nList.item(index);
			 
			Element eElement = (Element) nNode;
			 
			String title = eElement.getElementsByTagName("title").item(0).getTextContent();
			Long timestamp = Long.parseLong(eElement.getElementsByTagName("timestamp").item(0).getTextContent());
			String lang = eElement.getElementsByTagName("lang").item(0).getTextContent();
			String link = eElement.getElementsByTagName("link").item(0).getTextContent();
			
			String id = link.substring(link.lastIndexOf("/") + 1);
			String username = link.substring(19, link.length()-27);
			
			System.out.println("Title : " + title);
			System.out.println("Timestamp : " + timestamp);
			System.out.println("Lang : " + lang);
			System.out.println("Link : " + link);
			System.out.println("Id : " + id);
			System.out.println("Username : " + username);
			
			if(lang.equals("es"))
				en++;
			
			System.out.println("===========================");
		}
		
		System.out.println(en + " tweets in english");
	}
	
	public static void main(String...args) throws Exception {
		XMLUtils.loafFromXML("/disk1_data/Datasets/copaamerica2011-matches/20-argentina-uruguay.xml");
	}
}
