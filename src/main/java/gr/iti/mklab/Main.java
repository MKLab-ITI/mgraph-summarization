package gr.iti.mklab;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import com.aliasi.util.Arrays;

import cc.mallet.types.InstanceList;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedGraph;
import gr.iti.mklab.analysis.ItemFilter;
import gr.iti.mklab.dao.MorphiaDAO;
import gr.iti.mklab.models.ExtendedTimeline;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.Pair;
import gr.iti.mklab.models.Topic;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.models.WeightedEdge;
import gr.iti.mklab.structures.TimeTopicGrid;
import gr.iti.mklab.summarization.ttgrid.TimeTopicSummarizer;
import gr.iti.mklab.topicmodels.LDA;
import gr.iti.mklab.topicmodels.SCAN;
import gr.iti.mklab.topicmodels.TopicDetector;
import gr.iti.mklab.utils.GraphUtils;
import gr.iti.mklab.vocabulary.Vocabulary;

public class Main {

	private static String hostname = "160.40.50.207";
	private static String dbname = "Sundance2013";
	
	static Integer[] periods = {1, 3, 7, 8, 9};
	
	public static void main(String[] args) throws Exception {
		
		// Load and Filter Graph
		Graph<String, WeightedEdge> graph = GraphUtils.loadGraph("./items.graphml");
		//Graph<String, WeightedEdge> graph = GraphUtils.generateGraph(vectors, 0.3);
		//GraphUtils.saveGraph(graph, "./full_items.graphml");
		//graph = GraphUtils.filter(graph, 0.5);
		//GraphUtils.saveGraph(graph, "./items.graphml");
		
		System.out.println("Graph Loaded");
		System.out.println(graph.getVertexCount() + " vertices");
		System.out.println(graph.getEdgeCount() + " edges");
		
		MorphiaDAO<Item> dao = new MorphiaDAO<Item>(hostname ,dbname, Item.class);
		System.out.println(dao.count() + " items");
		
		// Aggressive Filtering 
		ItemFilter filter = new ItemFilter();
		List<Item> items = filter.filter(dao.iterator());
		System.out.println(items.size() + " items after filtering");
		
		Map<String, Item> itemsMap = new HashMap<String, Item>();
		Map<String, Integer> popularities = new HashMap<String, Integer>();
		for(Item item : items) {
			popularities.put(item.getId(), item.getReposts());
			itemsMap.put(item.getId(), item);
		}
		
		Map<String, Vector> vectors = Vocabulary.createVocabulary(items);
		
		//Collection<String> boostingTerms = SundanceDataset.getBoostingTerms();
		//IOUtils.writeLines(boostingTerms, "\n", new FileOutputStream("boostingTerms.txt"));
		Collection<String> boostedTerms = IOUtils.readLines(new FileInputStream("boostingTerms.txt"));
		
		Vocabulary.addBoostedTerms(boostedTerms);
		System.out.println(boostedTerms.size() + " boosted terms");
		
		//TopicModel scan = new SCAN(graph);
		//scan.run(vectors, itemsMap);
		//scan.saveModel("./topics/scan_model.bin");
		TopicDetector scan = SCAN.loadModel("./topics/scan_model.bin");
				
		List<Topic> topics = scan.getTopics();
		Map<Integer, Collection<String>> associations = scan.getTopicAssociations();
		System.out.println("#topics: " + topics.size() + ", #associations: " + associations.size());
		
		int totalAssociations = 0;
		for(Collection<String> l : associations.values())
			totalAssociations += l.size();
		
		System.out.println("Total Associations: " + totalAssociations);
		
		// Create Time-Topic Grid with timeslots of 60 minutes
		TimeTopicGrid ttGrid = new TimeTopicGrid(60, TimeUnit.MINUTES);
		ttGrid.addTopics(topics, associations, vectors, itemsMap);
		
		Graph<String, WeightedEdge> normalizedGraph = GraphUtils.toDirected((UndirectedGraph<String, WeightedEdge>) graph, itemsMap);
		normalizedGraph = GraphUtils.normalize(normalizedGraph);
		
		System.out.println("Total Vertices: " + normalizedGraph.getVertexCount());
		System.out.println("Total Edges: " + normalizedGraph.getEdgeCount());
		
		Collection<Integer> activeTopics = ttGrid.getActiveTopics();
		System.out.println("Active Topics: " + activeTopics.size() + " / " + ttGrid.getTopics().size());
		
		TimeTopicSummarizer ttSummarizer = new TimeTopicSummarizer(ttGrid, normalizedGraph, popularities);
		System.out.println("Summarizer Initialized");
		
		//PopularitySummarizer popSummarizer = new PopularitySummarizer(popularities);
		//DivRankSummarizer dvrSummarizer = new DivRankSummarizer(normalizedGraph, popularities);
		//CentroidSummarizer cntSummarizer = new CentroidSummarizer();
		//TopicSummarizer topicSummarizer = new TopicSummarizer(topics);
		
		//RandomSummarizer rdmSummarizer = new RandomSummarizer();
		
		ExtendedTimeline totalTimeline = ExtendedTimeline.createTimeline(60, TimeUnit.MINUTES, vectors, items);
		List<Pair<Long, Long>> peakWindows = totalTimeline.detectPeakWindows();
		System.out.println(peakWindows.size() + " peaks detected");
		
		String resultsFolder = "./results-sensitivity/";
		
		if(!new File(resultsFolder).exists())
			new File(resultsFolder).mkdirs();
		
		int target = 100;
		for(Pair<Long, Long> window : peakWindows) {
	
			Integer w = peakWindows.indexOf(window) + 1;
			
			if(!Arrays.member(w, periods)) {
				continue;
			}
			
			List<String> itemIds = totalTimeline.getItems(window);			
			double compresion = ((double) target) / itemIds.size();
			
			System.out.println("===========================================================");
			

			System.out.println("Window: " + w);
			System.out.println("CR = " + compresion);
			
			List<String> summaries = new ArrayList<String>();
			for(double W=0; W<=1; W=W+0.2) {
				Set<String> ttSummary = ttSummarizer.summarizeTest(vectors, compresion, window, W, 0.2, 0);
				summaries.add(StringUtils.join(ttSummary, ","));
			}
			
			for(double W=0; W<=1; W=W+0.2) {
				Set<String> ttSummary = ttSummarizer.summarizeTest(vectors, compresion, window, 0.8, W, 0);
				summaries.add(StringUtils.join(ttSummary, ","));
			}
			
			for(double W=0; W<=1; W=W+0.2) {
				Set<String> ttSummary = ttSummarizer.summarizeTest(vectors, compresion, window, 0.8, 0.2, W);
				summaries.add(StringUtils.join(ttSummary, ","));
			}
			IOUtils.writeLines(summaries, "\n", new FileOutputStream(resultsFolder+"TT-sensitivity" + w + ".csv"));

			/*
			Set<String> ttSummary = ttSummarizer.summarize(vectors, compresion, window);
			System.out.println("|S_tt| = " + ttSummary.size());
			
			Set<String> popSummary = popSummarizer.summarize(itemIds, vectors, target);
			System.out.println("|S_pop| = " + popSummary.size());
			
			Set<String> dvrSummary = dvrSummarizer.summarize(itemIds, vectors, target);
			System.out.println("|S_dvr| = " + dvrSummary.size());
			
			Set<String> cntSummary = cntSummarizer.summarize(itemIds, vectors, target);
			System.out.println("|S_cnt| = " + cntSummary.size());
			*/
			
			//Set<String> tpSummary = topicSummarizer.summarize(vectors, target, window);
			//System.out.println("|S_tp| = " + tpSummary.size());
			
			//Set<String> rdmSummary = rdmSummarizer.summarize(itemIds, vectors, target);
			//System.out.println("|S_rdm| = " + rdmSummary.size());
			
			
			//IOUtils.write(StringUtils.join(ttSummary, ","), new FileOutputStream(resultsFolder+"timetopic_W" + w + ".csv"));
			//IOUtils.write(StringUtils.join(popSummary, ","), new FileOutputStream(resultsFolder+"popularity_W" + w + ".csv"));
			//IOUtils.write(StringUtils.join(dvrSummary, ","), new FileOutputStream(resultsFolder+"divrank_W" + w + ".csv"));
			//IOUtils.write(StringUtils.join(cntSummary, ","), new FileOutputStream(resultsFolder+"centroid_W" + w + ".csv"));
			//IOUtils.write(StringUtils.join(tpSummary, ","), new FileOutputStream(resultsFolder+"topic_W" + w + ".csv"));
			//IOUtils.write(StringUtils.join(rdmSummary, ","), new FileOutputStream(resultsFolder+"random_W" + w + ".csv"));
			
			/*
			Set<String> intersection = new HashSet<String>(popSummary);
			intersection.retainAll(dvrSummary);
			System.out.println("|S_intersection(pop, dvr)| = " + intersection.size());
			
			intersection.clear();
			intersection = new HashSet<String>(ttSummary);
			intersection.retainAll(dvrSummary);
			System.out.println("|S_intersection(tt, dvr)| = " + intersection.size());
			
			intersection.clear();
			intersection = new HashSet<String>(ttSummary);
			intersection.retainAll(popSummary);
			System.out.println("|S_intersection(tt, pop)| = " + intersection.size());
			*/
		}
	}
	
