package gr.iti.mklab.utils;

import java.util.*;
import java.io.*;

public class FileUtils {
	
	public static void writeLines(String file, List<?> counts) {
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(new File(file)));
			for (int i = 0; i < counts.size(); i++) {
				writer.write(counts.get(i) + "\n");
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

	}

	public static void writeLine(String file, String string2) {
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(new File(file)));
			writer.write(string2 + "\n");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public static String getKeysFromValue(HashMap<Integer, String> hm,
			String value) {
		Set<?> s = hm.entrySet();
		// Move next key and value of HashMap by iterator
		Iterator<?> it = s.iterator();
		while (it.hasNext()) {
			// key=value separator this by Map.Entry to get key and value
			@SuppressWarnings("rawtypes")
			Map.Entry m = (Map.Entry) it.next();
			if (m.getValue().equals(value))
				return m.getKey() + "";
		}
		System.err.println("Error, can't find the data in Hashmap!");
		return null;
	}
	
	/**
	 * Create a directory by calling mkdir();
	 * 
	 * @param dirFile
	 */
	public static void mkdir(File dirFile) {
		System.out.println("Creating the folder: " + dirFile);
		try {
			boolean bFile = dirFile.exists();
			if (bFile == true) {
				System.out.println("The folder exists.");
			} else {
				System.out
						.println("The folder do not exist,now trying to create a one...");
				bFile = dirFile.mkdir();
				if (bFile == true) {
					System.out.println("Create successfully!");
				} else {
					System.out
							.println("Disable to make the folder,please check the disk is full or not.");
					System.exit(1);
				}
			}
		} catch (Exception err) {
			System.err.println("ELS - Chart : unexpected error");
			err.printStackTrace();
		}
	}

	/**
	 * 
	 * @param path
	 * @return
	 */
	static public boolean deleteDirectory(File path) {
		if (path.exists()) {
			File[] files = path.listFiles();
			for (int i = 0; i < files.length; i++) {
				if (files[i].isDirectory()) {
					deleteDirectory(files[i]);
				} else {
					files[i].delete();
				}
			}
		}
		return (path.delete());
	}
	
	/**
	 * Frequently used functions
	 * */
	static public int count(String a, String contains) {
		int i = 0;
		int count = 0;
		while (a.contains(contains)) {
			i = a.indexOf(contains);
			a = a.substring(0, i) + a.substring(i + contains.length(), a.length());
			count++;
		}
		return count;
	}

	public static void writeLines(String file, Map<?, ?> hashMap) {
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(new File(file)));

			Set<?> s = hashMap.entrySet();
			Iterator<?> it = s.iterator();
			while (it.hasNext()) {
				@SuppressWarnings("rawtypes")
				Map.Entry m = (Map.Entry) it.next();
				writer.write(m.getKey() + "\t" + m.getValue() + "\n");
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

}