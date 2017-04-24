package gr.iti.mklab.models;

import gr.iti.mklab.dao.MorphiaDAO;
import gr.iti.mklab.utils.StringUtils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.mongodb.morphia.query.Query;

import twitter4j.HashtagEntity;
import twitter4j.Status;
import twitter4j.URLEntity;

public class Timeline implements Serializable, Iterable<Entry<Long, Integer>>{

	private static final long serialVersionUID = -6107744748512784691L;
	
	protected TreeMap<Long, Integer> histogram = new TreeMap<Long, Integer>();
	
	protected TreeMap<Long, Double> Z = new TreeMap<Long, Double>();
	
	protected Set<Long> minimaBins = new HashSet<Long>();
	
	protected long div;
	protected Long minTime = Long.MAX_VALUE, maxTime = Long.MIN_VALUE;
	
	public Timeline(int time, TimeUnit tu) {
		div = TimeUnit.MILLISECONDS.convert(time, tu);
	}
	
	private Integer total = 0;
	private Double peakiness = null, sustainedInterestRatio = null;
	private Long peakTime = null;
	
	private List<Pair<Long, Long>> windows = new ArrayList<Pair<Long, Long>>();
	private List<Long> activeBins = new ArrayList<Long>();
	
	private Integer maxCount;
	private Double meanCount = 0d;
	private Double stdCount = 0d;
	
	public void put(Long key, Integer value) {
		
		total += value; 
		
		key = (key/div)*div;
		
		if(key > maxTime) {
			maxTime = key;
		}
		
		if(key < minTime) {
			minTime = key;
		}
		
		Integer currFreq = histogram.get(key);
		if(currFreq != null) {
			value += currFreq;
		}
		
		histogram.put(key, value);
	}
	
	public Integer getFrequency(Long key) {
		key = (key/div)*div;
		Integer freq = histogram.get(key);
		if(freq == null)
			return 0;
		
		return freq;
	}
	
	public Integer getFrequency(Pair<Long, Long> window) {
		Integer freq = 0;
		
		long t1 = (window.left / div) * div;
		long t2 = (window.right / div) * div;
		
		for(long t = t1; t <= t2; t += div) {
			freq += getFrequency(t);
		}
		return freq;
	}
	
	public Integer getTotal() {
		return total;
	}
	
	public Double getPeakiness() {
		if(peakiness == null) {
			for(Long bin = minTime; bin<maxTime; bin += div) {
				if(!histogram.containsKey(bin))
					histogram.put(bin, 0);
			}
			
			peakiness = 0D;
			for(Entry<Long, Integer> h : histogram.entrySet()) {
				Double p = h.getValue().doubleValue() / total.doubleValue();
				if(p > peakiness) {
					peakiness = p;
					peakTime = h.getKey();
				}
				meanCount += h.getValue();
			}
			meanCount = meanCount / histogram.size();
			
			for(Entry<Long, Integer> h : histogram.entrySet()) {
				Double count = h.getValue().doubleValue();
				stdCount += Math.pow(count-meanCount, 2);
			}
			stdCount = Math.sqrt(stdCount / histogram.size());
			
			for(Entry<Long, Integer> h : histogram.entrySet()) {
				Double count = h.getValue().doubleValue();
				Double z = (count - meanCount) / stdCount;
				Z.put(h.getKey(), z);
			}
		}
		return peakiness;
	}
	
	public Long getPeakTime() {
		if(peakTime == null) {
			getPeakiness();
		}
		return peakTime;
	}
	
	public Double getSustainedInterest() {
		if(sustainedInterestRatio == null) {
			Double peakiness = getPeakiness();
			if(peakiness == 1) {
				return 0D;
			}
			
			if(histogram.isEmpty())
				return 0D;
			
			Long peakTime = getPeakTime();
			Long t1 = histogram.firstKey();
			Long t2 = histogram.lastKey();
			
			Double prePeakNTF = 0D;
			for(Long t = t1; t<peakTime; t += div) {
				Integer tf = histogram.get(t);
				if(tf != null) {
					prePeakNTF += (tf.doubleValue()/total.doubleValue());
				}
			}
			
			if(peakTime -1 == 0)
				prePeakNTF = 0d;
			else
				prePeakNTF = prePeakNTF/(peakTime - t1);
			
			Double postPeakNTF = 0D;
			for(Long t = peakTime+div; t<=t2; t += div) {
				Integer tf = histogram.get(t);
				if(tf != null) {
					postPeakNTF += (tf.doubleValue()/total.doubleValue());
				}
			}
			
			if(t2 - peakTime == 0)
				postPeakNTF = 0d;
			else
				postPeakNTF = postPeakNTF/(t2 - peakTime);	
			
			if(prePeakNTF == 0)
				prePeakNTF = 1d;
			
			sustainedInterestRatio = postPeakNTF / prePeakNTF;
		}
		return sustainedInterestRatio;
	}
	
