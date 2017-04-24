package gr.iti.mklab.models;

import java.util.ArrayList;
import java.util.List;

public class UserProfile {

	private String userId;
	private List<Item> items = new ArrayList<Item>();
	
	public void setUserId(String userId) {
		this.userId = userId;
	}
	
	public String getUserId() {
		return userId;
	}

	public void setItems(List<Item> items) {
		this.items.addAll(items);
	}
	
	public List<Item> getItems() {
		return items;
	}
	
}

