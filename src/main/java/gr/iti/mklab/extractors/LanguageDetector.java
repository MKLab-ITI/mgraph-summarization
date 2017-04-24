package gr.iti.mklab.extractors;

import gr.iti.mklab.models.Item;

import com.cybozu.labs.langdetect.Detector;
import com.cybozu.labs.langdetect.DetectorFactory;
import com.cybozu.labs.langdetect.LangDetectException;

public class LanguageDetector {

	public LanguageDetector(String langProfiles) throws LangDetectException {
		DetectorFactory.loadProfile(langProfiles);
	}
	
	public String detect(Item item) {
        try {
        	Detector detector = DetectorFactory.create();
    		String text = item.getText();
            detector.append(text);
            
        	String language = detector.detect();
        	return language;
        }
        catch(Exception e) {
        	return null;
        }
        
    }
}
