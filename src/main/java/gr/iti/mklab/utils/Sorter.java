package gr.iti.mklab.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class Sorter {
		
		public static <K> List<Entry<K, Double>> sort(Map<K, Double> map) {
			
			List<Map.Entry<K, Double>> list = new ArrayList<Map.Entry<K, Double>>();
			for(Entry<K, Double> entry : map.entrySet()) {
				list.add(entry);
			}
			
			Collections.sort(list, new Comparator<Entry<K, Double>>() {

				@Override
				public int compare(Entry<K, Double> e1, Entry<K, Double> e2) {
					if(e1.getValue() >= e2.getValue())
						return -1;
					else
						return 1;
				}
				
			});
			
			return list;
		}
		
		public static <K> List<Entry<K, Long>> sortByLong(Map<K, Long> map) {
			
			List<Map.Entry<K, Long>> list = new ArrayList<Map.Entry<K, Long>>();
			for(Entry<K, Long> entry : map.entrySet()) {
				list.add(entry);
			}
			
			Collections.sort(list, new Comparator<Entry<K, Long>>() {

				@Override
				public int compare(Entry<K, Long> e1, Entry<K, Long> e2) {
					if(e1.getValue() >= e2.getValue())
						return -1;
					else
						return 1;
				}
				
			});
			
			return list;
		}
		
	}