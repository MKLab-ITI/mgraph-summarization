package gr.iti.mklab.analysis;

import gr.iti.mklab.extractors.EntitiesExtractor;
import gr.iti.mklab.extractors.PosExtractor;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.NamedEntity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.jsoup.Jsoup;

import cmu.arktweetnlp.Tagger.TaggedToken;

public class ItemFilter {

	private String serializedClassifier3Class = "edu/stanford/nlp/models/ner/english.all.3class.distsim.crf.ser.gz";
	private EntitiesExtractor entitiesExtractor;
	
	private String posModelFilename = "/disk1_data/workspace/git/microblogging-summarization/model.20120919";
	private PosExtractor posExtractor;
	
	private String posRegex = ".*[AN]*.*V+.*";
	
	public ItemFilter() throws IOException {
		entitiesExtractor = new EntitiesExtractor(serializedClassifier3Class);
		posExtractor = new PosExtractor(posModelFilename);
	}
	
	public boolean lengthAccept(String text) {	
		if(text == null || text.length()< 20) {
			return false;
		}
		
		return true;
	}
	
	public boolean tokensAccept(List<String> tokens) {	
		if(tokens == null || tokens.size() < 3)
			return false;
		
		return true;
	}
	
	public boolean accept(Item item) {
	
		String text = item.getText();
		if(text == null) {
			return false;
		}
		
		Map<String, String> media = item.getMediaItems();
		if(media != null && !media.isEmpty()) {
			try {
				text = text.replaceAll( "([\\ud800-\\udbff\\udc00-\\udfff])", "");
				text = text.replaceAll( "([^\\x00-\\x7f-\\x80-\\xad])", " ");
				item.setCleanText(text);
			
				
				text = Jsoup.parse(text).text();
				text = text.replaceAll("&", " and ");
				
				List<TaggedToken> posTags = posExtractor.getPosTags(text);
				String posTagsString = posExtractor.posTagsString(posTags);
				Set<String> pn = posExtractor.getProperNouns(posTags);
			
				item.addProperNouns(pn);
				item.setPosTags(posTagsString);	
			}	
			catch (Exception e) { }
			try {
				Collection<NamedEntity> entities = entitiesExtractor.extractEntities(text);
				item.addNamedEntities(entities);
			} catch (Exception e) {	}
			
			return true;
		}
	
		if(!lengthAccept(text)) {
			System.out.println("Discard due text length (" + text.length() + ")");	
			return false;
		}
		
		List<String> urls = item.getUrls();
		if(urls.size() > 3) {
			System.out.println("Discard due urls");
			return false;
		}

		List<String> tokens = TextAnalyser.getTokens(text);
		if(!tokensAccept(tokens)) {
			System.out.println("Discard due tokens (" + tokens.size() + ")");
			return false;	
		}
		
		List<String> hashtags = item.getHashtags();
		if(hashtags.size() > 4 && hashtagsRatio(hashtags, tokens)>0.5) {
			System.out.println("Discard due hashtags");
			return false;
		}
		
		text = text.replaceAll( "([\\ud800-\\udbff\\udc00-\\udfff])", "");
		text = text.replaceAll( "([^\\x00-\\x7f-\\x80-\\xad])", " ");
		item.setCleanText(text);
		
		text = Jsoup.parse(text).text();
		text = text.replaceAll("&", " and ");
		
		try {
			List<TaggedToken> posTags = posExtractor.getPosTags(text);
			String posTagsString = posExtractor.posTagsString(posTags);
			Set<String> pn = posExtractor.getProperNouns(posTags);
		
			item.addProperNouns(pn);
			item.setPosTags(posTagsString);	
		}	
		catch (Exception e) { }
		
		if(!posAccept(item.getPosTags())) {
			return false;
		}
		
		try {
			Collection<NamedEntity> entities = entitiesExtractor.extractEntities(text);
			item.addNamedEntities(entities);
		} catch (Exception e) {	}
		
		return true;
	}
	
	public double hashtagsRatio(List<String> hashtags, List<String> tokens) {
		double ratio = .0;
		try {
			if(!tokens.isEmpty()) {
				ratio = (double) hashtags.size() / (double) tokens.size();
			}
		}
		catch(Exception e) {
			
		}
		
		return ratio;
	}
	
	public List<Item> filter(Iterator<Item> it) {
		List<Item> acceptedItems = new ArrayList<Item>();
		while(it.hasNext()) {
			Item item = it.next();
			if(accept(item)) {
				acceptedItems.add(item);
			}
		}
		return acceptedItems;
	}
	
	public List<Item> filter(Collection<Item> items) {
		List<Item> acceptedItems = new ArrayList<Item>();
		for(Item item : items) {
			if(accept(item)) {
				acceptedItems.add(item);
			}
		}
		return acceptedItems;
	}
	
	public boolean posAccept(String taggedText) {
		taggedText = taggedText.trim();
		
		List<String> tags = new ArrayList<String>();
		String[] parts = taggedText.split("\\s+");
		for(String part : parts) {
			String[] taggedToken = part.split("#");
			if(taggedToken.length != 2 || taggedToken[0].equals(taggedToken[1]) ||
					taggedToken[1].equals(",")) {
				continue;
			}
			tags.add(taggedToken[1]);
		}
		
		String postag = StringUtils.join(tags, "");	
		return postag.matches(posRegex);
	}
	
}
