package gr.iti.mklab.extractors;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.lang.StringEscapeUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.NamedEntity;
import twitter4j.Status;
import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreLabel;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.SparseMultigraph;

public class EntitiesExtractor {

	AbstractSequenceClassifier<CoreLabel> classifier;
	
	public EntitiesExtractor(String serializedClassifier) {
		classifier = CRFClassifier.getClassifierNoExceptions(serializedClassifier);
	}
	
	public Map<String, NamedEntity> extractEntities(List<Status> statuses) throws Exception {
		Map<String, NamedEntity> entities = new HashMap<String, NamedEntity>();
		for(Status status : statuses) {
			String text = status.getText();
			extractEntities(text, entities);
		}
		return entities;
	}
	
	public Map<String, NamedEntity> extractEntitiesFromItems(List<Item> items) {
		Map<String, NamedEntity> entities = new HashMap<String, NamedEntity>();
		for(Item item : items) {
			String text = item.getText();
			try {
				extractEntities(text, entities);
			} catch (Exception e) {
				//e.printStackTrace();
			}
		}
		return entities;
	}
	
	public Collection<NamedEntity> extractEntities(String text) throws Exception {
		List<NamedEntity> entities = new ArrayList<NamedEntity>();
		Map<String, NamedEntity> entitiesMap = new HashMap<String, NamedEntity>();
		
		extractEntities(text, entitiesMap);
		for(NamedEntity entity : entitiesMap.values()) {
			entities.add(entity);
		}
		return entities;
	}
	
	public void extractEntities(String text, Map<String, NamedEntity> entities) throws Exception {
		text = StringEscapeUtils.unescapeXml(text);
		String itemXML = classifier.classifyWithInlineXML(text);
		
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder docb = dbf.newDocumentBuilder();
        
        byte[] content = ("<DOC>" + itemXML + "</DOC>").getBytes();
		ByteArrayInputStream bis = new ByteArrayInputStream(content);
		Document doc = docb.parse(bis);
		
		//3-class model
		extractEntities(entities, doc, "PERSON");
		extractEntities(entities, doc, "LOCATION");
		extractEntities(entities, doc, "ORGANIZATION");
		
		//4-class model
		extractEntities(entities, doc, "MISC");
				
		//7-class model
		extractEntities(entities, doc, "TIME");
		extractEntities(entities, doc, "DATE");
		extractEntities(entities, doc, "MONEY");
		extractEntities(entities, doc, "PERCENT");
	}
	
	private void extractEntities(Map<String, NamedEntity> entities, Document doc, String type) {
		NodeList nodeList = doc.getElementsByTagName(type);

        for (int k = 0; k < nodeList.getLength(); k++) {
            String name = nodeList.item(k).getTextContent().toLowerCase();
            if(name == null)
            	continue;
            
            name = name.replaceAll("[^A-Za-z0-9 ]", "");
            name = name.replaceAll("\\s+", " ");
            name = name.trim();
            
            String key = type + "#" + name;
            
            if (!entities.containsKey(key)) {
            	NamedEntity e = new NamedEntity(name, type);
            	entities.put(key, e);
            }
            else {
            	NamedEntity e = entities.get(key);
            	e.incFrequency();
            	entities.put(key, e);
            }
        }
	}
	
	public Map<Long, List<NamedEntity>> getEntitiesPerStatus(List<Status> statuses) {
		Map<Long, List<NamedEntity>> entitiesPerStatus = new HashMap<Long, List<NamedEntity>>();
		for(Status status : statuses) {
			String text = status.getText();
			Map<String, NamedEntity> entities = new HashMap<String, NamedEntity>();
			try {
				extractEntities(text, entities);
			} catch (Exception e) { }
			entitiesPerStatus.put(status.getId(), new ArrayList<NamedEntity>(entities.values()));
		}
		
		return entitiesPerStatus;
	}
	
	public static Graph<NamedEntity, Edge> getEntitiesGraph(Map<Long, List<NamedEntity>> entitiesPerStatus) {
		Graph<NamedEntity, Edge> graph = new SparseMultigraph<NamedEntity, Edge>();
		for(Entry<Long, List<NamedEntity>> entry : entitiesPerStatus.entrySet()) {
			List<NamedEntity> eColl = entry.getValue();
			for(int i=0; i<eColl.size(); i++) {
				for(int j=i+1; j<eColl.size(); j++) {
					NamedEntity e1 = eColl.get(i);
					NamedEntity e2 = eColl.get(j);
					graph.addVertex(e1);
					graph.addVertex(e2);
					
					Edge edge = graph.findEdge(e1, e2);
					if(edge == null)  {
						edge = new Edge();
						graph.addEdge(edge, e1, e2); 
					}
					else {
						edge.incFrequency();
					}
				}
			}
		}
		return graph;
	}
	
	public static class Edge {
		private Integer freq = 1;
		
		public void incFrequency() {
			freq++;
		}
		
		public Integer getFreaquency() {
			return freq;
		}
		
		public String toString() {
			return freq.toString();
		}
	}
	
	public static void main(String[] args) {
		
		//String serializedClassifier3Class = "./stanford-ner/classifiers/english.all.3class.distsim.crf.ser.gz";
		//String serializedClassifier4Class = "./stanford-ner/classifiers/english.conll.4class.distsim.crf.ser.gz";
		//String serializedClassifier7Class = "./stanford-ner/classifiers/english.muc.7class.distsim.crf.ser.gz";
		
		//EntitiesExtractor entitiesExtractor = new EntitiesExtractor(serializedClassifier3Class);
		//List<Status> statuses = MongoDAO.loadUniqueStatuses("160.40.50.207", "Sundance2013", "Tweets");
		
		//Map<Long, List<Entity>> entitiesPerStatus = entitiesExtractor.getEntitiesPerStatus(statuses);
		//MongoDAO.saveEntitiesPerStatus("160.40.50.207","Sundance2013","EntitiesPerStatus", entitiesPerStatus);

		//Map<Long, List<NamedEntity>> entitiesPerStatus = MongoDAO.loadEntitiesPerStatus("160.40.50.207","Sundance2013","EntitiesPerStatus");
		//Graph<NamedEntity, Edge> entitiesGraph = getEntitiesGraph(entitiesPerStatus);
		
		/*
		Map<String, Entity> entitiesMap = new HashMap<String, Entity>();
		for(Status status : statuses) {
			String text = status.getText();
			try {
				entitiesExtractor.extractEntities(text, entitiesMap);
			} catch (Exception e) {	}
		}
		
		Collection<Entity> entities = entitiesMap.values();
		
		MongoDAO.saveEntities("160.40.50.207","super_tuesday","Entities7class", entities);
		*/
		
	}

}
