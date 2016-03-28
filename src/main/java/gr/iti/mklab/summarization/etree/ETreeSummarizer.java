package gr.iti.mklab.summarization.etree;

import gr.iti.mklab.analysis.TextAnalyser;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ETreeSummarizer {

	
	public List<InformationBlock> BlockIdentification(List<String> texts) {
		
		for(String text : texts) {
			
			Map<String, Integer> ngramsMap = new HashMap<String, Integer>();
			try {
				List<String> ngrams = TextAnalyser.getNgrams(text, 4);
				for(String ngram : ngrams) {
					Integer f = ngramsMap.get(ngram);
					if(f == null)
						f = 0;
					ngramsMap.put(ngram, ++f);
				}
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		return null;
	}
	
}
