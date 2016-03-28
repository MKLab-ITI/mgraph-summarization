package gr.iti.mklab.summarization;

import java.util.Map;
import java.util.Set;

import gr.iti.mklab.models.Pair;
import gr.iti.mklab.models.Vector;

public interface Summarizer {

	public Set<String> summarize(Map<String, Vector> vectors);
	
	public Set<String> summarize(Map<String, Vector> vectors, Pair<Long, Long> window);
	
	public Set<String> summarize(Map<String, Vector> messages, int L);

	public Set<String> summarize(Map<String, Vector> vectors, int L, Pair<Long, Long> window);
	
	public Set<String> summarize(Map<String, Vector> vectors, double compression, Pair<Long, Long> window);
	
}
