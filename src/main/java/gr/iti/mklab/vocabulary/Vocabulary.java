package gr.iti.mklab.vocabulary;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import gr.iti.mklab.analysis.TextAnalyser;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.NamedEntity;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.utils.StringUtils;
import twitter4j.Status;

public class Vocabulary {

	private static Integer docs = 0;
	private static Map<String, Integer> map = new HashMap<String, Integer>();
	
	private static Set<String> boostedTerms = new HashSet<String>();
	public static double boost = 2;
	
	private static Collection<String> stopwords = null;
	
	public static void createFromStatuses(List<Status> statuses) {
		for(Status status : statuses) {
			String text = status.getText();
			
			List<String> tokens;
			try {
				tokens = TextAnalyser.getTokens(text);
			} catch (Exception e) {
				continue;
			}
			addDoc(tokens);
		}
	}

	public static Map<String, Vector> createFromItems(Collection<Item> items) {
		return createFromItems(items, 1);
	}
	
	public static Map<String, Vector> createFromItems(Collection<Item> items, int ngrams) {
		Map<String, Vector> vectors = new HashMap<String, Vector>();
		for(Item item : items) {
			try {
				String id = item.getId();
				
				Vector vector = process(item, ngrams);
				if(vector != null) {
					vectors.put(id, vector);
				}
				
			} catch (Exception e) {
				continue;
			}
		}
		return vectors;
	}
	
	public static Map<String, Vector> createVocabulary(Iterator<Item> it) {
		return createVocabulary(it, 1);
	}
	
	public static Map<String, Vector> createVocabulary(Iterator<Item> it, int ngrams) {
		Vocabulary.reset();
		Map<String, Vector> vectors = new HashMap<String, Vector>();
		
		Set<String> urls = new HashSet<String>();
		while(it.hasNext()) {
			Item item = it.next();
			urls.addAll(item.getUrls());
			String id = item.getId();
			
			Vector vector = process(item, ngrams);
			if(vector != null)
				vectors.put(id, vector);
		}
		
		Collection<String> stowords = Vocabulary.getStopwords();
		stowords.addAll(urls);
		
		Vocabulary.removeWords(stowords);
		
		return vectors;
	}
	
	
	public static Map<String, Vector> createVocabulary(Collection<Item> items) {
		return createVocabulary(items, 1);
	}
	
	public static Map<String, Vector> createVocabulary(Collection<Item> items, int ngrams) {
		Vocabulary.reset();
		Set<String> urls = new HashSet<String>();
		for(Item item : items) {
			urls.addAll(item.getUrls());
		}
		
		Map<String, Vector> vectors = Vocabulary.createFromItems(items, ngrams);
		Collection<String> stowords = Vocabulary.getStopwords();
		stowords.addAll(urls);
		
		Vocabulary.removeWords(stowords);
		
		return vectors;
	}
	
	
	public static void create(List<String> texts) {
		for(String text : texts) {
			
			List<String> tokens;
			try {
				tokens = TextAnalyser.getTokens(text);
			} catch (Exception e) {
				continue;
			}
			addDoc(tokens);
		}
	}
	
	private static Vector process(Item item, int ngrams) {
		String text = item.getText();
		
		text = StringUtils.clean(text, item.getUrls());
		try {
			Set<String> tokens = new HashSet<String>();
			
			List<String> ngramsList = TextAnalyser.getNgrams(text, ngrams);
			tokens.addAll(ngramsList);
			
			/* Experimental Code - Add Named Entities & Proper Nouns*/
			for(NamedEntity ne : item.getNamedEntities()) {
				String nameEntity = ne.getName();
				nameEntity = nameEntity.replaceAll("\\s+", " ").trim();
				tokens.add(nameEntity);
			}
			
			for(String pNoun : item.getProperNouns()) {
				pNoun = pNoun.replaceAll("\\s+", " ").trim();
				tokens.add(pNoun);
			}
			/* Experimental Code */
			
			addDoc(tokens);
			
			Vector vector = new Vector(tokens);
			return vector;
		} catch (IOException e) {
			return null;
		}

	}
	
	public static void addDoc(Collection<String> words) {
		docs++;
		Set<String> tokens = new HashSet<String>(words);
		for(String word : tokens) {
			word = word.replaceAll("\\s+", " ").trim();
			
			Integer df = map.get(word);
			if(df == null) {
				df = 0;
			}
			map.put(word, ++df);
		}
	}
	
	public static Double getDf(String word) {
		word = word.replaceAll("\\s+", " ").trim();
		Integer df = map.get(word);
		if(df == null || docs == 0) {
			return 0d;
		}
		return df.doubleValue() / docs.doubleValue();
	}
	
	public static Double getIdf(String word) {
		word = word.replaceAll("\\s+", " ").trim();
		Integer df = map.get(word);
		if(df == null) {
			return 0d;
		}
		return Math.log(docs.doubleValue()/df.doubleValue());
	}
	
	public static Integer getIndex(String word) {
		word = word.replaceAll("\\s+", " ").trim();
		Set<String> keys = map.keySet();
		List<String> list = new ArrayList<String>(keys);
		
		return list.indexOf(word);
	}
	
	public static Double getBoost(String word) {
		word = word.replaceAll("\\s+", " ").trim();
		if(boostedTerms.contains(word)) {
			return boost;
		}
		else {
			return 1d;
		}
	}
	
	public static Integer getNumOfDocs() {
		return docs;
	}
	
	public static Integer getNumOfTerms() {
		return map.size();
	}
	
	public static Set<String> getTerms() {
		Set<String> terms = new HashSet<String>();
		terms.addAll(map.keySet());
		
		return terms;
	}
	
	public static Set<String> getBoostedTerms() {
		Set<String> terms = new HashSet<String>();
		terms.addAll(Vocabulary.boostedTerms);
	
		return terms;
	}
	
	public static void print() {
		System.out.println(docs + " documents");
		System.out.println(map.size() + " tokens");
	}
	
	public static Collection<String> getStopwords() {
		if(stopwords == null) {
			double minDF = 10.0/docs.doubleValue();
			stopwords = new HashSet<String>();
			for(String word : map.keySet()) {
				Double df = getDf(word);		
				if(df < minDF || df > 0.6) {
					stopwords.add(word);
				}
			}
		}
		return stopwords;
	}

	public static void addBoostedTerms(Collection<String> terms) {
		for(String term : terms) {
			term = term.replaceAll("\\s+", " ").trim();
			boostedTerms.add(term);
		}
	}

	public static void removeWords(Collection<String> words) {
		for(String word : words) {
			map.remove(word);
		}
	}

	public synchronized static void reset() {
		docs = 0;
		map.clear();
		boostedTerms.clear();
	}
}
