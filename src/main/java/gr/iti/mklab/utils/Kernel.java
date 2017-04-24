package gr.iti.mklab.utils;

public class Kernel {
	
	public static double gaussian(long dt, long s) {
		return Math.exp(-(dt*dt)/(2.*s*s));
	}

	public static double triangle(long dt, long s) {
		if(dt > s) {
			return 0.;
		}
		return 1.0 - ((double)dt / (double)s);
	}

	public static double circle(long dt, long s) {
		if(dt > s) {
			return 0.;
		}
		return Math.sqrt(1.0 - Math.pow(((double)dt / (double)s), 2));
	}
	
	public static double cosine(long dt, long s) {
		if(dt > s) {
			return 0.;
		}
		
		return 0.5 * (1.0 + Math.cos( ((double)dt * Math.PI) / (double)s));
	}
	
	public static double window(long dt, long s) {
		if(dt > s) {
			return 0.;
		}

		return 1.;
	}
}
