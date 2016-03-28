package gr.iti.mklab.utils;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.IOUtils;

public class StringUtils {

	public static String clean(String text) {
		text = text.replaceAll("\\r\\n|\\r|\\n", " ");	
		text = text.replaceAll("RT", " ");
		text = text.replaceAll("http.*$| ", " ");
		
		return text;
	}
	
	public static String clean(String text, List<String> termsToExclude) {
		text = clean(text);
		for(String term : termsToExclude)
			text = text.replaceAll(term, " ");	
		
		return text;
	}
	
	public static String cleanNonUTF(String text) throws IOException {
		CharsetDecoder decoder = Charset.forName("utf-8").newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPLACE);
        decoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
        ByteBuffer bb = ByteBuffer.wrap(IOUtils.toByteArray(new StringReader(text)));
        CharBuffer parsed = decoder.decode(bb);
        
        return parsed.toString();
	}
	
	public static String join(Collection<String> tokens, String separator) {
		return edu.stanford.nlp.util.StringUtils.join(tokens, separator);
	}
}
