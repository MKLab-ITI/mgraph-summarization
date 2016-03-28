package gr.iti.mklab.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.IOUtils;

import cern.colt.Arrays;

public class IOUtil {

	public static void main(String[] args) throws IOException {
		File f = new File("/disk1_data/workspace/somus2014/results/results_tw_1.txt");
		Map<String, String[]> results = readResults(f);
		for(Entry<String, String[]> e : results.entrySet()) {
			System.out.println(e.getKey() + " : " + Arrays.toString(e.getValue()));
		}
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
}
