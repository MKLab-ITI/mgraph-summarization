package gr.iti.mklab.dao;

import gr.iti.mklab.models.Event;
import gr.iti.mklab.models.Item;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MySqlDAO {

	private static String dbUrl = "jdbc:mysql://localhost/twitter_visualisation_export?user=root&password=madrugada";
	
	private static String eventsQuery = "SELECT eventid, title, tweets, clusters FROM event E WHERE tweets>=2950 AND crowdsourced_category=? ORDER BY tweets DESC";
	private static String tweetsQuery = "SELECT TC.tweetid FROM cluster C, tweet_cluster TC WHERE C.clusterid=TC.clusterid AND C.eventid=?";
	
	public static List<Event> getEvents() throws SQLException, ClassNotFoundException {
		Class.forName("com.mysql.jdbc.Driver");
		
		List<Event> events = new ArrayList<Event>();
		
		Connection connection = DriverManager.getConnection(dbUrl);
		PreparedStatement evtStatement = connection.prepareStatement(eventsQuery);
		evtStatement.setString(1, "Disasters and Accidents");
		ResultSet resultSet = evtStatement.executeQuery();
		while (resultSet.next()) {
			
			String eventid = resultSet.getString("eventid");
			String title = resultSet.getString("title");	
			int clusters = Integer.parseInt(resultSet.getString("clusters"));
		
			Event event = new Event(eventid, title, clusters);
			events.add(event);
		}
		
		return events;
	}
	
	public static Set<String> getEventTweets(String eventid, Map<String, Item> itemsMap) 
			throws SQLException, ClassNotFoundException {
		Class.forName("com.mysql.jdbc.Driver");
		Connection connection = DriverManager.getConnection(dbUrl);
		
		PreparedStatement twtsStatement = connection.prepareStatement(tweetsQuery);
		twtsStatement.setString(1, eventid);
		
		Set<String> ids = new HashSet<String>();
		ResultSet tweetSet = twtsStatement.executeQuery();
		while (tweetSet.next()) {
			String tweetid = tweetSet.getString("tweetid");
			if(tweetid != null) {
				Item item = itemsMap.get(tweetid);
				if(item != null) {
					ids.add(tweetid);
					
					String inReplyId = item.getInReplyId();
					if(inReplyId != null && itemsMap.containsKey(inReplyId)) {
						ids.add(tweetid);
					}
				}
			}
		}
		
		return ids;
	}
	
	public static Map<String, String> getEventImages(Set<String> ids, Map<String, Item> itemsMap) {
		Map<String, String> images = new HashMap<String, String>();
		for(String itemId : ids) {
			Item item = itemsMap.get(itemId);
			if(item != null) {
				for(String imageId : item.getMediaItems().keySet()) {
					images.put(item.getId(), imageId);
				}
			}
		}
		return images;
	}

	public static Map<String, Set<String>> getSubEvents(String eventid) throws SQLException, ClassNotFoundException {
		
		Map<String, Set<String>> clusters = new HashMap<String, Set<String>>();
		
		Class.forName("com.mysql.jdbc.Driver");
		Connection connection = DriverManager.getConnection(dbUrl);
		
		String clustersQuery = "SELECT TC.tweetid, C.clusterid FROM cluster C, tweet_cluster TC WHERE C.clusterid=TC.clusterid AND C.eventid=?";
		
		PreparedStatement statement = connection.prepareStatement(clustersQuery);
		statement.setString(1, eventid);
		ResultSet set = statement.executeQuery();
		while (set.next()) {
			String clusterid = set.getString("clusterid");
			String tweetid = set.getString("tweetid");
			
			Set<String> cluster = clusters.get(clusterid);
			if(cluster == null) {
				cluster = new HashSet<String>();
				clusters.put(clusterid, cluster);
			}
			cluster.add(tweetid);
		}
		
		return clusters;
	}
}
