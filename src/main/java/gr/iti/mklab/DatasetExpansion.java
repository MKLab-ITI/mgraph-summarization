package gr.iti.mklab;

import gr.iti.mklab.analysis.ItemFilter;
import gr.iti.mklab.dao.FileDAO;
import gr.iti.mklab.dao.MorphiaDAO;
import gr.iti.mklab.extractors.LanguageDetector;
import gr.iti.mklab.models.Item;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.mongodb.morphia.query.Query;

public class DatasetExpansion {

	private static String langProfiles = "/disk1_data/workspace/social-media-summarization/lang-profiles";
	
	private static String dataset = "WWDC14";
	
	public static void main(String[] args) throws Exception {
	
		MorphiaDAO<Item> dao = new MorphiaDAO<Item>("160.40.50.207" , dataset, Item.class);

		//getMissingIds("/disk1_data/Datasets/" + dataset + "/additional_ids_3.txt", dao);
		insertNewItems("/disk1_data/Datasets/" + dataset + "/additional_items_3.txt", dao);
	}

	public static void insertNewItems(String itemsFilename, MorphiaDAO<Item> dao) throws Exception {
		Map<String, Item> itemsMap = FileDAO.getItems(itemsFilename);
		System.out.println(itemsMap.size() + " items!");
		
		LanguageDetector languageDetector = new LanguageDetector(langProfiles);
		ItemFilter filter = new ItemFilter();
		for(Item item : itemsMap.values()) {
			if(filter.accept(item)) {
				String language = languageDetector.detect(item);
				if(language != null && language.equals("en")) {
					Query<Item> q = dao.getQuery().filter("_id", item.getId());
					if(dao.count(q) > 0) {
						System.out.println("Already exists: " + item.getId());
						//Map<String, String> mediaItems = item.getMediaItems();
						//if(mediaItems != null && !mediaItems.isEmpty()) {
							//UpdateOperations<Item> ops = dao.getUpdateOperations();
							//ops.set("mediaItems", mediaItems);
							//dao.update(q, ops);
						//}
					}
					else {
						System.out.println("Save " + item.getId());
						dao.save(item);
					}
				}
			}
			else {
				System.out.println("Is not accepted: " + item.getText());
			}
		}
	}
	
	public static void getMissingIds(String outputfile, MorphiaDAO<Item> dao) throws IOException {

		Map<String, Item> itemsMap = new HashMap<String, Item>();
		Iterator<Item> it = dao.iterator();
		while(it.hasNext()) {
			Item item = it.next();
			itemsMap.put(item.getId(), item);
		}
		
		Set<String> references = new HashSet<String>();
		Set<String> replies = new HashSet<String>();
		for(Item item : itemsMap.values()) {
			String reference = item.getReference();
			if(reference != null) {
				references.add(reference);
			}
			
			String inReply = item.getInReplyId();
			if(inReply != null) {
				replies.add(inReply);
			}
		}

		System.out.println(itemsMap.size() + " items");
		
		System.out.println(references.size() + " references");
		System.out.println(replies.size() + " replies");
		
		Set<String> ids = itemsMap.keySet();
		
		references.removeAll(ids);
		replies.removeAll(ids);

		System.out.println(references.size() + " references not exist");
		System.out.println(replies.size() + " replies not exist");
		
		Set<String> missingIds = new HashSet<String>();
		missingIds.addAll(references);
		missingIds.addAll(replies);
		
		System.out.println(missingIds.size() + " missing ids");
		
		Writer output = new FileWriter(outputfile);
		IOUtils.writeLines(missingIds, "\n", output);
		output.close();
	}
	
}
