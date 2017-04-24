package gr.iti.mklab.topicmodels.TwitterLDA;

import gr.iti.mklab.analysis.TextAnalyser;
import gr.iti.mklab.dao.MorphiaDAO;
import gr.iti.mklab.index.TextIndex;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.Topic;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.topicmodels.TopicDetector;
import gr.iti.mklab.topicmodels.TwitterLDA.TwitterLDA.User.Post;
import gr.iti.mklab.utils.CollectionsUtils;
import gr.iti.mklab.utils.IOUtil;
import gr.iti.mklab.utils.ItemsUtils;
import gr.iti.mklab.utils.Stopwords;
import gr.iti.mklab.vocabulary.Vocabulary;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.mongodb.morphia.query.Query;

public class TwitterLDA extends TopicDetector {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1107637312771396524L;

	private int T; 				// number of topics
	
	private int numOfUsers; 	// user number
	private int V; 				// vocabulary size
	private int nIter; 			// iteration number
	private int wordCnt = 50; 	// words per topic
	
	private float[] alphaGeneral;
	private float alphaGeneralSum = 0;
	private float[] betaWord;
	private float betaWordSum = 0;
	private float[] betaBackground;
	private float betaBackgroundSum = 0;
	private float[] gamma;

	private float[][] thetaGeneral;
	private float[][] phiWord;
	private float[] phiBackground;
	private float[] rho;

	private short[][] z; // all hidden variables
	private boolean[][][] x;

	private int[][] Cua;
	private long[] Clv;
	private int[][] Cword;
	private int[] Cb;

	private int[] countAllWord; // # of words which are general topic a
	private float beta;
	private float betaB;

	private Map<String, Integer> wordsMap = new HashMap<String, Integer>();
	private List<String> uniqueWords = new ArrayList<String>();
	
	private List<User> users = new ArrayList<User>();
	
	public TwitterLDA(int T, int nIter, float alphaGeneral, float beta, float betaB, float gamma) {
		this.T = T;
		this.nIter = nIter;

		this.alphaGeneral = new float[T];
		for (int i = 0; i < T; i++) {
			this.alphaGeneral[i] = alphaGeneral;
			alphaGeneralSum += this.alphaGeneral[i];
		}

		this.gamma = new float[2];
		for (int i = 0; i < 2; i++) {
			this.gamma[i] = gamma;
		}

		Clv = new long[2];
		Clv[0] = 0;
		Clv[1] = 0;

		rho = new float[2];
		rho[0] = 0;
		rho[1] = 0;

		countAllWord = new int[T];
		for (int i = 0; i < T; i++) {
			countAllWord[i] = 0;
		}
		
		this.beta = beta;
		this.betaB = betaB;
	}

	public void estimate() {
		int niter = 0;
		while (true) {
			niter++;
			System.out.println("iteration" + " " + niter + " ...");
			sweep();
			if (niter >= nIter) {
				updateDistribution();
				break;
			}
		}
	}
	
	private void initialize() {
		System.out.print("initializing....");
		this.numOfUsers = users.size();
		Cua = new int[numOfUsers][T];
		thetaGeneral = new float[numOfUsers][T];
		for (int i = 0; i < numOfUsers; i++) {
			for (int j = 0; j < T; j++) {
				Cua[i][j] = 0;
				thetaGeneral[i][j] = 0;
			}
		}
		this.V = this.uniqueWords.size();
		
		this.betaBackground = new float[V];
		this.betaWord = new float[V];
		for (int i = 0; i < V; i++) {
			betaBackground[i] = betaB;
			betaBackgroundSum += betaBackground[i];
			betaWord[i] = beta;
			betaWordSum += betaWord[i];
		}
		
		Cword = new int[T][V];
		phiWord = new float[T][V];
		for (int i = 0; i < T; i++) {
			for (int j = 0; j < V; j++) {
				Cword[i][j] = 0;
				phiWord[i][j] = 0;
			}
		}

		Cb = new int[V];
		phiBackground = new float[V];
		for (int i = 0; i < V; i++) {
			Cb[i] = 0;
			phiBackground[i] = 0;
		}
		
		int u, d, w = 0;
		z = new short[users.size()][];
		x = new boolean[users.size()][][];
		for (u = 0; u < users.size(); u++) {
			User buffer_user = users.get(u);
			z[u] = new short[buffer_user.posts.size()];
			x[u] = new boolean[buffer_user.posts.size()][];

			for (d = 0; d < buffer_user.posts.size(); d++) {
				Post post = buffer_user.posts.get(d);
				x[u][d] = new boolean[post.postwords.length];

				double randgeneral = Math.random();
				double thred = 0;
				short a_general = 0;
				for (short a = 0; a < T; a++) {
					thred += (double) 1 / T;
					if (thred >= randgeneral) {
						a_general = a;
						break;
					}
				}
				
				z[u][d] = a_general;
				Cua[u][a_general]++;
				for (w = 0; w < post.postwords.length; w++) {
					int word = post.postwords[w];
					double randback = Math.random();
					boolean buffer_x;
					if (randback > 0.5) {
						buffer_x = true;
					} else {
						buffer_x = false;
					}

					if (buffer_x == true) {
						Clv[1]++;
						Cword[a_general][word]++;
						countAllWord[a_general]++;
						x[u][d][w] = buffer_x;
					} else {
						Clv[0]++;
						Cb[word]++;
						x[u][d][w] = buffer_x;
					}
				}
			}
		}
		System.out.println("Done");
	}

