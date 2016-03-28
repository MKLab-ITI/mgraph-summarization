package gr.iti.mklab.eval;

import gr.iti.mklab.analysis.ItemFilter;
import gr.iti.mklab.dao.MorphiaDAO;
import gr.iti.mklab.models.ClusterVector;
import gr.iti.mklab.models.ExtendedTimeline;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.Pair;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.vocabulary.Vocabulary;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

public class Evaluator {

	static String hostname = "160.40.50.207";
	static String dbname = "Sundance2013";
	static String collName = "Tweets";
	
	static String resultsFolder = "./results-sensitivity/";
	static String gtFolder = "./gtFiles/";
	static String[] references1 = {"beis_id=1.csv", "boididou_id=3.csv", "teomrd_id=7.csv", "latas_id=8.csv", "manos_id=9.csv"};
	static String[] references2 = {"lena_id=1.csv", "atsak_id=3.csv", "laapost_id=7.csv", "vivi_id=8.csv", "ntinos_id=9.csv"};
			
	static int[] periods = {1, 3, 7, 8, 9};
	
	static String refDir = "/home/manosetro/Infotainment/refs/";
	
	public static void eval() throws Exception {

		int N = 2;
		
		MorphiaDAO<Item> dao = new MorphiaDAO<Item>(hostname ,dbname, Item.class);
		System.out.println(dao.count() + " items");
		
		List<Item> items = new ArrayList<Item>();
		Iterator<Item> it = dao.iterator();
		while(it.hasNext()) {
			items.add(it.next());
		}
		
		Map<String, Vector> vectors = Vocabulary.createVocabulary(items, N);
		
		for(int i = 0; i<periods.length; i++) {
			
			int period = periods[i];
			
			Set<String> reference1 = getIdsFromFile(gtFolder + references1[i]);
			Set<String> reference2 = getIdsFromFile(gtFolder + references2[i]);
			Set<String> unionReference = new HashSet<String>(reference1);
			unionReference.addAll(reference2);
			
			List<Set<String>> summaries = getSummariesFromFile(resultsFolder + "TT-sensitivity" + period+".csv");
			for(Set<String> summary : summaries) {
				
				double rouge = (getRouge(summary, reference1, vectors) 
						+ getRouge(summary, reference2, vectors))/2.;
				
				System.out.print(rouge + "\t");

			}
			System.out.println();
		}
		
	}
	
	public static void eval(String[] refs, Set<String> summary, Map<String, Vector> vectors) throws FileNotFoundException, IOException {

		Set<String> unionReference = new HashSet<String>();
		
		double rougeSum = 0;
		for(String ref : refs) {
			
			Set<String> reference = getIdsFromFile(ref);
			unionReference.addAll(reference);

				
			double rouge = getRouge(summary, reference, vectors);

			rougeSum += rouge;
		}
		double totalRouge = getRouge(summary, unionReference, vectors);
		System.out.println((rougeSum/refs.length) + "\t" + totalRouge);
		
	}
	
	public static void main(String[] args) throws FileNotFoundException, IOException {
		/*
		DBCollection usersCollection = MongoDAO.getCollection("160.40.50.241", "tiff54", "StreamUsers");
		
		Map<String, Pair<Vector, Long>> vectorsMap = MongoDAO.loadSocialSensorVectors("160.40.50.241", "tiff54", "Items", true);
		
		Map<String, Vector> vectors = new HashMap<String, Vector>(); 
		for(Entry<String, Pair<Vector, Long>> e : vectorsMap.entrySet()) {
			
			DBObject user = usersCollection.findOne(new BasicDBObject());
	        if(user == null)
	            continue;
	        
			vectors.put(e.getKey(), e.getValue().left);
		}
		
		Map<String, Item> itemsMap = MongoDAO.loadSocialSensorItemsMap("160.40.50.241", "tiff54", "Items", vectors.keySet());
		
		ItemFilter filter = new ItemFilter();
		List<Item> items = filter.filter(itemsMap.values());
		System.out.println(items.size() + " items after filtering");

		vectors = Vocabulary.createVocabulary(items, 1);
		
		
		Set<String> ttSummary = getIdsFromFile("/home/manosetro/Infotainment/timetopic.csv");
		Set<String> dvrSummary = getIdsFromFile("/home/manosetro/Infotainment/divrank.csv");
		Set<String> popSummary = getIdsFromFile("/home/manosetro/Infotainment/popularity.csv");
		Set<String> tpSummary = getIdsFromFile("/home/manosetro/Infotainment/topic.csv");
		
		String[] refs = new String[13];
		for(int i=1;i<=13;i++) {
			refs[i-1] = refDir + i + ".csv";
			System.out.println(refs[i-1]);
		}
		
		eval(refs, ttSummary, vectors);
		eval(refs, dvrSummary, vectors);
		eval(refs, popSummary, vectors);
		eval(refs, tpSummary, vectors);
		
	
		eval();
		int N = 2;
		Map<String, LightItem> itemsMap = MongoDAO.loadItemsMap(hostname, dbname, collName, true, "en");		
		Map<String, Vector> vectors = Main.createVocabulary(itemsMap.values(), N);

		for(int i = 0; i<periods.length; i++) {
			
			int period = periods[i];
			
			Set<String> reference1 = getIdsFromFile(gtFolder + references1[i]);
			Set<String> reference2 = getIdsFromFile(gtFolder + references2[i]);
			Set<String> unionReference = new HashSet<String>(reference1);
			unionReference.addAll(reference2);
			
			Set<String> ttSummary = getIdsFromFile(resultsFolder + "timetopic_W" + period+".csv");
			Set<String> dvrSummary = getIdsFromFile(resultsFolder + "divrank_W" + period+".csv");
			Set<String> cntSummary = getIdsFromFile(resultsFolder + "centroid_W" + period+".csv");
			Set<String> popSummary = getIdsFromFile(resultsFolder + "popularity_W" + period+".csv");
			Set<String> tpSummary = getIdsFromFile(resultsFolder + "topic_W" + period+".csv");
			
			double tt1 = getRouge(ttSummary, reference1, vectors);
			double tt2 = getRouge(ttSummary, reference2, vectors);
			double ttU = getRouge(ttSummary, unionReference, vectors);
			double dvr1 = getRouge(dvrSummary, reference1, vectors);
			double dvr2 = getRouge(dvrSummary, reference2, vectors);
			double dvrU = getRouge(dvrSummary, unionReference, vectors);
			double cnt1 = getRouge(cntSummary, reference1, vectors);
			double cnt2 = getRouge(cntSummary, reference2, vectors);
			double cntU = getRouge(cntSummary, unionReference, vectors);
			double pop1 = getRouge(popSummary, reference1, vectors);
			double pop2 = getRouge(popSummary, reference2, vectors);
			double popU = getRouge(popSummary, unionReference, vectors);
			double tp1 = getRouge(tpSummary, reference1, vectors);
			double tp2 = getRouge(tpSummary, reference2, vectors);
			double tpU = getRouge(tpSummary, unionReference, vectors);
			
			System.out.println(tt1  + " - " + tt2 + " - " + (tt1 + tt2)/2. + " - " + ttU);
			System.out.println(dvr1 + " - " + dvr2 + " - " + (dvr1 + dvr2)/2. + " - " + dvrU);
			System.out.println(cnt1 + " - " + cnt2 + " - " + (cnt1 + cnt2)/2. + " - " + cntU);
			System.out.println(pop1 + " - " + pop2 + " - " + (pop1 + pop2)/2. + " - " + popU);
			System.out.println(tp1 + " - " + tp2 + " - " + (tp1 + tp2)/2. + " - " + tpU);
			
			System.out.println("===================================================");
			System.out.println("===================================================");
		
		}
		*/
	}
	
