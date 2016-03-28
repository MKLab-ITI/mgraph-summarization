package gr.iti.mklab.dao;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.dao.BasicDAO;
import org.mongodb.morphia.dao.DAO;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

import gr.iti.mklab.Config;
import gr.iti.mklab.analysis.ItemFilter;
import gr.iti.mklab.analysis.TextAnalyser;
import gr.iti.mklab.extractors.EntitiesExtractor;
import gr.iti.mklab.extractors.PosExtractor;
import gr.iti.mklab.models.NamedEntity;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.Pair;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.utils.StringUtils;
import gr.iti.mklab.vocabulary.Vocabulary;
import twitter4j.Status;
import twitter4j.TwitterException;
import twitter4j.json.DataObjectFactory;
import cmu.arktweetnlp.Tagger.TaggedToken;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;

public class MongoDAODep {
	
	private static BasicDBObject fields = new BasicDBObject(); 
	
	{
		fields.put("_id", 0);
		fields.put("id", 1);
		fields.put("text", 1);
		fields.put("created_at", 1);
		fields.put("entities.urls.url", 1);
		fields.put("entities.hashtags.text", 1);
		fields.put("entities.media.id", 1);
		fields.put("entities.media.media_url", 1);
		fields.put("user.screen_name", 1);
		fields.put("retweeted_status.id", 1);
		fields.put("retweeted_status.text", 1);
		fields.put("retweeted_status.created_at", 1);
		fields.put("retweeted_status.user.screen_name", 1);
		fields.put("in_reply_to_status_id_str", 1);
	}
	
