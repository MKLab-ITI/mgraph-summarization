package gr.iti.mklab.summarization.tscan;

import java.util.ArrayList;
import java.util.List;

public class Event {
		
		public int theme;
		
		public int maxAmplBlock;
		public double maxAmplitude;
		
		public int bb;
		public int eb;
		
		public List<Integer> blocks = new ArrayList<Integer>();
		
		public static double temporalWeight(Event ei, Event ej, int n) {
			
			if(ej.bb > ei.eb) {
				return 1. - (double)(ej.bb - ei.eb)/(double)n;
			}
			else {
				double D = ei.blocks.size() + ej.blocks.size();
				return 1 - 2.0*(Math.min(ei.eb, ej.eb) - ej.bb)/D;
			}	
		}
		
		public String toString() {
			return "theme:" + theme + ", bb:" + bb + ", eb:" + eb + ", maxAmplBlock:" + maxAmplBlock +
					", maxAmplitude:" + maxAmplitude + ", blocks:" + blocks.size();
		}
	}