	public void detectLocalMinima() {
		Set<Integer> minimaIndices = new HashSet<Integer>();
		
		int start = 0;
		int end = histogram.size();
		
		List<Long> bins = new ArrayList<Long>(histogram.keySet());
		Collections.sort(bins);
		
		Long[] x = new Long[bins.size()];
		Integer[] y = new Integer[bins.size()];
		
		int index = 0;
		for(Long bin : bins) {
			x[index] = bin;
			y[index++] = histogram.get(bin);
		}
		
		findLocalMinima(y, start, end, minimaIndices);
		
		for(Integer i : minimaIndices) {
			minimaBins.add(x[i]);
		}
	}

	private void findLocalMinima(Integer[] array, int start, int end, Set<Integer> minima) {
	    int mid = (start + end) / 2;
	    if ((mid-2) < 0 && (mid+1) >= array.length)
	        return;
	    
	    if (array[mid - 2] > array[mid - 1] && array[mid-1] < array[mid]) {
	    	minima.add(mid - 1);
	    	return;
	    }
	    
	    if (array[mid - 1] > array[mid - 2]) {
	        findLocalMinima(array, start, mid, minima);
	    }
	    else {
	        findLocalMinima(array, mid, end, minima);
	    }
	}
	
	public List<Pair<Long, Long>> detectPeakWindows() {
	
		getPeakiness();
		
		double th = 2.;

		Set<Long> keyset = histogram.keySet();
		List<Long> keys = new ArrayList<Long>();
		keys.addAll(keyset);
		Collections.sort(keys);
		//Long[] keys = .toArray(new Long[histogram.size()]);
		
		Collection<Integer> values = histogram.values();
		if(values.isEmpty() || values.size()<2)
			return windows;
	
		this.maxCount =  Collections.max(values);
		
		double mean = histogram.get(keys.get(0));
		double meandev = 0;
		//for(int i=0; i<Math.min(5, keys.length); i++) {
		//	meandev += Math.abs((mean - histogram.get(keys[i])));
		//}
		//meandev = meandev / Math.min(5, keys.length);
		for(int i=0; i<Math.min(5, keys.size()); i++) {
			meandev += Math.pow( histogram.get(keys.get(i)) - mean, 2);
		}
		meandev = meandev / Math.min(5.0, keys.size());
		
		for(int i=1; i<keys.size(); i++) {
			
			Integer Ci = histogram.get(keys.get(i));
			Integer Ci_1 = histogram.get(keys.get(i-1));
		
			if (Math.abs(Ci - mean)/meandev > th && Ci > Ci_1) {
		
				int start = i - 1, end = i;
				while(i < keys.size() && Ci > Ci_1) {
					Ci = histogram.get(keys.get(i));
					Ci_1 = histogram.get(keys.get(i-1));
					
					double[] m = update(mean, meandev, Ci);
					mean = m[0];
					meandev = m[1];
					i++;
				}
				
				Integer Cstart = histogram.get(keys.get(start));
				while (i < keys.size() && Ci > Cstart) {
					Ci = histogram.get(keys.get(i));
					Ci_1 = histogram.get(keys.get(i-1));
					
					if (Math.abs(Ci - mean)/meandev > th && Ci > Ci_1) {
						end = --i;
						break;
					}
					else {
						double[] m = update(mean, meandev, Ci);
						mean = m[0];
						meandev = m[1];
						end = i++;
					}
				}
		
				if(start <= end) {
					Long startTime = keys.get(start);
					Long endTime = keys.get(end);
					 
					if(windows.isEmpty()) {
						windows.add(Pair.of(startTime, endTime));
					}
					else {
						int lastIndex = windows.size()-1;
						Pair<Long, Long> lastWindow = windows.get(lastIndex);
						if(lastWindow.right == startTime) {
							windows.set(lastIndex, Pair.of(lastWindow.left, endTime));
						}
						else {
							windows.add(Pair.of(startTime, endTime));
						}
					}
				}
			}
			else {
				double[] m = update(mean, meandev, Ci);
				mean = m[0];
				meandev = m[1];
			}
		}
		
		//Keep a sorted list of active bins
		for(Pair<Long, Long> window : windows) {
			for(Long bin = window.left; bin<=window.right; bin += div) {
				activeBins.add(bin);
			}
		}
		Collections.sort(activeBins);
		
		return windows;
	}
		
