package gr.iti.mklab.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.mongodb.morphia.annotations.Id;

public class MediaItem implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3073382656391375036L;

	@Id
	private String id;

	private String url;
	
	private String concept;
	
	private double conceptScore;
	
	private int width;
	
	private int height;
	
	private List<String> references = new ArrayList<String>();

	private String title;

	private List<Integer> judgements = new ArrayList<Integer>();
	
	private int numOfJudgements = 0;
	
	private double relevance = .0;
	
	public MediaItem() {
		
	}
	
	public MediaItem(String id, String url) {
		this.id = id;
		this.url = url;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}
	
	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getConcept() {
		return concept;
	}

	public void setConcept(String concept, double score) {
		this.concept = concept;
		this.conceptScore = score;
	}

	public int getWidth() {
		return width;
	}

	public void setWidth(int width) {
		this.width = width;
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
	}

	public List<String> getReferences() {
		return references;
	}

	public void setReferences(List<String> references) {
		this.references = references;
	}
	
	public void addReference(String reference) {
		references.add(reference);
	}

	public double getConceptScore() {
		return conceptScore;
	}

	public void setConceptScore(double conceptScore) {
		this.conceptScore = conceptScore;
	}

	public String getTitle() {
		return title;
	}
	
	public void setTitle(String title) {
		this.title = title;
	}

	public List<Integer> getJudgements() {
		return judgements;
	}

	public void setJudgements(List<Integer> judgements) {
		this.judgements.addAll(judgements);
	}
	

	public void addJudgement(Integer judgement) {
		this.judgements.add(judgement);
	}

	public double getRelevance() {
		return relevance;
	}

	public void setRelevance(double relevance) {
		this.relevance = relevance;
	}

	public int getNumOfJudgements() {
		return numOfJudgements;
	}

	public void setNumOfJudgements(int numOfJudgements) {
		this.numOfJudgements = numOfJudgements;
	}
}
