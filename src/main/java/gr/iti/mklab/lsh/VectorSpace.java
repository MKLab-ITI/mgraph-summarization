package gr.iti.mklab.lsh;

import gr.iti.mklab.vocabulary.Vocabulary;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.List;


public class VectorSpace implements Iterable<Entry<Integer, Double>>, Comparator<VectorSpace>{
	
	static int c = 0;

	String id;
	//String text;
	String tokens[];
	Map<String, Integer> terms = new HashMap<String, Integer>();
	Map<Integer, Double> features = null;
        
        public static int boost_hashtags_factor=1;
        public static int boost_entities_factor=1;
        public static Set<String> entities;
	
	long ts;
	
	public VectorSpace() { }

//	public VectorSpace(String id, List<String> intokens,int boost_hashtags_factor,int boost_entities_factor, Set<String> entities) {
	public VectorSpace(String id, List<String> intokens) {
		this.ts = System.currentTimeMillis();
		this.id = id;
		this.tokens=new String[intokens.size()];
		int i=0;
        for(String token:intokens){
        	tokens[i++]=token;
        }
		for(String term : tokens) {
                        int term_increase=1;
                        if(entities.contains(term))
                            term_increase=boost_entities_factor;
                        if(term.startsWith("#"))
                            term_increase=boost_hashtags_factor;
			if(terms.containsKey(term)) {
				int f = terms.get(term)+term_increase;
				terms.put(term, f);
			}
			else{
				terms.put(term, term_increase);
			}
		}
	}


	public int tf(String term) {
		Integer f = terms.get(term);
		if(f==null)
			return 0;
		return f.intValue();
	}
	
	public double idf(String term) {
		return Vocabulary.getIdf(term);
	}
	
	public int index(String term) {
		return Vocabulary.getIndex(term);
	}
	
	public String[] tokens() {
		return tokens;
	}
	
	public String id(){
		return id;
	}
	
	@Override
	public Iterator<Entry<Integer, Double>> iterator() {
		if(features!=null)
			return features.entrySet().iterator();
		features = new HashMap<Integer, Double>();
		for(String term : terms.keySet()) {
			int index = index(term);
			double w = tf(term) * idf(term);
			features.put(index, w);
		}
		return features.entrySet().iterator();
	}

	public double cosineSimilarity(VectorSpace candidate) {
		
		Iterator<Entry<Integer, Double>> it1 = this.iterator();
		double magnitude1 = 0;
		while(it1.hasNext()) {
			magnitude1 += Math.pow(it1.next().getValue(), 2);
		}
		Iterator<Entry<Integer, Double>> it2 = candidate.iterator();
		double magnitude2 = 0;
		while(it2.hasNext()) {
			magnitude2 += Math.pow(it2.next().getValue(), 2);
		}
		
		double denominator = Math.sqrt(magnitude1 * magnitude2);
		if(denominator<0.000000000000001)
			return 0.0;
		
		double numerator = 0.0;
		double w1=-1, w2=-1;
		Set<String> both = new HashSet<String>(terms.keySet());
        both.retainAll(candidate.terms.keySet());
		for(String term : both) {
			w1 = this.tf(term) * this.idf(term); 
			w2 = candidate.tf(term) * candidate.idf(term); 
			numerator += w1 * w2;
		}
		//if((numerator / denominator)>0.1){
			//System.out.println("COSINE SIMILARITY");
			//System.out.println(this.text);
			//System.out.println(candidate.text);
			//System.out.println(tokens.length +" tokens "+ candidate.tokens.length);
			//System.out.println(w1 +" w "+ w2);
			//System.out.println(terms.keySet().size() +" b "+ candidate.terms.keySet().size());
			//System.out.println(numerator +" - "+ denominator);
			//System.out.println(numerator / denominator);
		//}
		return numerator / denominator;
	}

	@Override
	public int compare(VectorSpace vsm1, VectorSpace vsm2) {
		if (vsm1.ts > vsm2.ts)
			return 1;
		else if (vsm1.ts < vsm2.ts)
			return -1;
		else
			return 0;
	}
}