	public List<Pair<Long, Long>> getPeakWindows() {
		if(windows == null || windows.isEmpty()) {
			detectPeakWindows();
		}
		return windows;
	}
	
	public List<Long> getActiveBins() {
		if(activeBins == null || activeBins.isEmpty()) {
			detectPeakWindows();
		}
		return activeBins;
	}
	
	public Integer getMaxFreaquency() {
		return maxCount;
	}
	
	private double[] update(double oldmean, double oldmeandev, double updatevalue) {
	
		double diff = Math.abs(oldmean - updatevalue);
		double a = 0.125;
		
		double newmeandev = a*diff + (1.0-a) * oldmeandev;
		double newmean = a*updatevalue + (1.0-a)*oldmean;
		double[] ret = {newmean, newmeandev};
		
		return ret;
	}
	
	public void serialize(String filename) throws IOException {
		FileOutputStream fileOut = new FileOutputStream(filename);
		ObjectOutputStream out = new ObjectOutputStream(fileOut);
		
		out.writeObject(this);
        out.close();
        fileOut.close();
	}
	
	public static Timeline deserialize(String filename) throws IOException, ClassNotFoundException {
		FileInputStream fileIn = new FileInputStream(filename);
        ObjectInputStream in = new ObjectInputStream(fileIn);
        Timeline obj = (Timeline) in.readObject();
        in.close();
        fileIn.close();
        
        return obj;
	}
	
	public Long getTimeslotLength() {
		return div;
	}
	
	public void writeToFile(String filename) {
		try {
			for(Long bin = minTime; bin<=maxTime; bin += div) {
				if(!histogram.containsKey(bin)) {
					histogram.put(bin, 0);
				}
			}
			
			SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MM/dd/yyyy' 'HH:mm:ss");
			
			List<String> lines = new ArrayList<String>();
			for(Entry<Long, Integer> e : histogram.entrySet()) {
				Long timestamp = e.getKey();
				Date date = new Date(timestamp);
				String dateStr = simpleDateFormat.format(date);

		   
				Integer value = e.getValue();
				
				Double z = Z.get(timestamp);
				
				StringBuffer strBuff = new StringBuffer(timestamp + "\t" +  dateStr + "\t" + value + "\t" + z);
				strBuff.append("\t" + windows.size() + "\t" + maxCount + "\t" + total);
				String burst = "0";
				for(Pair<Long, Long> window : windows) {
					if(timestamp>=window.left && timestamp<=window.right) {
						burst = value.toString();
						break;
					}
				}
				strBuff.append("\t" + burst);
				
				if(minimaBins.contains(timestamp)) {
					strBuff.append("\t" + value);
				}
				else {
					strBuff.append("\t" + 0);
				}
				
				lines.add(strBuff.toString());
			}
			
			OutputStream output = new FileOutputStream(filename);
			IOUtils.writeLines(lines, "\n", output);
		} catch (Exception e) {
			e.printStackTrace();
		} 
	}
	
	public int size() {
		return histogram.size();
	}
	
	public static Timeline createTimeline(int time, TimeUnit tu, Collection<Item> items) {
		Timeline timeline = new Timeline(time, tu);
		for(Item item : items) {
			long publicationTime = item.getPublicationTime();
			timeline.put(publicationTime, 1);
		}
		return timeline;
	}
	
	public static Timeline createTimeline(int time, TimeUnit tu, Iterator<Item> it) {
		Timeline timeline = new Timeline(time, tu);
		while(it.hasNext()) {
			Item item = it.next();
			long publicationTime = item.getPublicationTime();
			timeline.put(publicationTime, 1);
		}
		return timeline;
	}
	
	public void merge(Timeline other) {
		for(Entry<Long, Integer> entry : other.histogram.entrySet()) {
			
			Integer count = entry.getValue();
			if(count != null) {
				this.put(entry.getKey(), count);
			}
			
			
		}
	}
	
