package gr.iti.mklab.utils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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
}
