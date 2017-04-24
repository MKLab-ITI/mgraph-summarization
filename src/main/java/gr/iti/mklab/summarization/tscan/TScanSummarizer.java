package gr.iti.mklab.summarization.tscan;

import jama.EigenvalueDecomposition;
import jama.Matrix;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import gr.iti.mklab.dao.MorphiaDAO;
import gr.iti.mklab.models.ExtendedTimeline;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.Pair;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.summarization.Summarizer;
import gr.iti.mklab.utils.CollectionsUtils;
import gr.iti.mklab.utils.Sorter;
import gr.iti.mklab.vocabulary.Vocabulary;

import org.mongodb.morphia.query.Query;

public class TScanSummarizer implements Summarizer {

	private double similarityThreshold = 0.01;
	private int H = 10;
	
	private double segmentThreshold = 0.1;
	private Map<String, Item> itemsMap;

	public TScanSummarizer(Collection<Item> items) {
		this.itemsMap = new HashMap<String, Item>();
		for(Item item : items) {
			itemsMap.put(item.getId(), item);
		}
	}
	
	@Override
	public Set<String> summarize(Map<String, Vector> vectors) {
		return null;
	}

	@Override
	public Set<String> summarize(Map<String, Vector> vectors,
			Pair<Long, Long> window) {
		return null;
	}
	
	@Override
	public Set<String> summarize(Map<String, Vector> vectors, int L) {
		
		Set<String> keys = vectors.keySet();
		List<String> ids = sortTimesliceIds(keys);
		
		System.out.println("Start JAMA Eigenvalue Decomposition.");
		double[][] VL = eigenvectorDecomposition(vectors, L);
		System.out.println("Done!");
		
		System.out.println("Start Event Segmentation");
		List<Event> events = segment(VL);
		
		System.out.println("#Events: " + events.size());
		
		Map<String, Double> scores = new HashMap<String, Double>();
		for(Event event : events) {
			int index = event.maxAmplBlock;
			if(index >= 0) {
				String id = ids.get(index);
				Double score = scores.get(id);
				if(score == null || score < event.maxAmplitude) {
					scores.put(id, event.maxAmplitude);
				}
			}
		}
		
		// Sort by weight
		List<Entry<String, Double>> sortedWeights = Sorter.sort(scores);

		Set<String> summary = new HashSet<String>();
		for(int i = 0; i < Math.min(L, sortedWeights.size()); i++) {
			Entry<String, Double> entry = sortedWeights.get(i);
			summary.add(entry.getKey());
		}
		return summary;
	}
	
	@Override
	public Set<String> summarize(Map<String, Vector> vectors, int L, Pair<Long, Long> window) {
		return null;
	}

	@Override
	public Set<String> summarize(Map<String, Vector> vectors, double compression, Pair<Long, Long> window) {
		return null;
	}

	private double[][] eigenvectorDecomposition(Map<String, Vector> vectors, int themes) {

		Set<String> keys = vectors.keySet();
		List<String> ids = sortTimesliceIds(keys);
		
		int n = ids.size();
		Matrix A = new Matrix(n, n);
		
		for(int i = 0; i<n; i++) {
			for(int j = i; j<n; j++) {		
				Vector v1 = vectors.get(ids.get(i));
				Vector v2 = vectors.get(ids.get(j));
				
				double w = .0;
				if(v1 != null && v2 != null) {
					w = v1.cosine(v2);
					if(w < similarityThreshold) {
						w = .0;
					}
				}				
				A.set(i, j, w);
				A.set(j, i, w);				
			}	
		}
		EigenvalueDecomposition evd = new EigenvalueDecomposition(A);
		
		double[][] VL = new double[themes][n];
		Matrix V = evd.getV();
		for(int i = 1; i<=themes; i++) {
			for(int j = 0; j<n; j++) {
				VL[i-1][j] = V.get(n-i, j);
			}
		}
		return VL;	
	}
	
