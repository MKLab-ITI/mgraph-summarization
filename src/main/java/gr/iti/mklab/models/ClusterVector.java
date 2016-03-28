package gr.iti.mklab.models;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

public class ClusterVector extends Vector {

	private static final long serialVersionUID = -5870776673853925944L;
	
	private Long ts1 = 0L, ts2 = 0L;
	private Integer n = 0;
	
	private Set<String> f_set = new HashSet<String>();
	
	public ClusterVector() {
		
	}
	
	@SuppressWarnings("unchecked")
	public ClusterVector(DBObject obj) {
		
		super((Map<String, Integer>) obj.get("vector"));
		
		ts1 = (Long) obj.get("timestampSum");
		ts2 = (Long) obj.get("timestampQuadraticSum");
		
		n = (Integer) obj.get("n");
		f_set = (Set<String>) obj.get("focusSet");
	}
	
	public void addVector(String id, Vector v, long ts) {
		
		this.mergeVector(v);
		ts1 += ts;
		ts2 += (ts * ts);
		
		n++;
		f_set.add(id);
	}
	
	public void merge(ClusterVector cv) {
		this.mergeVector(cv);
		
		ts1 += cv.ts1;
		ts2 += cv.ts2;
		
		n += cv.n;
		f_set.addAll(cv.f_set);
	}

	public void subtrack(ClusterVector cv) {
		this.subtrackVector(cv);
		
		ts1 -= cv.ts1;
		ts2 -= cv.ts2;
		
		n -= cv.n;
		f_set.removeAll(cv.f_set);
	}
	
	public Long getTimestampSum() {
		return ts1;
	}
	
	public Long getTimestampQuadraticSum() {
		return ts2;
	}
	
	public Set<String> getFocusSet() {
		return f_set;
	}
	
	public Integer getNumOfVectors() {
		return n;
	}
	
	public Double cosine(Vector other) {
		return other.cosine(this);
	}

	public Double cosine(ClusterVector otherCV) {
		return otherCV.cosine(this);
	}
	
	public String toString() {
		return "Items: " + n + "\n" +
			   "Mean TS: " + ts1 + "\n" +
			   "Words: " + this.getWords() + "\n";
	}
	
	public DBObject toDBObject() {
		DBObject obj = new BasicDBObject();
		obj.put("timestampSum", ts1);
		obj.put("items", n);
		obj.put("vector", this.getWords());
		obj.put("focusSet", f_set);
		
		return obj;
	}
	
}
