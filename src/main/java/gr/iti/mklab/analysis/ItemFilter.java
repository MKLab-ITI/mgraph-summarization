package gr.iti.mklab.analysis;

import gr.iti.mklab.models.Item;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.StringUtils;

public class ItemFilter {

	private String posRegex = ".*[AN]*.*V+.*";
	
	public boolean lengthAccept(String text) {	
		if(text == null || text.length()< 30)
			return false;
		
		return true;
	}
	
	public boolean tokensAccept(List<String> tokens) {	
		if(tokens == null || tokens.size()< 6)
			return false;
		
		return true;
	}
	
	public boolean accept(Item item) {
		String text = item.getText();
		if(!lengthAccept(text))
			return false;
		
		List<String> urls = item.getUrls();
		if(urls.size() > 2) {
			return false;
		}
		
		List<String> hashtags = item.getHashtags();
		if(hashtags.size() >= 4) {
			return false;
		}
		
		try {
			List<String> tokens = TextAnalyser.getTokens(text);
			if(!tokensAccept(tokens)) {
				return false;	
			}
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		
		if(!posAccept(item.getPosTags())) {
			return false;
		}
		
		return true;
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
		//System.out.println(taggedText);
		
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
		
		//System.out.println(StringUtils.join(tags, "  "));
		
		String postag = StringUtils.join(tags, "");	
		return postag.matches(posRegex);
	}
	
}