	private void sweep() {
		for (int userIndex = 0; userIndex < users.size(); userIndex++) {
			User user = users.get(userIndex);
			for (int postIndex = 0; postIndex < user.postsCnt; postIndex++) {
				Post post = user.posts.get(postIndex);
				sampleZ(userIndex, postIndex, user, post);
				for (int wordIndex = 0; wordIndex < post.postwords.length; wordIndex++) {
					int word = post.postwords[wordIndex];
					sampleX(userIndex, postIndex, wordIndex, word);
				}
			}
		}
	}

	private void sampleX(int u, int d, int n, int word) {
		boolean binaryLabel = x[u][d][n];
		int binary;
		if (binaryLabel == true) {
			binary = 1;
		} else {
			binary = 0;
		}

		Clv[binary]--;
		if (binary == 0) {
			Cb[word]--;
		} else {
			Cword[z[u][d]][word]--;
			countAllWord[z[u][d]]--;
		}

		binaryLabel = drawX(u, d, n, word);

		x[u][d][n] = binaryLabel;
		if (binaryLabel == true) {
			binary = 1;
		} else {
			binary = 0;
		}

		Clv[binary]++;
		if (binary == 0) {
			Cb[word]++;
		} else {
			Cword[z[u][d]][word]++;
			countAllWord[z[u][d]]++;
		}
	}

	private boolean drawX(int u, int d, int n, int word) {
		boolean returnValue = false;
		double[] Plv = new double[2];
		double Pb = 1;
		double Ptopic = 1;

		Plv[0] = (Clv[0] + gamma[0]) / (Clv[0] + Clv[1] + gamma[0] + gamma[1]); // part 1 from counting Clv
		Plv[1] = (Clv[1] + gamma[1]) / (Clv[0] + Clv[1] + gamma[0] + gamma[1]);

		Pb = (Cb[word] + betaBackground[word]) / (Clv[0] + betaBackgroundSum); // word in background part(2)
		Ptopic = (Cword[z[u][d]][word] + betaWord[word]) / (countAllWord[z[u][d]] + betaWordSum);

		double p0 = Pb * Plv[0];
		double p1 = Ptopic * Plv[1];
		double sum = p0 + p1;
		double randPick = Math.random();
		if (randPick <= p0 / sum) {
			returnValue = false;
		} else {
			returnValue = true;
		}

		return returnValue;
	}

	private void sampleZ(int userIndex, int postIndex, User user, Post post) {

		short postTopic = z[userIndex][postIndex];
		int w = 0;
		Cua[userIndex][postTopic]--;
		for (w = 0; w < post.postwords.length; w++) {
			int word = post.postwords[w];
			if (x[userIndex][postIndex][w] == true) {
				Cword[postTopic][word]--;
				countAllWord[postTopic]--;
			}
		}

		postTopic = drawZ(userIndex, postIndex, user, post);
		z[userIndex][postIndex] = postTopic;
		Cua[userIndex][postTopic]++;
		for (w = 0; w < post.postwords.length; w++) {
			int word = post.postwords[w];
			if (x[userIndex][postIndex][w] == true) {
				Cword[postTopic][word]++;
				countAllWord[postTopic]++;
			}
		}
	}

