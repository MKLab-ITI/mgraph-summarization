package gr.iti.mklab.utils;

import gr.iti.mklab.models.ClusterVector;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.Vector;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import com.twitter.Extractor;


public class IOUtil {

	public static void saveClusters(Collection<Collection<String>> clusters, String filename) throws IOException {
		List<String> lines = new ArrayList<String>();
		for(Collection<String> cluster : clusters) {
			String line = StringUtils.join(cluster, "\t");
			lines.add(line);
		}
		
		Writer writer = new FileWriter(filename);
		IOUtils.writeLines(lines, "\n", writer);
		writer.close();
	}

	public static Map<String, String[]> readResults(File file) throws IOException {
		Map<String, String[]> results = new HashMap<String, String[]>();
		List<String> lines = IOUtils.readLines(new FileInputStream(file));
		
		for(int i=0; i<lines.size()-1; i=i+2) {
			String key = lines.get(i).replaceAll("[%$]", "");
			String[] v = lines.get(i+1).split("\t");
			
			String[] value = new String[v.length];
			for(int j=0; j<v.length; j++) {
				value[j] = v[j];
			}
			results.put(key, value);
		}
		
		return results;
	}
	
	public static Map<String, Item> loadDataset(String file) throws IOException {
		FileReader fr = new FileReader(file);
		BufferedReader br = new BufferedReader(fr);
		
		Extractor extractor = new Extractor();
		SimpleDateFormat dateFormatter = new SimpleDateFormat("KK:mm a - d MMM yyyy");
		
		Map<String, Item> itemsMap = new HashMap<String, Item>();
		Map<String, Integer> duplicates = new HashMap<String, Integer>();
		
		String line;
		while ((line = br.readLine()) != null) {
			String[] parts = line.split("\t");
			if(parts.length != 13)
				continue;
			
			String status = parts[0];
			Boolean suspended = Boolean.parseBoolean(parts[1]);
			if(status.equals("200") && !suspended) {
				String id = parts[6];
						
				if(itemsMap.containsKey(id)) {
					Integer f = duplicates.get(id);
					if(f == null)
						f = 0;
					
					duplicates.put(id, ++f);
				}
				else {
					Item item = new Item();
					
					item.setId(id);
					item.setUsername(parts[7]);
					
					String text = parts[8];
					item.setText(text);
					
					List<String> urls = extractor.extractURLs(text);
					item.setUrls(urls);
					
					List<String> hashtags = extractor.extractHashtags(text);
					item.setHashtags(hashtags);
					
					item.setReposts(Integer.parseInt(parts[10]));
					try {
						Date date = dateFormatter.parse(parts[9]);
						item.setPublicationTime(date.getTime());
					} catch (ParseException e) {
						System.out.println("Cannot parse date " + parts[9]+ " for tweet " + id);
						continue;
					}
					
					if(!parts[12].equals("null"))
						System.out.println(parts[12]);

					itemsMap.put(id, item);
				}
			}
		}
		br.close();	
		
		for(String id : itemsMap.keySet()) {
			if(duplicates.containsKey(id)) {
				Item item = itemsMap.get(id);
				item.setReposts(Math.max(duplicates.get(id) , item.getReposts()));
			}
		}
		
		return itemsMap;
	}
	
	public static Map<String, Set<String>> loadClusters(String file, Set<String> ids) throws FileNotFoundException, IOException {
		Map<String, Set<String>> clusters = new HashMap<String, Set<String>>();
		
		List<String> lines = IOUtils.readLines(new FileInputStream(file));
		for(String line : lines)  {
			String[] parts = line.split("\t");
			
			if(ids != null && !ids.contains(parts[1]))
				continue;
			
			Set<String> itemIds = clusters.get(parts[0]);
			if(itemIds == null) {
				itemIds = new HashSet<String>();
				clusters.put(parts[0], itemIds);
			}
			
			itemIds.add(parts[1]);
		}
		
		Set<String> tbr = new HashSet<String>();
		for(Entry<String, Set<String>> entry : clusters.entrySet()) {
			if(entry.getValue().isEmpty()) {
				tbr.add(entry.getKey());
			}
		}
		for(String id : tbr) {
			clusters.remove(id);
		}
		
		return clusters;
	}
	
	public static List<Set<String>> loadClusters(String file) throws IOException {
		List<Set<String>> clusters = new ArrayList<Set<String>>();
		List<String> lines = IOUtils.readLines(new FileInputStream(file));
		for(String line : lines) {
			String[] parts = line.split("\t");
			Set<String> cluster = new HashSet<String>();
			for(String part : parts) {
				if(part.contains("-")) {
					String[] subparts = part.split("-");
					List<String> list = Arrays.asList(subparts);
					Collections.sort(list);
					
					cluster.add(StringUtils.join(list, "-"));
				}
				else {
					cluster.add(part);
				}
				
			}
			clusters.add(cluster);
		}
		return clusters;
	}
	
	public static Map<String, ClusterVector> loadClusters(String file, Map<String, Item> itemsMap, Map<String, Vector> vectorsMap) 
			throws IOException {
		List<Set<String>> clusters = loadClusters(file);
		
		Map<String, ClusterVector> CVs = new HashMap<String, ClusterVector>();
		for(Set<String> cluster : clusters) {
			Integer index = clusters.indexOf(cluster);
			ClusterVector cv = new ClusterVector();
			for(String clusterMember : cluster) {
				Vector vector = vectorsMap.get(clusterMember);
				Item item = itemsMap.get(clusterMember);
				if(item!= null && vector != null) {
					cv.addVector(item.getId(), vector, item.getPublicationTime());
				}
				else {
					if(item == null) {
						System.out.println("Item " + clusterMember + " is not exists");
					}
					if(vector == null) {
						System.out.println("Vector " + clusterMember + " is not exists");
					}
				}
			}
			if(cv.getNumOfVectors() > 0) {
				CVs.put(index.toString(), cv);
			}
		}
		
		return CVs;
	}
	
}
