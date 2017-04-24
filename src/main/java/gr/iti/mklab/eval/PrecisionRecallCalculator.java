package gr.iti.mklab.eval;

import gr.iti.mklab.dao.MorphiaDAO;
import gr.iti.mklab.index.VisualIndex;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.MediaItem;
import gr.iti.mklab.utils.L2;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

import com.aliasi.classify.ScoredPrecisionRecallEvaluation;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

public class PrecisionRecallCalculator {

	private static boolean interpolation = false;
	
	private static String dataset = "BaltimoreRiots";

	public static void main(String...args) throws Exception {
		
		MorphiaDAO<MediaItem> dao = new MorphiaDAO<MediaItem>("xxx.xxx.xxx.xxx", dataset, MediaItem.class);
		
		Map<String, String> mediaMap = getMediaMap(dao);
		
		ScoredPrecisionRecallEvaluation eval = new ScoredPrecisionRecallEvaluation();
		Reader reader = new FileReader("/disk1_data/Datasets/" + dataset + "/topics_summary.tsv");
		List<String> lines = IOUtils.readLines(reader);
		reader.close();
		
		/* 
		Collections.shuffle(lines);
		Collections.shuffle(lines);
		//Collections.shuffle(lines);
		Collections.shuffle(lines);
		Collections.shuffle(lines);
		//Collections.shuffle(lines);
		//Collections.shuffle(lines);
		Collections.shuffle(lines);
		 */
		
		System.out.println(lines.size());
		
		List<String> ids = new ArrayList<String>();
		lines = lines.subList(0, 10);
		for(String line : lines) {
			String[] parts = line.split("\t");
			String id = parts[0];
			ids.add(id);
		}
		
		VisualIndex vIndex = new VisualIndex("/disk2_data/VisualIndex/learning_files", "/disk1_data/Datasets/"+dataset);
		double avs = getAVS(ids, vIndex, mediaMap);
		System.out.println("AVS: " + avs);
		
//		for(int i=0; i<100; i++) {
//			if(i%100==0)
//			System.out.println(i);
//			String line = lines.get(i);
//			String[] parts = line.split("\t");
//			String id = parts[0];
//			Double score = Double.parseDouble(parts[1]);
//			double relevance = getRelevance(id, dao);
//			if(relevance == -1)
//				continue;
//			
//			eval.addCase(relevance > 2, score);
//		}
//		double[][] curve = eval.prCurve(interpolation);
//		writeResults(curve, "/disk1_data/Datasets/" + dataset + "/stwr_precrec.tsv");
//		
//		System.out.println("P@5 = " + eval.precisionAt(5));
//		System.out.println("P@10 = " + eval.precisionAt(10));
//		System.out.println("P@100 = " + eval.precisionAt(100));
//		System.out.println("P@500 = " + eval.precisionAt(500));
//		System.out.println("P@1000 = " + eval.precisionAt(1000));
//		 
//		System.out.println("RR = " + eval.reciprocalRank());
		
		
		/* 
		MongoClient client = new MongoClient("160.40.50.207");
		DB database = client.getDB(dataset);
		DBCollection collection = database.getCollection("Cliques"); 
				
		DBCursor cursor = collection.find();
		while(cursor.hasNext()) {
			DBObject obj = cursor.next();
			double relevance = Double.parseDouble(obj.get("relevance").toString());
			int numOfJudgements = Integer.parseInt(obj.get("numOfJudgements").toString());
			BasicDBList judgements = (BasicDBList) obj.get("judgements");
			BasicDBList members = (BasicDBList) obj.get("members");
			for(Object member : members) {
				Query<MediaItem> query = dao.getQuery().filter("_id =", member);
				UpdateOperations<MediaItem> ops = dao.getUpdateOperations()
					.set("relevance", relevance)
					.set("numOfJudgements", numOfJudgements)
					.set("judgements", judgements);
				
				dao.update(query, ops);
				
			}
			
		}
		*/

	}
	
	public static double getAVS(List<String> summary, VisualIndex vIndex, Map<String, String> mediaMap) {
		 
		double avgSimilarity = 0;
		int pairs = 0;
		
		for(int i=0; i<(summary.size()-1); i++) {
			
			String id1 = summary.get(i);
			String mId1 = mediaMap.get(id1);
			double[] v1 = vIndex.getVector(mId1);
			if(v1 == null)
				continue;
			
			for(int j=i+1; j<summary.size(); j++) {
				String id2 = summary.get(j);
				String mId2 = mediaMap.get(id2);
				double[] v2 = vIndex.getVector(mId2);
				if(v2 == null)
					continue;
				
				avgSimilarity += L2.similarity(v1, v2);
				pairs++;
			}	
		}
		
		avgSimilarity = avgSimilarity / pairs;

		return avgSimilarity;
		
	}
	
	public static Map<String, String> getMediaMap(MorphiaDAO<MediaItem> dao) {
		Map<String, String> map = new HashMap<String, String>();
		List<MediaItem> mediaItems = dao.get();
		for(MediaItem mi : mediaItems) {
			String mId = mi.getId();
			List<String> references = mi.getReferences();
			for(String ref : references) {
				map.put(ref, mId);
			}
		}
		return map;
	}
	
	public static double getRelevance(String id, MorphiaDAO<MediaItem> dao) {
		Double relevance = 0.;
		Query<MediaItem> query = dao.getQuery();
		if(id.contains("-")) {
			String[] ids = id.split("-");
			query.filter("references in", ids);
		}
		else {
			query.filter("references =", id);
		}
		
		query.filter("width >", 250);
		query.filter("height >", 250);
		query.filter("concept nin", new String[] {"memes", "porn", "keepcalm", "messages"});
		
		List<MediaItem> mediaItems = dao.get(query);
		if(mediaItems != null && !mediaItems.isEmpty()) {
			int judgements = 0;
			for(MediaItem mi : mediaItems) {
				relevance += (mi.getRelevance() * mi.getJudgements().size());
				judgements += mi.getJudgements().size();
			}
			if(judgements > 0) {
				relevance = relevance / judgements;
			}
		}
		else{
			return -1;
		}
		return relevance;
	}

	public static void writeResults(double[][] curve, String file) throws IOException {
		List<String> lines = new ArrayList<String>();
		for(double[] entry : curve) {
			String line =  entry[0] + "\t" + entry[1];
			lines.add(line);
		}
		Writer writer = new FileWriter(file);
		IOUtils.writeLines(lines, "\n", writer);
		writer.close();
	}
	
}