	private short drawZ(int userIndex, int postIndex, User user, Post post) { 
		// return y then z
		int word;
		int w;

		double[] Ptopic = new double[T];
		int[] pCount = new int[T];

		Map<Integer, Integer> wordCnt = new HashMap<Integer, Integer>(); // store the topic words with frequency
		for (w = 0; w < post.postwords.length; w++) {
			if (x[userIndex][postIndex][w] == true) {
				word = post.postwords[w];
				if (!wordCnt.containsKey(word)) {
					wordCnt.put(word, 1);
				} else {
					int bufferWordCnt = wordCnt.get(word) + 1;
					wordCnt.put(word, bufferWordCnt);
				}
			}
		}

		for (int topicIndex = 0; topicIndex < T; topicIndex++) {
			Ptopic[topicIndex] = (Cua[userIndex][topicIndex] + alphaGeneral[topicIndex]) / (user.postsCnt - 1 + alphaGeneralSum);

			double P = 1;
			int i = 0;
			for(Entry<Integer, Integer> entry : wordCnt.entrySet()) {
				word = entry.getKey();
				int buffer_cnt = entry.getValue();
				for (int j = 0; j < buffer_cnt; j++) {
					double value = (double) (Cword[topicIndex][word] + betaWord[word] + j) / (countAllWord[topicIndex] + betaWordSum + i);
					i++;
					P *= value;
					P = isOverFlow(P, pCount, topicIndex);
				}
			}

			Ptopic[topicIndex] *= Math.pow(P, (double) 1);
		}

		reComputeProbs(Ptopic, pCount);

		double randz = Math.random();

		double sum = 0;
		for (int a = 0; a < T; a++) {
			sum += Ptopic[a];
		}

		double thred = 0;
		short chosenA = -1;
		for (short a = 0; a < T; a++) {
			thred += Ptopic[a] / sum;
			if (thred >= randz) {
				chosenA = a;
				break;
			}
		}
		
		if (chosenA == -1) {
			System.out.println("chosenA equals -1, error!");
		}

		return chosenA;
	}

	private void reComputeProbs(double[] Ptopic, int[] pCount) {
		int max = pCount[0];
		for (int i = 1; i < pCount.length; i++) {
			if (pCount[i] > max)
				max = pCount[i];
		}
		
		for (int i = 0; i < pCount.length; i++) {
			Ptopic[i] = Ptopic[i] * Math.pow(1e150, pCount[i] - max);
		}
		
	}

	private double isOverFlow(double bufferP, int[] pCount, int a2) {
		if (bufferP > 1e150) {
			pCount[a2]++;
			return bufferP / 1e150;
		}
		if (bufferP < 1e-150) {
			pCount[a2]--;
			return bufferP * 1e150;
		}
		return bufferP;
	}

	private void updateDistribution() {
		for (int userIndex = 0; userIndex < numOfUsers; userIndex++) {
			int Cua = 0;
			for (int topicIndex = 0; topicIndex < T; topicIndex++) {
				Cua += this.Cua[userIndex][topicIndex];
			}
			
			for (int topicIndex = 0; topicIndex < T; topicIndex++) {
				thetaGeneral[userIndex][topicIndex] = (this.Cua[userIndex][topicIndex] + alphaGeneral[topicIndex]) / (Cua + alphaGeneralSum);
			}
		}

		for (int topicIndex = 0; topicIndex < T; topicIndex++) {
			int c_v = 0;
			for (int v = 0; v < V; v++) {
				c_v += Cword[topicIndex][v];
			}
			for (int v = 0; v < V; v++) {
				phiWord[topicIndex][v] = (Cword[topicIndex][v] + betaWord[v]) / (c_v + betaWordSum);
			}
		}

		int Cbv = 0;
		for (int v = 0; v < V; v++) {
			Cbv += Cb[v];
		}
		for (int v = 0; v < V; v++) {
			phiBackground[v] = (Cb[v] + betaBackground[v]) / (Cbv + betaBackgroundSum);
		}
		for (int l = 0; l < 2; l++) {
			rho[0] = (Clv[0] + gamma[0]) / (Clv[0] + Clv[1] + gamma[0] + gamma[1]);
			rho[1] = (Clv[1] + gamma[1]) / (Clv[0] + Clv[1] + gamma[0] + gamma[1]);
		}	
	}

