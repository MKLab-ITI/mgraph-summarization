package gr.iti.mklab;

public class SocialSensorMain {
	
	public static void main(String[] args)  {
		
		//DBCollection mediaCollection = MongoDAO.getCollection(hostname, dbname, "FestMediaItems");
		
		/*
		DBCollection usersCollection = MongoDAO.getCollection(hostname, dbname, "StreamUsers");
		
		Map<String, Pair<Vector, Long>> vectorsMap = MongoDAO.loadSocialSensorVectors(hostname, dbname, collName, true);
		
		Map<String, Vector> vectors = new HashMap<String, Vector>(); 
		for(Entry<String, Pair<Vector, Long>> e : vectorsMap.entrySet()) {
			
			DBObject user = usersCollection.findOne(new BasicDBObject());
	        if(user == null)
	            continue;
	        
			vectors.put(e.getKey(), e.getValue().left);
		}
		
		Map<String, Item> itemsMap = MongoDAO.loadSocialSensorItemsMap(hostname, dbname, collName, vectors.keySet());
		
		ItemFilter filter = new ItemFilter();
		List<Item> items = filter.filter(itemsMap.values());
		System.out.println(items.size() + " items after filtering");

		vectors = Vocabulary.createVocabulary(items, 1);
		
		SCAN scan = new SCAN(0.1);
		scan.run(vectors, itemsMap);

		List<Topic> topics = scan.getTopics();
		Map<Integer, List<String>> associations = scan.getTopicAssociations();
		System.out.println("#topics: " + topics.size() + ", #associations: " + associations.size());
		
		Graph<String, WeightedEdge> graph = scan.getGraph();
		
		int totalAssociations = 0;
		for(List<String> l : associations.values())
			totalAssociations += l.size();
		
		System.out.println("Total Associations: " + totalAssociations);
		
		TimeTopicGrid ttGrid = new TimeTopicGrid(60, TimeUnit.MINUTES);
		ttGrid.addTopics(topics, associations, vectors, itemsMap);
		
		Graph<String, WeightedEdge> normalizedGraph = GraphUtils.toDirected((UndirectedGraph<String, WeightedEdge>) graph, itemsMap);
		normalizedGraph = GraphUtils.normalize(normalizedGraph);
		
		System.out.println("Total Vertices: " + normalizedGraph.getVertexCount());
		System.out.println("Total Edges: " + normalizedGraph.getEdgeCount());
		
		Collection<Integer> activeTopics = ttGrid.getActiveTopics();
		System.out.println("Active Topics: " + activeTopics.size() + " / " + ttGrid.getTopics().size());
		
		Map<String, Integer> popularities = MongoDAO.loadSocialSensorPopularity(hostname, dbname, collName);
		
		TimeTopicSummarizer ttSummarizer = new TimeTopicSummarizer(ttGrid, normalizedGraph, popularities);
		System.out.println("Summarizer Initialized");
		
		int target = 100;
		ExtendedTimeline totalTimeline = ExtendedTimeline.createTimeline(60, TimeUnit.MINUTES, vectors, items);
		
		Pair<Long, Long> window = Pair.of(totalTimeline.getMinTime(), totalTimeline.getMaxTime());	
		List<String> itemIds = totalTimeline.getItems(window);
		
		double compresion = ((double) target) / itemIds.size();
		
		System.out.println("===========================================================");
		

		System.out.println("Window: " + new Date(window.left) + " - " + new Date(window.right));
		System.out.println("CR = " + compresion);
		
		Set<String> ttSummary = ttSummarizer.summarize(vectors, compresion, window);
		System.out.println("|S_tt| = " + ttSummary.size());
		
		PopularitySummarizer popSummarizer = new PopularitySummarizer(popularities);
		Set<String> popSummary = popSummarizer.summarize(itemIds, vectors, target);
		System.out.println("|S_pop| = " + popSummary.size());
		
		DivRankSummarizer dvrSummarizer = new DivRankSummarizer(normalizedGraph, popularities);
		Set<String> dvrSummary = dvrSummarizer.summarize(itemIds, vectors, target);
		System.out.println("|S_dvr| = " + dvrSummary.size());
		
		TopicSummarizer topicSummarizer = new TopicSummarizer(topics);
		Set<String> tpSummary = topicSummarizer.summarize(vectors, target, window);
		System.out.println("|S_tp| = " + tpSummary.size());
		
		String resultsFolder = "/home/manosetro/Infotainment/";
		IOUtils.write(StringUtils.join(ttSummary, ","), new FileOutputStream(resultsFolder+"timetopic.csv"));
		IOUtils.write(StringUtils.join(popSummary, ","), new FileOutputStream(resultsFolder+"popularity.csv"));
		IOUtils.write(StringUtils.join(dvrSummary, ","), new FileOutputStream(resultsFolder+"divrank.csv"));
		IOUtils.write(StringUtils.join(tpSummary, ","), new FileOutputStream(resultsFolder+"topic.csv"));
		*/
		
	}

	

}
