package gr.iti.mklab.summarization.etree;

import gr.iti.mklab.models.ClusterVector;

import java.util.Set;

import com.mongodb.DBObject;

public class InformationBlock extends ClusterVector {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 4885807849792783968L;

	public InformationBlock(DBObject obj) {
		super(obj);
	}

	public Set<String> getMeaning() {
		return this.getWords();
	}
}