	public static void writeStatusesToFile(String hostname, String dbname, String collName, String output) {
		int k=0;
		List<String> statuses = new ArrayList<String>();
		DBCollection collection = getCollection(hostname, dbname, collName);
		
		DBCursor cursor = collection.find();
		while(cursor.hasNext()) {
			if(++k%100==0) {
				System.out.print(".");
				if(k%10000==0) {
					System.out.println("  " + k);
				}
			}
			
			DBObject obj = cursor.next();
			String text = (String) obj.get("text");
			text = text.replaceAll("\\r\\n|\\r|\\n", " ");
			try {
				text = StringUtils.cleanNonUTF(text);
			} catch (IOException e) {
				continue;
			}
			statuses.add(text);
		}
		try {
			IOUtils.writeLines(statuses, "\n", new  FileOutputStream(output));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println();
	}
	
	public static Map<String, Status> loadStatusesMap(String hostname, String dbname, String collName, boolean unique) {
		return loadStatusesMap(hostname, dbname, collName, unique, null, null);
	}
	
	public static Map<String, Status> loadStatusesMap(String hostname, String dbname, String collName, boolean unique, String[] ids) {
		return loadStatusesMap(hostname, dbname, collName, unique, null, ids);
	}
	
	public static Map<String, Status> loadStatusesMap(String hostname, String dbname, String collName, boolean unique, String lang, String[] ids) {
		Map<String, Status> statuses = new HashMap<String, Status>();
		DBCollection collection = getCollection(hostname, dbname, collName);
		
		BasicDBObject query = new BasicDBObject();
		if(ids != null && ids.length > 0) {
			query.put("id", new BasicDBObject("$in", ids));
		}
		if(lang != null) {
			query.put("lang", lang);
		}
		
		int k = 0;
		DBCursor cursor = collection.find(query);
		while(cursor.hasNext()) {
			
			if(++k%1000==0) {
				System.out.print(".");
				if(k%100000==0) {
					System.out.println("  " + k);
				}
			}
			
			DBObject obj = cursor.next();
			String rawJSON = obj.toString();
			try {
				Status status = DataObjectFactory.createStatus(rawJSON);
				Status rt = status.getRetweetedStatus();
				if(rt != null && !unique) {
					if(!statuses.containsKey(rt.getId()))
						statuses.put(Long.toString(rt.getId()), rt);
					
					if(!statuses.containsKey(status.getId()))
						statuses.put(Long.toString(status.getId()), status);
				}
				else {
					if(!statuses.containsKey(status.getId()))
						statuses.put(Long.toString(status.getId()), status);
				}
			} catch (TwitterException e) { 
				
			}
		}
		return statuses;
	}
	
	public static Map<String, Item> loadItemsMap(String hostname, String dbname, String collName, boolean unique, 
			String lang) {
		return loadItemsMap(hostname, dbname, collName, unique, null, null, lang, null);
	}
	
	public static Map<String, Item> loadItemsMap(String hostname, String dbname, String collName, boolean unique, 
			Long since, Long until, String lang, String[] ids) {
		
		Map<String, Item> items = new HashMap<String, Item>();
		DBCollection collection = getCollection(hostname, dbname, collName);
		
		BasicDBObject query = new BasicDBObject();
		if(ids != null && ids.length > 0) {
			query.put("id", new BasicDBObject("$in", ids));
		}
		if(lang != null) {
			query.put("lang", lang);
		}
		
		if(since != null) {
			query.put("timestamp", new BasicDBObject("$gte", since));
		}
		
		if(until != null) {
			query.put("timestamp", new BasicDBObject("$lte", until));
		}
		
		if(unique) {
			query.put("retweeted_status", new BasicDBObject("$exists", false));
		}
		
		System.out.println(query.toString());
		int k = 0;
		DBCursor cursor = collection.find(query, fields);
		while(cursor.hasNext()) {
			
			if(++k%1000==0) {
				System.out.print(".");
				if(k%20000==0) {
					System.out.println("  " + k + " items");
				}
			}
			
			DBObject obj = cursor.next();
			try {

				DBObject rtObj = (DBObject) obj.get("retweeted_status");
				if(rtObj != null && !unique) {
					Item rtItem = new Item(rtObj);
					if(!items.containsKey(rtItem.getId()))
						items.put(rtItem.getId(), rtItem);
					
					Item item = new Item(obj);
					if(!items.containsKey(item.getId()))
						items.put(item.getId(), item);
				}
				else {
					Item item = new Item(obj);				
					if(!items.containsKey(item.getId()))
						items.put(item.getId(), item);
				}
			} catch (Exception e) { 
				e.printStackTrace();
			}
		}
		System.out.println(". "+  items.size() + " items");
		return items;
	}
	
	public static Map<String, Item> loadSocialSensorItemsMap(String hostname, String dbname, String collName, Set<String> ids) {
		
		Map<String, Item> items = new HashMap<String, Item>();
		DBCollection collection = getCollection(hostname, dbname, collName);
		
		DBObject q = new BasicDBObject("id", new BasicDBObject("$exists", true));
		int k = 0;
		DBCursor cursor = collection.find(q);
		while(cursor.hasNext()) {
			
			if(++k%1000==0) {
				System.out.print(".");
				if(k%20000==0) {
					System.out.println("  " + k + " items");
				}
			}
			
			DBObject obj = cursor.next();
			try {
				Item item = new Item(obj, "");
				
				if(!ids.contains(item.getId()))
					continue;
				
				if(!items.containsKey(item.getId())) {
					items.put(item.getId(), item);
				}
			} catch (Exception e) { 
				e.printStackTrace();
			}
		}
		System.out.println(". "+  items.size() + " items");
		return items;
	}
	
	
	public static Map<String, Integer> loadPopularity(String hostname, String dbname, String collName) {
		
		Map<String, Integer> map = new HashMap<String, Integer>();
		
		DBCollection collection = getCollection(hostname, dbname, collName);
		
		DBObject query = new BasicDBObject("lang","en");
		DBObject fields = new BasicDBObject("id", 1);
		fields.put("retweeted_status", 1);
		
		DBCursor cursor = collection.find(query);
		while(cursor.hasNext()) {
			DBObject obj = cursor.next();
			
			Object rt = obj.get("retweeted_status");
			if(rt == null) {
				String id = obj.get("id").toString();
				Integer rtsNum = map.get(id);
				if(rtsNum == null)
					rtsNum = 0;
				
				map.put(id, ++rtsNum);
			}
			else {
				String id = ((DBObject)rt).get("id").toString();
				Integer rtsNum = map.get(id);
				if(rtsNum == null)
					rtsNum = 0;
				
				map.put(id, ++rtsNum);
			}
			
		}
		return map;
	}
	
	public static Map<String, Integer> loadSocialSensorPopularity(String hostname, String dbname, String collName) {
		
		Map<String, Integer> map = new HashMap<String, Integer>();
		
		DBCollection collection = getCollection(hostname, dbname, collName);
		
		DBObject query = new BasicDBObject("id",new BasicDBObject("$exists", true));
		
		DBCursor cursor = collection.find(query);
		while(cursor.hasNext()) {
			DBObject obj = cursor.next();
			
			String reference = (String) obj.get("reference");
			if(reference == null) {
				String id = obj.get("id").toString().split("#")[1];
				Integer rtsNum = map.get(id);
				if(rtsNum == null)
					rtsNum = 0;
				
				map.put(id, ++rtsNum);
			}
			else {
				String id = reference.split("#")[1];
				Integer rtsNum = map.get(id);
				if(rtsNum == null)
					rtsNum = 0;
				
				map.put(id, ++rtsNum);
			}
			
		}
		return map;
	}

	public static Map<String, Status> loadStatusesMap(String hostname, String dbname, String collName) {
		return loadStatusesMap(hostname, dbname, collName, false);
	}
	
	public static List<Status> loadStatuses(String hostname, String dbname, String collName) {
		Map<String, Status> statuses = loadStatusesMap(hostname, dbname, collName);
		return new ArrayList<Status>(statuses.values());
	}
	
	public static List<Status> loadStatuses(String hostname, String dbname, String collName, boolean unique) {
		Map<String, Status> statuses = loadStatusesMap(hostname, dbname, collName, unique);
		return new ArrayList<Status>(statuses.values());
	}
	
	public static Map<String, Pair<Vector, Long>> loadVectors(String hostname, String dbname, String collName) {
		Map<String, Pair<Vector, Long>> vectors = loadVectors(hostname, dbname, collName, false, false);
		return vectors;
	}
	
	public static Map<String, Pair<Vector, Long>> loadVectors(String hostname, String dbname, String collName, boolean unique, boolean media) {
		SimpleDateFormat formatter = new SimpleDateFormat("EEE MMM d HH:mm:ss zzz yyyy");
		Map<String, Pair<Vector, Long>> vectors = new HashMap<String, Pair<Vector, Long>>();
		
		DBCollection collection = getCollection(hostname, dbname, collName);
		
		int k=0;
		DBObject q = new BasicDBObject("lang","en");
		
		if(media) {
			q.put("entities.media", new BasicDBObject("$exists", true));
		}
		
		DBCursor cursor = collection.find(q);
		while(cursor.hasNext()) {
			if(++k%500==0) {
				System.out.print(".");
				if(k%50000==0) {
					System.out.println("  " + k);
				}
			}
			
			DBObject obj = cursor.next();
			if(unique) {
				Object rt = obj.get("retweeted_status");
				if(rt != null)
					continue;
			}
			
			String text = (String) obj.get("text");
			String created_at = (String) obj.get("created_at");
			long publicationTime;
			try {
				publicationTime = formatter.parse(created_at).getTime();
			} catch (ParseException e1) {
				e1.printStackTrace();
				continue;
			}
			Long id = (Long) obj.get("id");
			
			try {
				
				text = StringUtils.clean(text);
				
				List<String> tokens = TextAnalyser.getTokens(text);
				Vocabulary.addDoc(tokens);
				
				Vector v = new Vector(tokens);
				
				vectors.put(id.toString(), Pair.of(v, publicationTime));
			} catch (IOException e) {
				e.printStackTrace();
			}
			
		}
		System.out.println("  " + k);
		
		return vectors;
	}
	
	public static Map<String, Pair<Vector, Long>> loadSocialSensorVectors(String hostname, String dbname, String collName, boolean unique) {

		Map<String, Pair<Vector, Long>> vectors = new HashMap<String, Pair<Vector, Long>>();		
		DBCollection collection = getCollection(hostname, dbname, collName);
		
		int k=0;
		DBObject q = new BasicDBObject("description", new BasicDBObject("$ne", "Comment"));
		
		DBCursor cursor = collection.find(q);
		while(cursor.hasNext()) {
			if(++k%500==0) {
				System.out.print(".");
				if(k%50000==0) {
					System.out.println("  " + k);
				}
			}
			
			DBObject obj = cursor.next();
			if(unique) {
				Object reference = obj.get("reference");
				if(reference != null)
					continue;
			}
			
			String text = (String) obj.get("title");
			
			if(text == null)
				text = (String) obj.get("description");
			
			if(text == null)
				continue;
			
			long publicationTime = (Long) obj.get("publicationTime");
			
			String idStr = (String) obj.get("id");
			String id = idStr.split("#")[1];
			try {
				
				text = StringUtils.clean(text);
				
				List<String> tokens = TextAnalyser.getTokens(text);
				Vocabulary.addDoc(tokens);
				
				Vector v = new Vector(tokens);
				
				vectors.put(id.toString(), Pair.of(v, publicationTime));
			} catch (IOException e) {
				e.printStackTrace();
			}
			
		}
		System.out.println("  " + k);
		
		return vectors;
	}
	
	public static List<Status> getDataSlice(Map<Long, Status> data, List<Long> sliceIds) {
		List<Status> slice = new ArrayList<Status>();
		
		for(Long id : sliceIds) {
			Status status = data.get(id);
			if(status != null) {
				slice.add(status);
			}
		}
		return slice;
	}
	
	public static Map<String, Integer> getRetweets(String filename) throws FileNotFoundException, IOException {
		Map<String, Integer> map = new HashMap<String, Integer>();
		List<String> lines = IOUtils.readLines(new FileInputStream(filename));
		for(String line : lines) {
			String[] parts = line.split("\t");
			String id = parts[0];
			Integer f = Integer.parseInt(parts[1]);
			
			map.put(id, f);
		}
		return map;
	}
	
	public static Pair<Date, Date> getTimeRange(String hostname, String dbName, String collection) {
		Date first = null, last = null;
		DBCollection coll = getCollection(hostname, dbName, collection);
		DBCursor c = coll.find();
		while(c.hasNext()) {
			DBObject obj = c.next();
			String rawJSON = obj.toString();
			try {
				Status status = DataObjectFactory.createStatus(rawJSON);
				
				Date d = status.getCreatedAt();
				if(first == null || first.after(d))
					first = d;
				
				if(last == null || last.before(d))
					last = d;
				
			} catch (TwitterException e) { }
		}
		
		System.out.println("First tweet at: " + first);
		System.out.println("Last tweet at: " + last);
		
		return Pair.of(first, last);
	}
	
	public static void saveEntities(String hostname, String dbname, String collName, List<NamedEntity> entities) {
		DBCollection collection = getCollection(hostname, dbname, collName);
		for(NamedEntity entity : entities) {			
			collection.insert(entity.toDBOject());
		}
	}
	
	public static void saveEntitiesPerStatus(String hostname, String dbname, String collName, Map<Long, List<NamedEntity>> entitiesPerStatus) {
		DBCollection collection = MongoDAODep.getCollection(hostname, dbname, collName);
		for(Entry<Long, List<NamedEntity>> e : entitiesPerStatus.entrySet()) {
			if(e.getValue().isEmpty())
				continue;
			
			DBObject obj = new BasicDBObject("id", e.getKey());
			
			List<DBObject> entities = new ArrayList<DBObject>();
			for(NamedEntity entity : e.getValue()){
				entities.add(entity.toDBOject());
			}
			obj.put("entities", entities);
			obj.put("numOfEntities", entities.size());
			
			collection.insert(obj);
		}
	}
	
	public static Map<Long, List<NamedEntity>> loadEntitiesPerStatus(String hostname, String dbname, String collName) {
		
		Map<String, NamedEntity> entitiesMap =new HashMap<String, NamedEntity>();
		Map<Long, List<NamedEntity>> entitiesPerStatus = new HashMap<Long, List<NamedEntity>>();
		
		DBCollection collection = MongoDAODep.getCollection(hostname, dbname, collName);
		DBCursor cursor = collection.find();
		while(cursor.hasNext()) {
			
			DBObject obj = cursor.next();
			Long id = (Long) obj.get("id");
			
			List<NamedEntity> entities = new ArrayList<NamedEntity>();
			BasicDBList entitiesList = (BasicDBList) obj.get("entities");
			for(int i=0; i<entitiesList.size(); i++) {
				DBObject o = (DBObject) entitiesList.get(i);
				String name = (String) o.get("name");
				String type = (String) o.get("type");
				Integer frequency = (Integer) o.get("frequency");
				
				NamedEntity e = entitiesMap.get(name+"%"+type);
				if(e == null) {
					e = new NamedEntity(name, type);
					entitiesMap.put(name+"%"+type, e);
				}
				e.setFrequency(frequency);
				
				entities.add(e);
			}
			entitiesPerStatus.put(id, entities);
		}
		return entitiesPerStatus;
	}
	
	public static DBCollection getCollection(String hostname, String dbname, String collName) {
		try {		
			ServerAddress srvAdr = new ServerAddress(hostname);
			MongoClient client = new MongoClient(srvAdr);
			
			DB db = client.getDB(dbname);
			DBCollection collection = db.getCollection(collName);
			
			return collection;
			
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public static void mv(String sourceHostname, String sourceDBname, String sourceCollName, String destHostname, String destDBname) throws Exception {
		String serializedClassifier3Class = "edu/stanford/nlp/models/ner/english.all.3class.distsim.crf.ser.gz";
		EntitiesExtractor entitiesExtractor = new EntitiesExtractor(serializedClassifier3Class);
		
		String posModelFilename = "/disk1_data/workspace/git/microblogging-summarization/model.20120919";	
		//String posModelFilename = "./model.ritter_ptb_alldata_fixed.20130723";	
		PosExtractor posExtractor = new PosExtractor(posModelFilename);
		
		Morphia morphia = new Morphia();
		morphia.map(Item.class);
		
		MongoClient mongoClient = new MongoClient(destHostname);
		Datastore ds = morphia.createDatastore(mongoClient, destDBname);
		DAO<Item, String> itemDAO = new BasicDAO<Item, String>(Item.class, mongoClient, morphia, destDBname);
		
		DBCollection collection = getCollection(sourceHostname, sourceDBname, sourceCollName);
			
		ItemFilter filter = new ItemFilter();
		
		BasicDBObject query = new BasicDBObject("lang", "en");
		long count = collection.count(query);
		
		int limit = 10000;
		for(int i=0; i<=(count/limit); i++) {
			int skip = i*limit;
			DBCursor cursor = collection.find(query, fields).skip(skip).limit(limit);
			while(cursor.hasNext()) {
				DBObject obj = cursor.next();
				try {
					DBObject rtObj = (DBObject) obj.get("retweeted_status");
					if(rtObj != null) {
					
						Item rtItem = new Item(rtObj);
					
						Query<Item> q = ds.createQuery(Item.class).filter("_id = ", rtItem.getId());
						if(itemDAO.exists(q)) {
							//System.out.println(rtItem.getId() + " exists!");
							UpdateOperations<Item> ops = ds.createUpdateOperations(Item.class).inc("reposts");
							itemDAO.update(q, ops);
						}
						else {
							String text = rtItem.getText();
							
							text = text.replaceAll( "([\\ud800-\\udbff\\udc00-\\udfff])", "");
							text = text.replaceAll( "([^\\x00-\\x7f-\\x80-\\xad])", " ");
							rtItem.setCleanText(text);
						
							text = text.replaceAll("&amp;", " and ");
							text = text.replaceAll("&lt;", " ");
							text = text.replaceAll("&gt;", " ");
							text = text.replaceAll("&quot;", " ");
						
							try {
								Collection<NamedEntity> entities = entitiesExtractor.extractEntities(text);
								rtItem.addNamedEntities(entities);
							} catch (Exception e) {			
								//System.out.println();
								//System.out.println(e.getMessage());
							}
						
							try {
								List<TaggedToken> posTags = posExtractor.getPosTags(text);
								String posTagsString = posExtractor.posTagsString(posTags);
								Set<String> pn = posExtractor.getProperNouns(posTags);
							
								rtItem.addProperNouns(pn);
								rtItem.setPosTags(posTagsString);	
							}	
							catch (Exception e) {
								//System.out.println(rtItem.getText());
								//System.out.println(e.getMessage());
							}
						
							rtItem.setAccepted(filter.accept(rtItem));
							itemDAO.save(rtItem);
						
						}
					}
					else {
						Item item = new Item(obj);
						String text = item.getText();
						//text = text.replaceAll("[^\\p{L}\\p{Nd}]+", "");
						text = text.replaceAll( "([\\ud800-\\udbff\\udc00-\\udfff])", " ");
						text = text.replaceAll( "([^\\x00-\\x7f-\\x80-\\xad])", " ");
						
						item.setCleanText(text);
					
						text = text.replaceAll("&amp;", " and ");
						text = text.replaceAll("&lt;", " ");
						text = text.replaceAll("&gt;", " ");
						text = text.replaceAll("&quot;", " ");
						try {
							Collection<NamedEntity> entities = entitiesExtractor.extractEntities(text);
							for(NamedEntity ne : entities) {
								String name = ne.getName();
								name = name.replaceAll("\\s+", " ");
								name = name.trim();
								
								ne.setName(name);
							}
							item.addNamedEntities(entities);
						} catch (Exception e) {
							//System.out.println(item.getText());
							//System.out.println(e.getMessage());
						}
					
						try {
							List<TaggedToken> posTags = posExtractor.getPosTags(text);
							String posTagsString = posExtractor.posTagsString(posTags);
							Set<String> pn = posExtractor.getProperNouns(posTags);
							
							item.addProperNouns(pn);
							item.setPosTags(posTagsString);
						}
						catch (Exception e) {
							//System.out.println(item.getText());
							//System.out.println(e.getMessage());
						}
					
						item.setAccepted(filter.accept(item));
						itemDAO.save(item);
					
					}
				
				} catch (ParseException e) {
					e.printStackTrace();
				}	
			}
			try {
				System.out.println(((i+1)*limit) + " items moved");
				cursor.close();
			}
			catch(Exception e) {
				
			}
		}
	}
	
	public static void main(String...args) throws Exception {
		mv(args[0], args[1], args[2], args[3], args[4]);	
	}
}
