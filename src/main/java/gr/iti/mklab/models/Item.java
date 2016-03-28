package gr.iti.mklab.models;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

import com.mongodb.BasicDBList;
import com.mongodb.DBObject;

@Entity(noClassnameStored=true)
public class Item implements Serializable {
	
	private static final long serialVersionUID = 796797857208699406L;
	
	@Id
	private String id;
	
	private String inReplyId;
	private String text;
	private long publicationTime;
	
	private String reference;
	private int reposts = 0;
	
	private boolean original = true;
	
	private List<String> urls = new ArrayList<String>();
	private Map<String, String> mediaItems = new HashMap<String, String>();
	private List<String> hashtags = new ArrayList<String>();
	private String username;
	
	private List<NamedEntity> entities = new ArrayList<NamedEntity>();
	private List<String> properNouns = new ArrayList<String>();
	private String posTags;

	private String cleanText;
	private Boolean accepted = true;
	
	public Item() {
		
	}
	
	public Item(String id, String text, long publicationTime) {
		this.id = id;
		this.text = text;
		this.publicationTime = publicationTime;
	}

	public Item(String id, String text, long publicationTime, List<String> urls) {
		this(id, text, publicationTime);
		this.urls.addAll(urls);
	}
	
	public Item(DBObject obj) throws ParseException {
		SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzzz yyyy");      

		Object id = obj.get("id");
		String text = (String) obj.get("text");
		String created_at = (String) obj.get("created_at");
		DBObject entities = (DBObject) obj.get("entities");
		DBObject user = (DBObject) obj.get("user");
		
		this.inReplyId = (String) obj.get("in_reply_to_status_id_str");
		
		DBObject rtObj = (DBObject) obj.get("retweeted_status");
		if(rtObj != null) {
			this.original = false;
			this.reference = rtObj.get("id").toString();
		}
		
		
		Date date = sdf.parse(created_at);
		
		this.id = id.toString();
		this.text = text;
		this.publicationTime = date.getTime();
		
		if(user != null) {
			this.username = (String) user.get("screen_name");
		}
		
		if(entities != null) {
			BasicDBList urlEntities = (BasicDBList) entities.get("urls");
			if(urlEntities != null) {
				for(int i=0; i<urlEntities.size(); i++) {
					DBObject urlEntity = (DBObject) urlEntities.get(i);
					if(urlEntity != null) {
						String url = (String) urlEntity.get("url");
						urls.add(url);
					}
				}
			}
			
			BasicDBList hashtagEntities = (BasicDBList) entities.get("hashtags");
			if(hashtagEntities != null) {
				for(int i=0; i<hashtagEntities.size(); i++) {
					DBObject hashtagEntity = (DBObject) hashtagEntities.get(i);
					if(hashtagEntity != null) {
						String hashtag = (String) hashtagEntity.get("text");
						hashtags.add(hashtag);
					}
				}
			}
			
			BasicDBList mediaEntities = (BasicDBList) entities.get("media");
			if(mediaEntities != null) {
				for(int i=0; i<mediaEntities.size(); i++) {
					DBObject mediaEntity = (DBObject) mediaEntities.get(i);
					if(mediaEntity != null) {
						Long mediaId = (Long) mediaEntity.get("id");
						String mediaUrl = (String) mediaEntity.get("media_url");
						mediaItems.put(mediaId.toString(), mediaUrl);
					}
				}
			}
		}
	}
	
	public Item(DBObject obj, String type) throws ParseException {

		String id = (String) obj.get("id");
		String text = (String) obj.get("title");
		this.publicationTime = (Long) obj.get("publicationTime");

		DBObject user = (DBObject) obj.get("user");
		
		this.id = id.split("#")[1];
		this.text = text;
		
		if(user != null) {
			this.username = (String) user.get("screen_name");
		}
		
		BasicDBList links = (BasicDBList) obj.get("links");
		if(links != null) {
			for(int i=0; i<links.size(); i++) {
				String url = (String) links.get(i);
				if(url != null) {
					urls.add(url);
				}
			}
		}
			
		BasicDBList hashtagEntities = (BasicDBList) obj.get("tags");
		if(hashtagEntities != null) {
			for(int i=0; i<hashtagEntities.size(); i++) {
				String hashtag = (String) hashtagEntities.get(i);
				if(hashtag != null) {
					hashtags.add(hashtag);
				}
			}
		}
			
		BasicDBList mediaEntities = (BasicDBList) obj.get("mediaIds");
		if(mediaEntities != null) {
			for(int i=0; i<mediaEntities.size(); i++) {
				String mediaId = (String) mediaEntities.get(i);
				if(mediaId != null) {
					mediaItems.put(mediaId.split("#")[1], "");
				}
			}
		}
	}
	
	public String getId() {
		return id;
	}
	
	public void setId(String id) {
		this.id = id;
	}
	
	public String getText() {
		return text;
	}
	
	public void setText(String text) {
		this.text = text;
	}
	
	public String getCleanText() {
		return cleanText;
	}
	
	public void setCleanText(String cleanText) {
		this.cleanText = cleanText;
	}
	
	public long getPublicationTime() {
		return publicationTime;
	}
	
	public void setPublicationTime(long publicationTime) {
		this.publicationTime = publicationTime;
	}
	
	public List<String> getUrls() {
		return urls;
	}
	
	public void setUrls(List<String> urls) {
		this.urls.addAll(urls);
	}

	public Map<String, String> getMediaItems() {
		return mediaItems;
	}
	
	public void setMediaUrls(Map<String, String> mediaUrls) {
		this.mediaItems.putAll(mediaUrls);
	}
	
	public List<String> getHashtags() {
		return hashtags;
	}
	
	public void setHashtags(List<String> hashtags) {
		this.hashtags.addAll(hashtags);
	}
	
	public void setUsername(String username) {
		this.username = username;
	}
	
	public String getUsername() {
		return username;
	}
	
	public void setInReplyId(String inReplyId) {
		this.inReplyId = inReplyId;
	}
	
	public String getInReplyId() {
		return inReplyId;
	}
	
	public void setReference(String reference) {
		this.reference = reference;
	}
	
	public String getReference() {
		return reference;
	}
	
	public Collection<NamedEntity> getNamedEntities() {
		return this.entities;
	}
	
	public void addNamedEntities(Collection<NamedEntity> entities) {
		this.entities.addAll(entities);
	}
	
	public Collection<String> getProperNouns() {
		return this.properNouns;
	}
	
	public void addProperNouns(Collection<String> properNouns) {
		this.properNouns.addAll(properNouns);
	}
	
	public void setPosTags(String posTags) {
		this.posTags = posTags;
	}
	
	public String getPosTags() {
		return this.posTags;
	}
	
	public void setReposts(int reposts) {
		this.reposts = reposts;
	}
	
	public int getReposts() {
		return reposts;
	}
	
	public boolean isOriginal() {
		return this.original;
	}
	
	public boolean isAccepted() {
		return accepted;
	}
	
	public void setAccepted(boolean accepted) {
		this.accepted = accepted;
	}
}
