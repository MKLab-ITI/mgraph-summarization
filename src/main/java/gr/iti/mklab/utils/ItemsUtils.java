package gr.iti.mklab.utils;

import gr.iti.mklab.models.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ItemsUtils {

	
	public static Map<String, Item> loadItems(Iterator<Item> iterator) {
		Map<String, Item> itemsMap = new HashMap<String, Item>();
		while(iterator.hasNext()) {
			Item item = iterator.next();
	    	itemsMap.put(item.getId(), item);
		}
		return itemsMap;
	}
	
	public static Map<String, Item> loadUniqueItems(Iterator<Item> iterator) {
		Map<String, Item> itemsMap = new HashMap<String, Item>();
		Map<String, Integer> reposts = new HashMap<String, Integer>();
		while(iterator.hasNext()) {
			Item item = iterator.next();
	    	if(item.isOriginal()) {
	    		itemsMap.put(item.getId(), item);
	    	}
	    	else {
	    		String reference = item.getReference();
	    		Integer r = reposts.get(reference);
	    		reposts.put(reference, (r == null ? 0 : r) + item.getReposts());
	    	}
		}
		
		for(String itemId : reposts.keySet()) {
			Integer r = reposts.get(itemId);
			Item item = itemsMap.get(itemId);
			if(item != null) {
				item.incReposts(r);
			}
		}
		
		return itemsMap;
	}
	
	public static Map<String, List<Item>> getItemsPerUser(Iterator<Item> it) {
		Map<String, List<Item>> itemsPerUser = new HashMap<String, List<Item>>();
		while(it.hasNext()) {
			Item item = it.next();
			String username = item.getUsername();
			if(username == null)
				continue;
			
			List<Item> items = itemsPerUser.get(username);
			if(items == null) {
				items = new ArrayList<Item>();
				itemsPerUser.put(username, items);
			}
			items.add(item);
		}
		return itemsPerUser;
	}
}