	public static double getRouge(Set<String> summary, Set<String> reference, Map<String, Vector> vectors) {
		ClusterVector cv1 = new ClusterVector();
		for(String id : summary) {
			Vector v = vectors.get(id);
			if(v != null) {
				cv1.mergeVector(v);
			}
		}
		
		ClusterVector cv2 = new ClusterVector();
		for(String id : reference) {
			Vector v = vectors.get(id);
			if(v != null) {
				cv2.mergeVector(v);
			}
		}
		
		Set<String> intersection = new HashSet<String>();
		intersection.addAll(cv1.getWords());
		intersection.retainAll(cv2.getWords());
		
		return ((double) intersection.size()) / ((double)cv2.getWords().size());
	}
	
	public static double getOverlap(Set<String> ids1, Set<String> ids2) {
		
		Set<String> union = new HashSet<String>();
		union.addAll(ids1);
		union.addAll(ids2);
		
		Set<String> intersection = new HashSet<String>();
		intersection.addAll(ids1);
		intersection.retainAll(ids2);
		
		return ((double) intersection.size()) / ((double)union.size());
	}
	
	public static Set<String> getIdsFromFile(String filename) throws FileNotFoundException, IOException {
		Set<String> idsSet = new HashSet<String>();
		List<String> lines = IOUtils.readLines(new FileInputStream(filename));
		for(String line : lines) {
			String[] ids = line.split(",");
			idsSet.addAll(Arrays.asList(ids));
		}
		return idsSet;
	}
	
	public static List<Set<String>> getSummariesFromFile(String filename) throws FileNotFoundException, IOException {
		List<Set<String>> summaries = new ArrayList<Set<String>>();
		List<String> lines = IOUtils.readLines(new FileInputStream(filename));
		for(String line : lines) {
			String[] ids = line.split(",");
			Set<String> idsSet = new HashSet<String>();
			idsSet.addAll(Arrays.asList(ids));
			
			summaries.add(idsSet);
		}
		return summaries;
	}
	
	public static void createGTPeriods() throws Exception {
				
		//DBCollection coll = MongoDAO.getCollection(hostname, dbname, "GTPeriods");
		
		MorphiaDAO<Item> dao = new MorphiaDAO<Item>(hostname ,dbname, Item.class);
		System.out.println(dao.count() + " items");
			
		// Aggressive Filtering 
		ItemFilter filter = new ItemFilter();
		List<Item> items = filter.filter(dao.iterator());
		System.out.println(items.size() + " items after filtering");
		
		Map<String, Vector> vectors = Vocabulary.createVocabulary(items);
		
		ExtendedTimeline timeline = ExtendedTimeline.createTimeline(60, TimeUnit.MINUTES, vectors, items);
		List<Pair<Long, Long>> peaks = timeline.detectPeakWindows();
		System.out.println(peaks.size() + " peaks detected!");
		
		timeline.writeToFile("./timeline.csv");
		
		int selected = 0;
		for(Pair<Long, Long> peak : peaks) {
			selected++;
			int numOfItems = timeline.getFrequency(peak);
			
			System.out.print(new Date(peak.left) + " - " + new Date(peak.right));
			System.out.println(" => #items: " + numOfItems);
			
			List<String> itemIds = timeline.getItems(peak);
			
			DBObject doc = new BasicDBObject("id", selected);
			doc.put("start", peak.left);
			doc.put("end", peak.right);
			
			doc.put("numOfItems", numOfItems);
			doc.put("itemIds", itemIds);
		
			//coll.insert(doc);
			
		}
		System.out.println(selected + " peaks selected!");
	}

}
