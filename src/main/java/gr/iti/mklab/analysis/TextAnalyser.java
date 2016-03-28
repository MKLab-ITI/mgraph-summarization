package gr.iti.mklab.analysis;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.LowerCaseTokenizer;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.KStemFilter;
import org.apache.lucene.analysis.position.PositionFilter;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.util.Version;

@SuppressWarnings("deprecation")
public class TextAnalyser {

	private static Analyzer stdAnalyzer = new StandardAnalyzer(Version.LUCENE_46);
		 
	public static List<String> getTokens(String text) throws IOException {
		
		List<String> tokens = new ArrayList<String>();
		
		TokenStream ts = stdAnalyzer.tokenStream("text", text);
		CharTermAttribute charTermAtt = ts.addAttribute(CharTermAttribute.class);
		
		ts.reset();
		while (ts.incrementToken()) {
			String token = charTermAtt.toString();
			if(token.length()>1)
				tokens.add(token);
	      }
	      ts.end();  
	      ts.close();
	      
		return tokens;
	}
    
	public static List<String> getNgrams(String text) throws IOException {
		return getNgrams(text, 1);
	}
	
	public static List<String> getNgrams(String text, int N) throws IOException {
		
		List<String> tokens = new ArrayList<String>();
		
		
		Reader reader = new StringReader(text);
		// Tokenizer
		//StandardTokenizer tokenizer = new StandardTokenizer(Version.LUCENE_46, reader);
		
		LowerCaseTokenizer tokenizer = new LowerCaseTokenizer(Version.LUCENE_46, reader);
		
		// Filters
		LowerCaseFilter lowerCaseFilter = new LowerCaseFilter(Version.LUCENE_46, tokenizer); 
		KStemFilter kStemFilter = new KStemFilter(lowerCaseFilter);
		
		CharArraySet stopwords = StopAnalyzer.ENGLISH_STOP_WORDS_SET;
		StopFilter stopFilter = new StopFilter(Version.LUCENE_46, kStemFilter, stopwords);

		TokenStream ts;
		if(N > 1) {

			PositionFilter positionFilter = new PositionFilter(stopFilter);
			
			//@SuppressWarnings("resource")
			//ShingleFilter shingleFilter = new ShingleFilter(positionFilter, N, N);
			//shingleFilter.setOutputUnigrams(false);
			
			@SuppressWarnings("resource")
			ShingleFilter shingleFilter = new ShingleFilter(positionFilter, 2, N);
			shingleFilter.setOutputUnigrams(true);
			
			ts = shingleFilter;
		}
		else {
			ts = stopFilter;
		}
		
		CharTermAttribute charTermAtt = ts.addAttribute(CharTermAttribute.class);
		
		ts.reset();
		while (ts.incrementToken()) {
			String token = charTermAtt.toString();
			if(token.length()>1)
				tokens.add(token);
	      }
	      ts.end();  
	      ts.close();
	      
		return tokens;
	}

	public static void main(String...args) throws IOException {

	}
}
