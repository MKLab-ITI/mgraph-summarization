package gr.iti.mklab.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

public class CollectionsUtils {

	public static <K, V> Map<K,V> mapSlice(Map<K,V> map, Collection<K> slice) {
		Map<K, V> sliceMap = new HashMap<K, V>();
		for(K k : slice) {
			if(map.containsKey(k)) {
				sliceMap.put(k, map.get(k));
			}
		}
		return sliceMap;
	}
	
	public static <K, V> Map<K,V> mapFilter(Map<K,V> map, Collection<K> slice) {
		Map<K, V> sliceMap = new HashMap<K, V>();
		
		for(K k : map.keySet()) {
			if(slice.contains(k)) {
				continue;
			}
			sliceMap.put(k, map.get(k));
		}
		return sliceMap;
	}
	
	public static Map<Integer, Float> sortByValue(Map<Integer, Float> map) {
	     List<Entry<Integer, Float>> list = new LinkedList<Entry<Integer, Float>>(map.entrySet());
	     Collections.sort(list, new Comparator<Entry<Integer, Float>>() {
	          public int compare(Entry<Integer, Float> o1, Entry<Integer, Float> o2) {
	               return o2.getValue().compareTo(o1.getValue());
	          }
	     });

	     Map<Integer, Float> result = new LinkedHashMap<Integer, Float>();
	     for (Iterator<Entry<Integer, Float>> it = list.iterator(); it.hasNext();) {
	    	 Entry<Integer, Float> entry = it.next();
	    	 result.put(entry.getKey(), entry.getValue());
	     }
	     return result;
	}
	
	public static double getSumValue(Map<String, Double> map) {
		Double count = 0.0D;
		List<Entry<String, Double>> list = new LinkedList<Entry<String, Double>>(map.entrySet());
	    for (Iterator<Entry<String, Double>> it = list.iterator(); it.hasNext();) {
	        Entry<String, Double> entry = it.next();
	        count += map.get(entry.getKey());
	    }
		return count;
	} 
	

	public static void getTop(double[] array, List<Integer> rankList, int i) {
		int index = 0;
		Set<Integer> scanned = new HashSet<Integer>();
		double max = Double.MIN_VALUE;
		for (int m = 0; m < i && m < array.length; m++) {
			max = Double.MIN_VALUE;
			for (int no = 0; no < array.length; no++) {
				if (!scanned.contains(no)) {
					if (array[no] > max) {
						index = no;
						max = array[no];
					} else if (array[no] == max && Math.random() > 0.5) {
						index = no;
						max = array[no];
					}
				}
			}
				
			if (!scanned.contains(index)) {
				scanned.add(index);
				rankList.add(index);
			}
		}
	}
	 
	public static void getTop(List<Double> array, List<Integer> rankList, int i) {
		int index = 0;
		HashSet<Integer> scanned = new HashSet<Integer>();
		Double max = Double.MIN_VALUE;
		for (int m = 0; m < i && m < array.size(); m++) {
			max = Double.MIN_VALUE;
			for (int no = 0; no < array.size(); no++) {
				if (!scanned.contains(no)) {
					if(array.get(no) > max) {
						index = no;
						max = array.get(no);
					} else if(array.get(no).equals(max) && Math.random() > 0.5) {
						index = no;
						max = array.get(no);
					}
				}
			}
				
			if (!scanned.contains(index)) {
				scanned.add(index);
				rankList.add(index);
			}
		}
	}
		
	public static List<Integer> getTop(float[] array, int i) {
		List<Integer> rankList = new ArrayList<Integer>();
		int index = 0;
		Set<Integer> scanned = new HashSet<Integer>();
		float max = Float.MIN_VALUE;
		for (int m = 0; m < i && m < array.length; m++) {
			max = Float.MIN_VALUE;
			for (int no = 0; no < array.length; no++) {
				if (array[no] >= max && !scanned.contains(no)) {
					index = no;
					max = array[no];
				}
			}
			scanned.add(index);
			rankList.add(index);
		}
		return rankList;
	}
}
