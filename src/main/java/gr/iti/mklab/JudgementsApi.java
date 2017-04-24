package gr.iti.mklab;

import edu.uci.ics.jung.graph.Graph;
import gr.iti.mklab.clustering.GraphClusterer;
import gr.iti.mklab.dao.MorphiaDAO;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.Pair;
import gr.iti.mklab.models.WeightedEdge;
import gr.iti.mklab.utils.GraphUtils;

import java.io.File;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

public class JudgementsApi {

	private static String dbUrl = "jdbc:mysql://localhost/twitter_visualisation_export?user=root&password=madrugada";
	private static String eventsQuery = "SELECT eventid, title, tweets, clusters FROM event E WHERE tweets>=2950 ORDER BY tweets DESC";
	private static String tweetsQuery = "SELECT TC.tweetid FROM cluster C, tweet_cluster TC WHERE C.clusterid=TC.clusterid AND C.eventid=?";
	
	private static String imagesQuery = "SELECT I.imageid, I.tweetid, R.judgements, R.relevance, R.eventid " 
			+ "FROM images I, crowdsourcing_exp1_results R " 
			+ "WHERE I.imageid=R.imageid AND I.type='twitter'";
	
	public static void main(String[] args) throws Exception {
		//create();
		merge();
	}
	
	public static Map<String, ImageJudgement> load() {
		
		Map<String, ImageJudgement> judgements = new HashMap<String, ImageJudgement>();
		
		MongoClient client;
		try {
			client = new MongoClient("160.40.50.207");
		} catch (UnknownHostException e) {
			e.printStackTrace();
			return judgements;
		}
		DB db = client.getDB("ICMR2015");
		DBCollection collection = db.getCollection("Judgements");
		
		DBObject query = new BasicDBObject();
		String[] exclude = {"241104","252589", "422239", "302595"};
		query.put("eventid", new BasicDBObject("$nin", exclude));
	
		DBCursor cursor = collection.find(query);
		while(cursor.hasNext()) {
			DBObject obj = cursor.next();	
			ImageJudgement judgement = ImageJudgement.fromDBObject(obj);
			
			judgements.put(judgement.imageid, judgement);
		}
		
		return judgements;
	}
	
	public static void merge() throws Exception {
		MongoClient client = new MongoClient("160.40.50.207");
		DB db = client.getDB("ICMR2015");
		DBCollection collection = db.getCollection("Images");
		
		DBObject query = new BasicDBObject();
		String[] exclude = {"241104","252589", "422239", "302595"};
		query.put("eventid", new BasicDBObject("$nin", exclude));
		
		Map<String, String> images = new HashMap<String, String>();
		Map<String, Set<String>> events = new HashMap<String, Set<String>>();
		Map<String, String> tweets = new HashMap<String, String>();
		Map<String, String> titles = new HashMap<String, String>();
		Map<String, Double> relevanceScores = new HashMap<String, Double>();
		DBCursor cursor = collection.find(query);
		while(cursor.hasNext()) {
			DBObject obj = cursor.next();
			
			ImageJudgement judgement = ImageJudgement.fromDBObject(obj);			
			relevanceScores.put(judgement.imageid, judgement.relevance);
			images.put(judgement.imageid, judgement.imageid);
			
			tweets.put(judgement.imageid, judgement.tweetid);
			titles.put(judgement.imageid, judgement.title);
			
			String eventid = judgement.eventid;
			
			Set<String> ids = events.get(eventid);
			if(ids == null) {
				ids = new HashSet<String>();
				events.put(eventid, ids);
			}
			ids.add(judgement.imageid);
			
		}
		
		//VisualIndex vIndex = new VisualIndex("/disk2_data/VisualIndex/learning_files", "/disk1_data/Datasets/Events2012/VisualIndex");
		
		//Graph<String, WeightedEdge> graph = GraphUtils.generateVisualGraph(images, 0.5, vIndex);
		//GraphUtils.saveGraph(graph, "/disk1_data/Datasets/Events2012/visual_items.graphml");
		
		Graph<String, WeightedEdge> graph = GraphUtils.loadGraph("/disk1_data/Datasets/Events2012/visual_items.graphml");
		System.out.println(graph.getVertexCount() + " vertices - " + graph.getEdgeCount() + " edges");
		
		GraphClusterer.scanEpsilon = 0.5;
		GraphClusterer.scanMu = 1;
		
		Map<String, ImageJudgement> judgementsMap = new HashMap<String, ImageJudgement>();
		
		for(Entry<String, Set<String>> entry : events.entrySet()) {
			String eventid = entry.getKey();
			Set<String> ids = entry.getValue();
			Graph<String, WeightedEdge> eventGraph = GraphUtils.filter(graph, ids);
			
			System.out.println("============================================");
			Collection<Collection<String>> cliques = GraphClusterer.cluster(eventGraph, true);
			System.out.println("Event: " + eventid + " " + ids.size() + " images" + "\n" + cliques.size() + " cliques detected");
			
			for(Collection<String> clique : cliques) {
				Double avgRelevance = .0;
				for(String id : clique) {
					Double relevance = relevanceScores.get(id);
					if(relevance != null) {
						avgRelevance += relevance;
					}
				}
				avgRelevance = avgRelevance/clique.size();
				
				for(String id : clique) {
					String tweetid = tweets.get(id);
					ImageJudgement imgJudgement = new ImageJudgement(id, tweetid, avgRelevance, 0, eventid, titles.get(id));
					judgementsMap.put(id, imgJudgement);
				}
			}
		}
		

		DBCollection judgementsCollection = db.getCollection("Judgements");
		
		for(ImageJudgement judgement : judgementsMap.values()) {
			System.out.println(judgement);
			judgementsCollection.save(judgement.toDBObject());
		}
		
	}
	