	public void outputWordsInTopics(String output) throws Exception {
		BufferedWriter writer = new BufferedWriter(new FileWriter(new File(output)));
		for (int a = 0; a < T; a++) {
			String topicline = "Topic " + a + ":";
			writer.write(topicline);

			List<Integer> rankList = CollectionsUtils.getTop(phiWord[a], wordCnt);
			for (int i = 0; i < rankList.size(); i++) {
				String tmp = "\t" + uniqueWords.get(rankList.get(i)) + "\t" + phiWord[a][rankList.get(i)];
				writer.write(tmp + "\n");
			}
		}
		writer.flush();
		writer.close();
	}

	public void outputTopicDistributionOnUsers(String outputDir) throws Exception {
		String outputfile = outputDir + "TopicsDistributionOnUsers.txt";
		BufferedWriter writer = null;
		writer = new BufferedWriter(new FileWriter(new File(outputfile)));

		for (int u = 0; u < numOfUsers; u++) {
			String bufferline1 = "";
			String name = users.get(u).userID;
			writer.write(name + "\t");
			for (int a = 0; a < T; a++) {
				bufferline1 += thetaGeneral[u][a] + "\t";
			}
			writer.write(bufferline1 + "\n");
		}
		writer.flush();
		writer.close();

		outputfile = outputDir + "TopicCountsOnUsers.txt";
		writer = new BufferedWriter(new FileWriter(new File(outputfile)));

		for (int u = 0; u < numOfUsers; u++) {
			String bufferline1 = "";
			String name = users.get(u).userID;
			writer.write(name + "\t");
			for (int a = 0; a < T; a++) {
				bufferline1 += Cua[u][a] + "\t";
			}
			writer.write(bufferline1 + "\n");
		}
		writer.flush();
		writer.close();
	}

	public void outputBackgroundWordsDistribution(String output) throws Exception {
		BufferedWriter writer = new BufferedWriter(new FileWriter(new File(output)));
		// phi_background
		List<Integer> rankList = CollectionsUtils.getTop(phiBackground, wordCnt);
		for (int i = 0; i < rankList.size(); i++) {
			String tmp = "\t" + uniqueWords.get(rankList.get(i)) + "\t" + phiBackground[rankList.get(i)];
			writer.write(tmp + "\n");
		}
		writer.flush();
		writer.close();
	}

	public void outputTextWithLabel(String output) throws Exception {
		BufferedWriter writer = null;
		for (int u = 0; u < users.size(); u++) {
			User buffer_user = users.get(u);
			writer = new BufferedWriter(new FileWriter(new File(output + "/" + buffer_user.userID)));

			for (int d = 0; d < buffer_user.postsCnt; d++) {
				Post buffer_post = buffer_user.posts.get(d);
				String line = "z=" + z[u][d] + ":  ";
				for (int n = 0; n < buffer_post.postwords.length; n++) {
					int word = buffer_post.postwords[n];
					if (x[u][d][n] == true) {
						line += uniqueWords.get(word) + "/" + z[u][d] + " ";
					} else {
						line += uniqueWords.get(word) + "/" + "false" + " ";
					}
				}
				int buffertime = buffer_post.time + 1;
				if (buffertime <= 30) {
					if (buffertime < 10) {
						line = "2011-09-0" + buffertime + ":\t" + line;
					} else {
						line = "2011-09-" + buffertime + ":\t" + line;
					}
				} else if (buffertime <= 61 && buffertime > 30) {
					int buffer_time = buffertime - 30;
					if (buffertime - 30 < 10) {
						line = "2011-10-0" + buffer_time + ":\t" + line;
					} else {
						line = "2011-10-" + buffer_time + ":\t" + line;
					}
				} else if (buffertime > 61) {
					int buffer_time = buffertime - 61;
					if (buffertime - 61 < 10) {
						line = "2011-11-0" + buffer_time + ":\t" + line;
					} else {
						line = "2011-11-" + buffer_time + ":\t" + line;
					}
				}
				writer.write(line + "\n");
			}
			writer.flush();
			writer.close();
		}
	}

