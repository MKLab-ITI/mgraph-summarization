package gr.iti.mklab.summarization;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.Set;

import org.mongodb.morphia.query.Query;

import gr.iti.mklab.dao.MorphiaDAO;
import gr.iti.mklab.models.ExtendedTimeline;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.Pair;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.utils.CollectionsUtils;
import gr.iti.mklab.vocabulary.Vocabulary;

public class CentroidSummarizer implements Summarizer {


	private double a = 0.7, b = 0.3;
	@Override
	public Set<String> summarize(Map<String, Vector> vectors, int L, Pair<Long, Long> window) {
		return null;
	}


	@Override
	public Set<String> summarize(Map<String, Vector> vectors) {
		return null;
	}


	@Override
	public Set<String> summarize(Map<String, Vector> vectors, Pair<Long, Long> window) {
		return null;
	}

	private static double getRedandancy(Vector itemVector, Map<String, Vector> vectors) {
		double maxRedundancy = 0;
		for(Vector vector : vectors.values()) {
			double redundancy = itemVector.cosine(vector);
			if(maxRedundancy < redundancy) {
				maxRedundancy = redundancy;
			}
		}
		return maxRedundancy;
	}


	@Override
	public Set<String> summarize(Map<String, Vector> vectors, double compression, Pair<Long, Long> window) {
		return null;
	}


	public Set<String> summarize(Map<String, Vector> vectors, int L) {
		
		Vector centroid = new Vector();
		for(Vector vector : vectors.values()) {
			if(vector != null) {
				centroid.mergeVector(vector);
			}
		}
		
		Map<String, Double> coverages = new HashMap<String, Double>();
		for(Entry<String, Vector> vectorEntry : vectors.entrySet()) {
			String id = vectorEntry.getKey();
			Vector vector = vectorEntry.getValue();
			double coverage = centroid.cosine(vector);
			coverages.put(id, coverage);
		}
		
		Set<String> summary = new HashSet<String>();
		int S = summary.size();
		
		Set<String> ids = new HashSet<String>(vectors.keySet());
		while(summary.size() < L) {
			
			String selected = null;
			double maxScore = 0;
			for(String id : ids) {
				Vector vector = vectors.get(id);
				if(vector == null) {
					continue;
				}
				
				double coverage = coverages.get(id);
				double redundancy = getRedandancy(vector, CollectionsUtils.mapSlice(vectors, summary));
				
				double score = a * coverage - b * redundancy;
				if(maxScore < score) {
					selected = id;
					maxScore = score;
				}
			}
			
			if(selected != null) {
				summary.add(selected);
				ids.remove(selected);
			}
			else {
				break;
			}
			
			if(S == summary.size()) {
				break;
			}
			S = summary.size();
			
		}
		
		return summary;
	}
	
	public static void main(String...args) throws Exception {
		
		MorphiaDAO<Item> dao = new MorphiaDAO<Item>("", "", Item.class);
		Query<Item> query = dao.getQuery().filter("accepted =", Boolean.TRUE);
		System.out.println(dao.count(query) + " items to be proccessed");
		
		Map<String, Item> itemsMap = new HashMap<String, Item>();
		Iterator<Item> it = dao.iterator(query);
		while(it.hasNext()) {
			Item item = it.next();
			itemsMap.put(item.getId(), item);
		}
		System.out.println("Loaded.");
		
		Map<String, Vector> vectorsMap = Vocabulary.createVocabulary(itemsMap.values(), 2);
		
		ExtendedTimeline tml = ExtendedTimeline.createTimeline(1, TimeUnit.HOURS, vectorsMap, itemsMap.values());
		List<Pair<Long, Long>> peakWindows = tml.detectPeakWindows();
		
		CentroidSummarizer summarizer = new CentroidSummarizer();
		double compression = 0.01;
		for(Pair<Long, Long> window : peakWindows) {
			List<String> items = tml.getItems(window);
			if(items.size() > 1000) {
				System.out.println(new Date(window.left) + " - " + new Date(window.right));
				System.out.println("#items: " + items.size());
				
				int L = (int) Math.round(compression * items.size());
				System.out.println("target: " + L);
				
				Map<String, Vector> windowVector = CollectionsUtils.mapSlice(vectorsMap, items);
				Set<String> summary = summarizer.summarize(windowVector, L);

				System.out.println("|S|=" + summary.size());
			}
		}
		
	}

}