	public static TopicDetector ldaModeling(List<Item> items) throws IOException {
		Collection<String> stowords = Vocabulary.getStopwords();
		
		InstanceList instances = LDA.getInstances(items, stowords);
		InstanceList timePooledInstances = LDA.getInstancesWithTimePooling(items, 10, TimeUnit.MINUTES, stowords);
		//InstanceList authorPooledInstances = LDA.getInstancesWithAuthorPooling(items, stowords);
				
		System.out.println(instances.size() + " instances");
		System.out.println(timePooledInstances.size() + " time pooled instances");
				
		int K = (int) Math.sqrt(instances.size());
		LDA lda = new LDA(K);
		lda.train(timePooledInstances);
		
		return lda;
	}
	
	public static void test(TimeTopicGrid ttGrid) {
		
		ExtendedTimeline totalTimeline = ttGrid.getTotalTimeline();
		List<Pair<Long, Long>> peakWindows = totalTimeline.getPeakWindows();
		System.out.println(peakWindows.size() + " peaks in total timeline");
		
		int maxFrequncy = 0;
		Pair<Long, Long> maxWindow = null;
		for(Pair<Long, Long> window : peakWindows) {
			Integer frequency = totalTimeline.getFrequency(window);
			if(maxFrequncy < frequency) {
				maxFrequncy = frequency;
				maxWindow = window;
			}
		}
		
		System.out.println(new Date(maxWindow.left) + " - " + new Date(maxWindow.right));
		System.out.println("Max Frequncy: " + maxFrequncy);
		
		List<String> messages = totalTimeline.getItems(maxWindow);
		System.out.println("Max Peak #msgs: " + messages.size());
		
		int L = (int) (0.01 * maxFrequncy);
		System.out.println("Summary length: " + L);
		
		System.out.println("=======================================");
		List<Long> activeBins = totalTimeline.getActiveBins();
		int totalItem = 0;
		for(Long ab : activeBins) {
			totalItem += totalTimeline.getFrequency(ab);
		}
		
		List<String> windowItems = totalTimeline.getItems(maxWindow);
		
		System.out.println("* Active Bins = " + activeBins.size());
		System.out.println("* Items in Active Bins = " + totalItem);
		System.out.println("* |Peaks| = " + totalTimeline.getPeakWindows().size());
		System.out.println("* |T| = " + totalTimeline.getTotal());
		System.out.println("* |length| = " + totalTimeline.size());
		System.out.println("* |Ti| = " + windowItems.size());
	}
}
