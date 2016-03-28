package gr.iti.mklab.summarization.tscan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import gr.iti.mklab.analysis.ItemFilter;
import gr.iti.mklab.dao.MorphiaDAO;
import gr.iti.mklab.models.ExtendedTimeline;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.Pair;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.summarization.Summarizer;
import gr.iti.mklab.utils.CollectionsUtils;
import gr.iti.mklab.utils.Sorter;
import gr.iti.mklab.vocabulary.Vocabulary;

import org.ejml.data.DenseMatrix64F;
import org.ejml.factory.DecompositionFactory;
import org.ejml.factory.EigenDecomposition;

public class TScanSummarizer implements Summarizer {

	private static String hostname = "160.40.50.207";
	private static String dbname = "Sundance2013";
	
	private double similarityThreshold = 0.0;
	private int H = 10;
	
	private double segmentThreshold = 0.1;
	private Map<String, Item> itemsMap;
	
	public static void main(String[] args) throws Exception {
		System.out.println("Load items: ");
		
		MorphiaDAO<Item> dao = new MorphiaDAO<Item>(hostname ,dbname, Item.class);
		System.out.println(dao.count() + " items");
		
		// Aggressive Filtering 
		ItemFilter filter = new ItemFilter();
		List<Item> items = filter.filter(dao.iterator());
		System.out.println(items.size() + " items after filtering");
				
		Map<String, Vector> vectors = Vocabulary.createVocabulary(items, 2);
		System.out.println(vectors.size() + " vectors");
		
		System.out.println(Vocabulary.getTerms().size() + " terms in vocabulary");
		
		ExtendedTimeline tml = ExtendedTimeline.createTimeline(24, TimeUnit.HOURS, vectors, items);
		TScanSummarizer tscan = new TScanSummarizer(items);
		
		for(long t = tml.getMinTime(); t <= tml.getMaxTime(); t += tml.getTimeslotLength()) {
			
			if(tml.getFrequency(t) < 100)
				continue;
			
			System.out.println("========================================");
			System.out.println(new Date(t) + " => " + tml.getFrequency(t) + " items.");
			
			int L = (int) (0.1 * tml.getFrequency(t));
			System.out.println("Target Langth: " + L);
			
			List<String> timeslotItems = tml.getItems(t);
			Map<String, Vector> timeslotVectors = CollectionsUtils.mapSlice(vectors, timeslotItems);
			
			Set<String> summary = tscan.summarize(timeslotVectors, L);
			System.out.println("|S("+t+")| = " + summary.size());
		}
	}

	public TScanSummarizer(List<Item> items) {
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
		
		int n = vectors.size();
		int themes = (int) Math.sqrt(n);
		
		DenseMatrix64F A = new DenseMatrix64F(n, n);

		Set<String> keys = vectors.keySet();
		List<String> ids = sortTimesliceIds(keys);
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
		
		System.out.println("Start Eigenvalue Decomposition.");
		EigenDecomposition<DenseMatrix64F> evdFactory = DecompositionFactory.eig(n, true);
		evdFactory.decompose(A);
		System.out.println(evdFactory.getNumberOfEigenvalues() + " eigenvalues, " + themes + " major themes");
		
		System.out.println("Start Event Segmentation");
		List<Event> events = segment(evdFactory, themes);
		
		System.out.println("#Events: " + events.size());
		
		Map<String, Double> scores = new HashMap<String, Double>();
		for(Event event : events) {
			System.out.println(event.toString());
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
	
	private List<Event> segment(EigenDecomposition<DenseMatrix64F> evdFactory, int themes) {
		
		List<Event> events = new ArrayList<Event>();
		
		for(int j = 0; j<Math.min(themes, evdFactory.getNumberOfEigenvalues()); j++) {
			try {
				DenseMatrix64F V = evdFactory.getEigenVector(j);
				double[] u = V.getData();
				//normalize(u);
			
				Double[] energy = energy(u, H);
				//System.out.println(j + " => " + StringUtils.join(energy, " "));
			
				int maxAmplBlock = -1;
				double maxAmplitude = 0;
				List<Integer> blocks = new ArrayList<Integer>();
				for(int index=0; index<energy.length; index++) {
					if(energy[index] >= segmentThreshold) {
						blocks.add(index);
						//System.out.println(index + " => " +  Math.abs(u[index]) + ", " + energy[index]);
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
	
	private Double[] energy(double[] u, int H) {	
		
		Double[] energy = new Double[u.length];
		
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
		
		return energy;
	}
	
	/*
	public Set<String> summarizeSlow(Map<String, Vector> vectors, int L) {
		
		int n = vectors.size();

		Matrix A = new CRSMatrix(n, n);

		Set<String> keys = vectors.keySet();
		List<String> ids = new ArrayList<String>(keys);
		for(int i = 0; i<n; i++) {
			for(int j = i; j<n; j++) {
				
				Vector v1 = vectors.get(ids.get(i));
				Vector v2 = vectors.get(ids.get(j));
				
				if(v1 == null || v2 == null)
					continue;
				
				double w = v1.cosine(v2);
				
				if(w < similarityThreshold) {
					w = 0;
				}
				A.set(i, j, w);
				A.set(j, i, w);				
			}	
		}
		
		System.out.println(A.is(Matrices.SYMMETRIC_MATRIX));
		System.out.println("Start Eigenvalue Decomposition.");
		EigenDecompositor eigenDecompositor = new EigenDecompositor(A);
		Matrix[] vd = eigenDecompositor.decompose();
		
		System.out.println("Start Event Segmentation");
		List<Event> events = segment(vd[0], L);
		
		Map<String, Double> scores = new HashMap<String, Double>();
		for(Event event : events) {
			int index = event.maxAmplBlock;
			if(index >= 0) {
				String id = ids.get(index);
				Double score = scores.get(id);
				if(score == null || score < event.maxAmplitude) {
					scores.put(id, score);
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
	
	private List<Event> segment(Matrix V, int L) {	
		List<Event> events = new ArrayList<Event>();
	
		for(int j = 0; j<Math.min(L, V.columns()); j++) {
			org.la4j.vector.Vector u = V.getColumn(j);
			
			double[] energy = energy(u, H);
			
			int maxAmplBlock = -1;
			double maxAmplitude = 0;
			List<Integer> blocks = new ArrayList<Integer>();
			for(int index=0; index<energy.length; index++) {
				if(energy[index] >= segmentThreshold) {
					blocks.add(index);
					
					if(Math.abs(u.get(index)) > maxAmplitude) {
						maxAmplitude = Math.abs(u.get(index));
						maxAmplBlock = index;
					}
				}
				else if(!blocks.isEmpty()) {
					Event event = new Event();
					
					event.theme = j;
					event.maxAmplBlock = maxAmplBlock;
					event.maxAmplitude = maxAmplitude;
							
					event.eb = Collections.max(blocks);
					event.bb = Collections.min(blocks);
					
					event.blocks.addAll(blocks);
					events.add(event);
					
					maxAmplitude = 0;
					blocks.clear();
				}
			}
		}
		return events;
	}
	
	private double[] energy(org.la4j.vector.Vector u, int H) {
		double[] energy = new double[u.length()];
		
		int h = (H-1)/2;
		
		for(int i = 0; i<h; i++) {
			energy[i] = .0;
			energy[u.length()-i-1] = .0;
		}
		
		for(int i = h; i<(u.length()-h); i++) {
			double e = 0;
			for(int j = i-h; j<=(i+h); j++) {
				e += Math.pow(u.get(j), 2);
			}
			energy[i] = e / (double)H;
		}
		
		return energy;
	}

	 */
	
}