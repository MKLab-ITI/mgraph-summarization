package gr.iti.mklab.extractors;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cmu.arktweetnlp.Tagger;
import cmu.arktweetnlp.Tagger.TaggedToken;
import twitter4j.Status;

public class PosExtractor {

	private Tagger tagger;
	
	public PosExtractor(String modelFilename) throws IOException {
		tagger = new Tagger();
		tagger.loadModel(modelFilename);
	}
	
	public List<TaggedToken> getPosTags(String text) {
		text = text.replaceAll("\\r\\n|\\r|\\n", " ");
		text = text.replaceAll("#", " ");
		
		if(text==null || text.length()==0 || text.equals(" "))
			return new ArrayList<TaggedToken>();
		
		List<TaggedToken> taggedTokens = tagger.tokenizeAndTag(text);
		return taggedTokens;
	}
	
	public Set<String> getProperNouns(String text) {
		List<TaggedToken> taggedTokens = getPosTags(text);
		return getProperNouns(taggedTokens);
	}
	
	public String posTagsString(List<TaggedToken> taggedTokens) {
		StringBuffer taggedSentence = new StringBuffer();
		for (TaggedToken token : taggedTokens) {
			taggedSentence.append(token.token+"#"+token.tag + "   ");
		}
		return taggedSentence.toString();
	}
	
	public Set<String> getProperNouns(List<TaggedToken> taggedTokens) {

		Set<String> pNouns = new HashSet<String>();
		if(taggedTokens.isEmpty()) {
			return pNouns;
		}
		
		StringBuffer strBuf = new StringBuffer();
		StringBuffer taggedSentence = new StringBuffer();
		for (TaggedToken token : taggedTokens) {
			int l = strBuf.length();
			if(isProperNoun(token.tag)) {
				if(l>0 && !strBuf.substring(l-1).equals("^"))
					strBuf.append(" ");
				
				strBuf.append(token.token);
			}
			else {
				if(l>0 && !strBuf.substring(l-1).equals("^"))
					strBuf.append("^");
			}
			
			taggedSentence.append(token.tag+"/"+token.token+" ");
		}
		String temp = strBuf.toString();
		temp = temp.toLowerCase();
		
		String[] parts = temp.split("\\^");
		pNouns.addAll(Arrays.asList(parts));
		
		return pNouns;
	}
	
	public Map<Long, Set<String>> getProperNounPerStatus(List<Status> statuses) {
		Map<Long, Set<String>> statusPNouns = new HashMap<Long, Set<String>>();
		for(Status status : statuses) {
			String text = status.getText();
			Set<String> pNouns = getProperNouns(text);
			statusPNouns.put(status.getId(), pNouns);
		}
		return statusPNouns;
	}

	private boolean isProperNoun(String tag) {
		return "^".equals(tag) || "Z".equals(tag);
	}
	
	public static void main(String[] args) throws IOException {
		
		/*
		String modelFilename = "./model.20120919";		
		
		List<Status> statuses = MongoDAO.loadStatuses("160.40.50.207", "Sundance2013", "Tweets", true);
		System.out.println(statuses.size() + " loaded!");

		PosExtractor extractor = new PosExtractor(modelFilename);
		Map<Long, Set<String>> pNounsPerStatus = extractor.getProperNounPerStatus(statuses);
		System.out.println(pNounsPerStatus.size() + " extracted!");
		*/
		
		/*
		Map<String, Integer> pNounsMap = new HashMap<String, Integer>();
		int k = 0;
		for(Status status : statuses) {
			
			if(++k%100==0) {
				System.out.print(".");
				if(k%10000==0) {
					System.out.println("  " + k);
				}
			}
			
			String text = status.getText();
			Set<String> pNouns = extractor.extract(text);
			for(String pNoun : pNouns) {
				if(pNoun.equals(""))
					continue;
				
				Integer f = pNounsMap.get(pNoun);
				if(f == null)
					f = 0;
				pNounsMap.put(pNoun, ++f);
			}
		}
		
		DBCollection collection = MongoDAO.getCollection("160.40.50.207","super_tuesday","ProperNouns");
		for(Entry<String, Integer> pNoun : pNounsMap.entrySet()) {
			DBObject doc = new BasicDBObject();
			doc.put("noun", pNoun.getKey());
			doc.put("value", pNoun.getValue());
			collection.insert(doc);
		}
		*/
		
		
	}

}