	private List<String> sortTimesliceIds(Set<String> keys) {
		List<String> ids = new ArrayList<String>();
		// Sort by timestamp 
		Map<String, Long> map = new HashMap<String, Long>();
		for(String key : keys) {
			Item item = itemsMap.get(key);
			if(item != null) {
				map.put(key, item.getPublicationTime());
			}
		}
		List<Entry<String, Long>> sortedIds = Sorter.sortByLong(map);
		for(Entry<String, Long> entry : sortedIds) {
			ids.add(entry.getKey());
		}
		Collections.reverse(ids);
		
		return ids;
	}
	
	private List<Event> segment(double[][] VL) {
		
		List<Event> events = new ArrayList<Event>();
		
		for(int j = 0; j<VL.length; j++) {
			try {
				double[] u = VL[j];
				double[] energy = energy(u, H);
				
				
				int maxAmplBlock = -1;
				double maxAmplitude = 0;
				List<Integer> blocks = new ArrayList<Integer>();
				for(int index=0; index<energy.length; index++) {
					if(energy[index] >= segmentThreshold) {
						blocks.add(index);
						if(Math.abs(u[index]) >= maxAmplitude) {
							maxAmplitude = Math.abs(u[index]);
							maxAmplBlock = index;
						}
					}
					else if(!blocks.isEmpty()) {
						if(blocks.size() > 1) {
							Event event = new Event();
					
							event.theme = j;
							event.maxAmplBlock = maxAmplBlock;
							event.maxAmplitude = maxAmplitude;
							
							event.eb = Collections.max(blocks);
							event.bb = Collections.min(blocks);
					
							event.blocks.addAll(blocks);
							events.add(event);
						}
					
						maxAmplBlock = -1;
						maxAmplitude = 0;
						blocks.clear();
					}
				}
			}
			catch(Exception e) {
				System.out.println("Exception for theme: " + j + ", " + e.getMessage());
			}
		}
		
		return events;
	}
	
	public void normalize(double[] u) {
		double length = length(u);
		
		for(int i=0; i<u.length; i++) {
			u[i] = u[i]/length;
		}
	}
	
	public double length(double[] u) {
		double length = 0;
		for(double v : u) {
			length += Math.pow(v, 2);
		}
		length = Math.sqrt(length);
		
		return length;
		
	}
	
	private double[] energy(double[] u, int H) {	
		
		double[] energy = new double[u.length];
		
		int h = (H-1)/2;
		
		for(int i = 0; i<h; i++) {
			energy[i] = .0;
			energy[u.length-i-1] = .0;
		}
		
		for(int i = h; i<(u.length-h); i++) {
			double e = 0;
			for(int j = i-h; j<=(i+h); j++) {
				e += Math.pow(u[j], 2);
			}
			energy[i] = e / (double)H;
		}
		
		normalize(energy);
		return energy;
	}
	
	public static void main(String[] args) throws Exception {
		System.out.println("Load items: ");
		
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
		
		ExtendedTimeline tml = ExtendedTimeline.createTimeline(60, TimeUnit.MINUTES, vectorsMap, itemsMap.values());
		List<Pair<Long, Long>> peakWindows = tml.detectPeakWindows();
		
		TScanSummarizer tscan = new TScanSummarizer(itemsMap.values());
		
		double compression = 0.1;
		for(Pair<Long, Long> window : peakWindows) {
			
			List<String> items = tml.getItems(window);
			
			if(items.size() > 1000) {
				System.out.println(new Date(window.left) + " - " + new Date(window.right));
				System.out.println("#items: " + items.size());
			
				int L = (int) (compression * items.size());
				System.out.println("Target Langth: " + L);
			
				Map<String, Vector> windowVectors = CollectionsUtils.mapSlice(vectorsMap, items);
				Set<String> summary = tscan.summarize(windowVectors, L);
				System.out.println("summary = " + summary.size());		
				
				System.out.println("===============================================");
			}
			
			
			
		}
	}
	
}