	@Override
	public void run(Map<String, Vector> vectorsMap, Map<String, Item> items) throws IOException {
		
		Iterator<Item> it = items.values().iterator();
		Map<String, List<Item>> itemsPerUser = ItemsUtils.getItemsPerUser(it);
		for (Entry<String, List<Item>> entry : itemsPerUser.entrySet()) {
			String username = entry.getKey();
			List<Item> userItems = entry.getValue();
			User tweetUser;
			if(vectorsMap == null || vectorsMap.isEmpty()) {
				tweetUser = new User(username, userItems, wordsMap, uniqueWords);
			}
			else {
				tweetUser = new User(username, userItems, vectorsMap, wordsMap, uniqueWords);
			}
			users.add(tweetUser);
		}
		
		initialize();
		estimate();
	}

	@Override
	public List<Topic> getTopics() {
		Map<Integer, Topic> map = getTopicsMap();
		List<Topic> topics = new ArrayList<Topic>(map.values());
		return topics;
	}

	@Override
	public Map<Integer, Topic> getTopicsMap() {
		Map<Integer, Topic> topicsMap = new HashMap<Integer, Topic>();
		for (int topicId = 0; topicId < T; topicId++) {			
			Map<String, Double> topicWords = new HashMap<String, Double>();
			List<Integer>  rankList = CollectionsUtils.getTop(phiWord[topicId], wordCnt);
			for (int i = 0; i < rankList.size(); i++) {
				int index = rankList.get(i);
				String word = uniqueWords.get(index);
				float phi = phiWord[topicId][index];
				
				topicWords.put(word, (double) phi);
			}
			Topic topic = new Topic(topicId, topicWords);
			topicsMap.put(topicId, topic);
		}
		return topicsMap;
	}

	@Override
	public int getNumOfTopics() {
		return T;
	}

	@Override
	public Map<Integer, Collection<String>> getTopicAssociations() {
		Map<Integer, Collection<String>> associations = new HashMap<Integer, Collection<String>>();
		for (int u = 0; u < users.size(); u++) {
			User user = users.get(u);
			for (int d = 0; d < user.postsCnt; d++) {
				Post post =  user.posts.get(d);
				int topicIndex = (int) z[u][d];
				
				Collection<String> itemsList = associations.get(topicIndex);
				if(itemsList == null) {
					itemsList = new ArrayList<String>();
					associations.put(topicIndex, itemsList);
				}
				itemsList.add(post.id);
			}
		}
		return associations;
	}

	public class User {

		protected String userID;
		protected int postsCnt;
		protected List<Post> posts = new ArrayList<Post>();
		
		public User(String id, List<Item> items, Map<String, Integer> wordMap, List<String> uniWordMap) {
			this.userID = id;
			this.postsCnt = items.size();
			for(Item item : items) {
				String itemId = item.getId();
				String text = item.getText();
				Post post = new Post(itemId, text, wordMap, uniWordMap);
				posts.add(post);						
			}
		}
		
		public User(String id, List<Item> items, Map<String, Vector> vectorsMap, 
				Map<String, Integer> wordMap, List<String> uniWordMap) {
			this.userID = id;
			this.postsCnt = items.size();
			for(Item item : items) {
				String itemId = item.getId();
						
				String text = item.getText();
				Post post = new Post(itemId, text, vectorsMap, wordMap, uniWordMap);
				posts.add(post);						
			}
		}
		
		public class Post {
			
			protected String id;
			
			protected int time;
			protected int[] postwords;
			
			public Post(String id, String text, Map<String, Integer> wordMap, List<String> uniWordMap) {

				this.id = id;
				
				int number = wordMap.size();

				List<Integer> words = new ArrayList<Integer>();
				List<String> tokens = new ArrayList<String>();
				TextAnalyser.getTokens(text, tokens);
				
				for (int i = 0; i < tokens.size(); i++) {
					String tmpToken = tokens.get(i).toLowerCase();
					if (!Stopwords.isStopword(tmpToken) && !isNoisy(tmpToken)) {
						if (!wordMap.containsKey(tmpToken)) {
							words.add(number);
							wordMap.put(tmpToken, number++);
							uniWordMap.add(tmpToken);
						} else {
							words.add(wordMap.get(tmpToken));
						}
					}
				}

				postwords = new int[words.size()];
				for (int w = 0; w < words.size(); w++) {
					postwords[w] = words.get(w);
				}
				words.clear();
				tokens.clear();
			}

