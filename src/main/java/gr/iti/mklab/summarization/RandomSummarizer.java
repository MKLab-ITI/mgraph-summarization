package gr.iti.mklab.summarization;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import gr.iti.mklab.models.Pair;
import gr.iti.mklab.models.Vector;

public class RandomSummarizer implements Summarizer {

	@Override
	public Set<String> summarize(Map<String, Vector> messages, int L) {
		Set<String> s = new HashSet<String>();
		
		List<String> ids = new ArrayList<String>(messages.keySet());
		if(ids.isEmpty())
			return s;
			
		Random R = new Random();
		while(s.size() < Math.min(L, ids.size()-1)) {
			int index = R.nextInt(ids.size()-1);
			String id = ids.get(index);
			s.add(id);
		}
		
		return s;
	}

	public Set<String> summarize(List<String> ids, Map<String, Vector> messages, int L) {
		Set<String> s = new HashSet<String>();
		if(ids.isEmpty()) {
			return s;
		}
		
		Random R = new Random();
		while(s.size() < Math.min(L, ids.size()-1)) {
			int index = R.nextInt(ids.size()-1);
			String id = ids.get(index);
			s.add(id);
		}
		
		return s;
	}
	
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

	@Override
	public Set<String> summarize(Map<String, Vector> vectors, double compression, Pair<Long, Long> window) {
		return null;
	}

	
}