	public static void create() throws Exception {
		
		File imgDir = new File("/disk1_data/Datasets/Events2012/images");
		
		Map<String, Pair<String, String>> imagesMap = new TreeMap<String, Pair<String, String>>();
		
		Map<String, Item> itemsMap = new HashMap<String, Item>();
		MorphiaDAO<Item> dao = new MorphiaDAO<Item>("", "ICMR2015", Item.class);
		Iterator<Item> iterator = dao.iterator();
		while(iterator.hasNext()) {
			Item item = iterator.next();
			itemsMap.put(item.getId(), item);
		}
		System.out.println(itemsMap.size() + " items!");
		
		int eventItems = 0;
		Connection connection = DriverManager.getConnection(dbUrl);
		PreparedStatement evtStatement = connection.prepareStatement(eventsQuery);
		ResultSet resultSet = evtStatement.executeQuery();
		Map<String, String> events = new HashMap<String, String>();
		while (resultSet.next()) {
			
			String eventid = resultSet.getString("eventid");
			String title = resultSet.getString("title");
			
			events.put(eventid, title);
			
			PreparedStatement twtsStatement = connection.prepareStatement(tweetsQuery);
			twtsStatement.setString(1, eventid);
			Set<String> eventIds = new HashSet<String>();
			ResultSet tweetSet = twtsStatement.executeQuery();
			while (tweetSet.next()) {
				String tweetid = tweetSet.getString("tweetid");
				if(tweetid != null) {
					Item item = itemsMap.get(tweetid);
					if(item != null) {
						eventIds.add(tweetid);
						
						String inReplyId = item.getInReplyId();
						if(inReplyId != null && itemsMap.containsKey(inReplyId)) {
							eventIds.add(tweetid);
						}
					}
				}
			}
			
			eventItems += eventIds.size();
			
			Map<String, String> images = new HashMap<String, String>();
			for(String itemId : eventIds) {
				Item item = itemsMap.get(itemId);
				if(item != null) {
					for(String imageId : item.getMediaItems().keySet()) {
						File img = new File(imgDir, imageId);
						if(img.exists()) {
							images.put(item.getId(), imageId);
						}
					}
				}
			}
			
			
			//System.out.println(eventid + " " + eventIds.size() + " items with " + images.size() + " images");
			for(Entry<String, String> e : images.entrySet()) {
				Pair<String, String> pair = Pair.of(e.getKey(), eventid);
				imagesMap.put(e.getValue(), pair);
			}
			
		}
		System.out.println(eventItems + " event items!");
		
		Map<String, ImageJudgement> judgementsMap = new HashMap<String, ImageJudgement>();
		PreparedStatement imgStatement = connection.prepareStatement(imagesQuery);
		ResultSet imgSet = imgStatement.executeQuery();
		while (imgSet.next()) {
			String imageid = imgSet.getString("imageid");
			String tweetid = imgSet.getString("tweetid");
			double relevance = imgSet.getDouble("relevance");
			int judgements = imgSet.getInt("judgements");
			String eventid = imgSet.getString("eventid");
			String title = events.get(eventid);
			
			ImageJudgement ij = new ImageJudgement(imageid, tweetid, relevance, judgements, eventid, title);
			
			judgementsMap.put(imageid, ij);
		}
		
		System.out.println(imagesMap.size() + " images");
		System.out.println(judgementsMap.size() + " judgements");
		
		Map<String, ImageJudgement> validJudgements = new HashMap<String, ImageJudgement>();
		for(Entry<String, Pair<String, String>> e : imagesMap.entrySet()) {
			String imageid = e.getKey();
			Pair<String, String> p = e.getValue();
			
			ImageJudgement imgJudgement = judgementsMap.get(imageid);
			if(imgJudgement == null) {
				imgJudgement = new ImageJudgement(imageid, p.left, 0, 0, p.right, events.get(p.right));
				validJudgements.put(imageid, imgJudgement);
			}
			else {
				validJudgements.put(imageid, imgJudgement);
			}
		}
		
		MongoClient client = new MongoClient("160.40.50.207");
		DB db = client.getDB("ICMR2015");
		DBCollection collection = db.getCollection("Images");
		
		for(ImageJudgement judgement : validJudgements.values()) {
			System.out.println(judgement);
			collection.save(judgement.toDBObject());
		}
	}
	
	public static class ImageJudgement {
		
		public ImageJudgement(String imageid, String tweetid, double relevance, long judgements, String eventid, String title) {
			this.imageid = imageid;
			this.tweetid = tweetid;
			this.relevance = relevance;
			this.judgements = judgements;
			this.eventid = eventid;
			this.title = title;
		}
		
		public String imageid;
		public String tweetid;
		public double relevance;
		public long judgements = 0;
		public String eventid;
		public String title;
		
		public DBObject toDBObject() {
			DBObject obj = new BasicDBObject();
			
			obj.put("imageid", imageid);
			obj.put("tweetid", tweetid);
			obj.put("relevance", relevance);
			obj.put("judgements", judgements);
			obj.put("eventid", eventid);
			obj.put("title", title);
			
			return obj;
		}
		

		public static ImageJudgement fromDBObject(DBObject obj) {

			return new ImageJudgement((String) obj.get("imageid"), (String)obj.get("tweetid"), 
					(Double) obj.get("relevance"), 0, 
					(String) obj.get("eventid"), (String) obj.get("title"));

		}
		
		public String toString() {
			return imageid + " " + tweetid + " " + eventid + " " + relevance + " " + judgements;
		}
	}

}
