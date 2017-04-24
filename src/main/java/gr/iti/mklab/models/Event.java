package gr.iti.mklab.models;

public class Event {

	private String eventId;
	private String title;
	private int clusters;
	
	public Event(String eventId, String title, int clusters) {
		this.eventId = eventId;
		this.title = title;
		this.clusters = clusters;
	}
	
	public String getEventId() {
		return eventId;
	}
	
	public void setEventId(String eventId) {
		this.eventId = eventId;
	}
	
	public String getTitle() {
		return title;
	}
	
	public void setTitle(String title) {
		this.title = title;
	}
	
	public int getClusters() {
		return clusters;
	}
	public void setClusters(int clusters) {
		this.clusters = clusters;
	}
}