	public static Map<String, Timeline> getNgramsTimeline(List<Status> statuses, int N, int interval, TimeUnit tu) {
		Map<String, Timeline> timelines = new HashMap<String, Timeline>();
		
		for(Status status : statuses) {
			String text = status.getText();
			
			URLEntity[] urlsEntities = status.getURLEntities();
			List<String> urls = new ArrayList<String>(urlsEntities.length);
			for(int i=0; i<urls.size(); i++)
				urls.add(urlsEntities[i].getURL());
			
			text = StringUtils.clean(text, urls) ;
			
		    Date date = status.getCreatedAt();
		    
		    Long bin = date.getTime();
		    
		    List<String>  tokens = new ArrayList<String>();
		    //try {
		    	//tokens = getNgrams(text, N);
			//} catch (IOException e) {
			//	continue;
			//}    	
		    for(String token : tokens) {
		    	Timeline timeline = timelines.get(token);
		    	if(timeline == null) {
		    		timeline = new Timeline(interval, tu);
		    		timelines.put(token, timeline);
		    	}
		    	
		    	timeline.put(bin, 1);
		    }
		}
		return timelines;
	}
	
	public static Map<String, Timeline> getTagsTimeline(List<Status> statuses, int interval, TimeUnit tu) {
		Map<String, Timeline> timelines = new HashMap<String, Timeline>();
		
		for(Status status : statuses) {
			HashtagEntity[] hashtags = status.getHashtagEntities();
		    Date date = status.getCreatedAt();	    
		    Long bin = date.getTime();
   
		    for(HashtagEntity hashtagEntity : hashtags) {
		    	String hashtag = hashtagEntity.getText().toLowerCase();
		    	
		    	Timeline timeline = timelines.get(hashtag);
		    	if(timeline == null) {
		    		timeline = new Timeline(interval, tu);
		    		timelines.put(hashtag, timeline);
		    	}
		    	
		    	timeline.put(bin, 1);
		    }
		}
		return timelines;
	}
	
	public List<Pair<Long, Long>> getTimeIntervals(boolean smooth) {
		
		List<Pair<Long, Long>> intervals = new ArrayList<Pair<Long, Long>>();
		int s = histogram.size();
		
		Long[] t = new Long[s];
		Integer[] f = new Integer[s];
		Integer[] diff = new Integer[s];
		List<Integer> localMins = new ArrayList<Integer>();
		
		int index = 0;
		for(Entry<Long, Integer> e : histogram.entrySet()) {
			t[index] = e.getKey();
			f[index] = e.getValue();
			index++;
		}

		diff[0] = 0;
		for(int i=1; i<f.length; i++) {
			diff[i] = f[i]-f[i-1];
		}
		
		if(smooth) {
			Integer[] temp = Arrays.copyOf(diff, diff.length);
			for(int i=2; i<diff.length-2; i++) {
				diff[i] = (temp[i-2] + 2*temp[i-1] + 7*temp[i] + 2*temp[i+1] + temp[i+2])/13;
			}
			diff[0] = (7*temp[0] + 2*temp[1] + temp[2])/10;
			diff[1] = (2*temp[0] + 7*temp[1] + 2*temp[2] + temp[3])/12;
			diff[diff.length-2] = (temp[diff.length-4] + 2*temp[diff.length-3] + 7*temp[diff.length-2] + 2*temp[diff.length-1])/12;
			diff[diff.length-1] = (temp[diff.length-3] + 2*temp[diff.length-2] + 7*temp[diff.length-1])/10;
		}
		
		for(int i=0; i<diff.length-1; i++) {
			if(diff[i] < 0 && diff[i+1]>0) {
				localMins.add(i);
			}
		}
		
		for(int i=0; i<localMins.size()-1; i++) {
			Integer localMin1 = localMins.get(i);
			Integer localMin2 = localMins.get(i+1);
			
			Long t1 = t[localMin1];
			Long t2 = t[localMin2];
			
			intervals.add(new Pair<Long, Long>(t1, t2));
		}
		return intervals;
	}

	public Long getMinTime() {
		return Collections.min(histogram.keySet());
	}
	
	public Long getMaxTime() {
		return Collections.max(histogram.keySet());
	}
	
	public static void main(String...args) throws Exception {
		String hostname = "160.40.50.207";
		String dbName = "Sundance2013";
		
		MorphiaDAO<Item> dao = new MorphiaDAO<Item>(hostname ,dbName, Item.class);
		Query<Item> query = dao.getQuery().filter("publicationTime>", 1358200800000L);
		
		System.out.println(dao.count(query) + " items!");
		
		Timeline timeline = Timeline.createTimeline(30, TimeUnit.MINUTES, dao.iterator(query));
		System.out.println("Timeline Created");
		
		//timeline.detectPeakWindows();
		//System.out.println(timeline.getPeakWindows().size() + " peak windows detected!");
		
		//timeline.detectLocalMinima();
		timeline.writeToFile("/home/manosetro/Desktop/timeline.csv");
	}

	@Override
	public Iterator<Entry<Long, Integer>> iterator() {
		return histogram.entrySet().iterator();
	}
}
