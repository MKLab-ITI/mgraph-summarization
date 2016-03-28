package gr.iti.mklab.models;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

public class NamedEntity implements Comparable<NamedEntity> {

	private String name;
	private String type;
	private Integer frequency = null;
	
	public NamedEntity() {
		
	}
	
	public NamedEntity(String name, String type) {
		this.name = name;
		this.type = type;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getName() {
		return name;
	}
	
	public String getType() {
		return type;
	}
	
	public int getFrequency() {
		return frequency;
	}
	
	public void setFrequency(int frequency) {
		this.frequency = frequency;
	}
	
	public void incFrequency() {
		frequency++;
	}
	
	public String toString() {
		return name;
	}

	public DBObject toDBOject() {
		DBObject obj = new BasicDBObject();
		
		obj.put("name", name);
		obj.put("type", type);
		obj.put("frequency", frequency);
		
		return obj;
	}
	
	@Override
	public int compareTo(NamedEntity other) {
	    final int BEFORE = -1;
	    final int EQUAL = 0;
	    final int AFTER = 1;

	    if (this.frequency < other.frequency) 
	    	return BEFORE;
	    else if (this.frequency > other.frequency) 
	    	return AFTER;
	    else
	    	return EQUAL;
	}
}
