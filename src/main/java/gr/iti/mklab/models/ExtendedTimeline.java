package gr.iti.mklab.models;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;


public class ExtendedTimeline extends Timeline {

	/**
	 * 
	 */
	private static final long serialVersionUID = -6107744748512784691L;
	
	private TreeMap<Long, Vector> vectorsMap = new TreeMap<Long, Vector>();
	
	private TreeMap<Long, List<String>> items = new TreeMap<Long, List<String>>();
	
	private Long minTime = Long.MAX_VALUE, maxTime = Long.MIN_VALUE;
	
	public ExtendedTimeline(int time, TimeUnit tu) {
		super(time, tu);
	}

	public Vector put(Long key, Vector vector, String id) {
		
		super.put(key, 1);
		
		key = (key/div)*div;
		if(key > maxTime)
			maxTime = key;
		if(key < minTime)
			minTime = key;
		
		List<String> binItems = items.get(key);
		if(binItems == null) {
			binItems = new ArrayList<String>();
			items.put(key, binItems);
		}
		binItems.add(id);
		
		Vector currValue = vectorsMap.get(key);
		if(currValue == null) {
			return vectorsMap.put(key, vector);
		}
		else {
			currValue.mergeVector(vector);
			return vectorsMap.put(key, currValue);
		}
	}

	public Vector getVector(Long key) {
		Vector vector = vectorsMap.get(key);
		return vector;
	}
	
	public Vector getVector(Pair<Long, Long> window) {
		Vector mergedVector = new Vector();
		
		long t1 = (window.left / div) * div;
		long t2 = (window.right / div) * div;
		for(long t = t1; t <= t2; t += div) {
			Vector vector = getVector(t);
			if(vector != null) {
				mergedVector.mergeVector(vector);
			}
		}
		return mergedVector;
	}
	
	public List<String> getItems(Long key) {
		
		// TODO: Check if this is right. 
		key = (key / div) * div;
		
		List<String> binItems = items.get(key);
		if(binItems == null) {
			return new ArrayList<String>();
		}
		
		return binItems;
	}
	
	public List<String> getItems(Pair<Long, Long> window) {
		List<String> allItems = new ArrayList<String>();
		
		long t1 = (window.left / div) * div;
		long t2 = (window.right / div) * div;		
		for(long t = t1; t <= t2; t += div) {
			List<String> itemIds = getItems(t);
			if(itemIds != null) {
				allItems.addAll(itemIds);
			}
		}
		return allItems;
	}
	
	public static ExtendedTimeline deserialize(String filename) throws IOException, ClassNotFoundException {
		FileInputStream fileIn = new FileInputStream(filename);
        ObjectInputStream in = new ObjectInputStream(fileIn);
        ExtendedTimeline obj = (ExtendedTimeline) in.readObject();
        in.close();
        fileIn.close();
        
        return obj;
	}
	
	public void concat(ExtendedTimeline other) {
		super.concat(other);
		
		for(Entry<Long, Vector> e1 : other.vectorsMap.entrySet()) {
			Long key = e1.getKey();
			Vector v1 = e1.getValue();
			
			Vector v2 = this.getVector(key);
			if(v2 == null)
				v2 = new Vector();
			
			v2.mergeVector(v1);
			this.vectorsMap.put(key, v2);
		}
		
		for(Entry<Long, List<String>> e1 : other.items.entrySet()) {
			Long key = e1.getKey();
			List<String> v1 = e1.getValue();
			
			List<String> v2 = this.getItems(key);
			if(v2 == null)
				v2 = new ArrayList<String>();
			
			v2.addAll(v1);
			this.items.put(key, v2);
		}
	}
	
	public static ExtendedTimeline createTimeline(int time, TimeUnit tu,Map<String, Pair<Vector, Long>> vectors) {
		ExtendedTimeline timeline = new ExtendedTimeline(time, tu);
		for(Entry<String, Pair<Vector, Long>> e : vectors.entrySet()) {
			String id = e.getKey();
			
			Pair<Vector, Long> value = e.getValue();
			Long publicationTime = value.right;
			Vector vector = value.left;
			if(vector == null || publicationTime == null)
				continue;
			
			timeline.put(publicationTime, vector, id);
		}
		return timeline;
	}
	
	public static ExtendedTimeline createTimeline(int time, TimeUnit tu, Map<String, Vector> vectors, 
			Collection<Item> items) {
		ExtendedTimeline timeline = new ExtendedTimeline(time, tu);
		for(Item item : items) {
			String id = item.getId();
			Long publicationTime = item.getPublicationTime();
			
			Vector vector = vectors.get(id);
			if(vector == null || publicationTime == null)
				continue;
		
			timeline.put(publicationTime, vector, id);
		}
		return timeline;
	}
	
}