			public Post(String id, String text, Map<String, Vector> vectorsMap, 
					Map<String, Integer> wordMap, List<String> uniWordMap) {

				this.id = id;
				int number = wordMap.size();

				List<Integer> words = new ArrayList<Integer>();
				Vector vector = vectorsMap.get(id);
				if(vector != null) {
					Set<String> terms = vector.getTerms();
					for (String term : terms) {
						String tmpToken = term.toLowerCase();
						if (!Stopwords.isStopword(tmpToken) && !isNoisy(tmpToken)) {
							if (!wordMap.containsKey(tmpToken)) {
								words.add(number);
								wordMap.put(tmpToken, number++);
								uniWordMap.add(tmpToken);
							} else {
								words.add(wordMap.get(tmpToken));
							}
						}
					}
				}
				
				postwords = new int[words.size()];
				for (int w = 0; w < words.size(); w++) {
					postwords[w] = words.get(w);
				}
			}
			
			private boolean isNoisy(String token) {
				Collection<String> stopwords = Vocabulary.getStopwords();
				if(stopwords != null && !stopwords.isEmpty()) {
					if(stopwords.contains(token)) {
						return true;
					}
				}
				
				if (token.toLowerCase().contains("#pb#")
						|| token.toLowerCase().contains("http:"))
					return true;
				if (token.contains("@")) {
					return true;
				}
				return token.matches("[\\p{Punct}]+");
			}

		}
	}

	
	private static String dataset = "SNOW";
	
	public static void main(String args[]) throws Exception {
		
		MorphiaDAO<Item> dao = new MorphiaDAO<Item>("160.40.50.207" , dataset, Item.class);
		
		Query<Item> query = dao.getQuery().filter("original =", Boolean.TRUE);
		Map<String, Item> itemsMap = ItemsUtils.loadItems(dao.iterator(query));
		System.out.println(itemsMap.size() + " items");
		
		TextIndex tIndex = new TextIndex("/disk1_data/Datasets/" + dataset + "/TextIndex");
		tIndex.open();
		//Map<String, Vector> vectorsMap = tIndex.getVectorsMap(itemsMap.keySet(), "text");
		Map<String, Vector> vectorsMap = tIndex.createVocabulary("text");
		System.out.println(vectorsMap.size() + " vectors");
		
		// Estimation for the number of topics in the dataset
		int T = (int) Math.sqrt(itemsMap.size());
		
		// topics, iterations, alphaG, beta, betaB, gamma
		TwitterLDA twLDAmodel = new TwitterLDA(T, 300, 0.5f, 0.01f, 0.01f, 20);
		twLDAmodel.run(vectorsMap, itemsMap);
		
		Map<Integer, Collection<String>> associations = twLDAmodel.getTopicAssociations();
		
		//twLDAmodel.outputTextWithLabel("/disk1_data/Datasets/" + dataset + "/topics");
		//twLDAmodel.outputBackgroundWordsDistribution("/disk1_data/Datasets/" + dataset + "/BackgroundWordsDistribution.txt");
		//twLDAmodel.outputTopicDistributionOnUsers("/disk1_data/Datasets/" + dataset + "/");
		//twLDAmodel.outputWordsInTopics("/disk1_data/Datasets/" + dataset + "/WordsInTopics.txt");
		
		Set<String> associated = new HashSet<String>();
		for(Collection<String> c : associations.values()) {
			associated.addAll(c);
		}
		
		Integer topicId = Collections.max(associations.keySet());
		Set<String> unclustered = new HashSet<String>(vectorsMap.keySet());
		unclustered.removeAll(associated);
		for(String id : unclustered) {
			Set<String> set = new HashSet<String>();
			set.add(id);
			associations.put(++topicId, set);
		}
		
		
		IOUtil.saveClusters(associations.values(), "/disk1_data/Datasets/" + dataset + "/twitter-lda-clusters.txt");
		
	}
}
