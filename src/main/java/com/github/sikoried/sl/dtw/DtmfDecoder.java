package com.github.sikoried.sl.dtw;

import com.github.sikoried.jstk.exceptions.MalformedParameterStringException;
import com.github.sikoried.jstk.framed.DTMF;
import com.github.sikoried.jstk.framed.FFT;
import com.github.sikoried.jstk.framed.Window;
import com.github.sikoried.jstk.io.FrameInputStream;
import com.github.sikoried.jstk.io.FrameSource;
import com.github.sikoried.jstk.sampled.AudioFileReader;
import com.github.sikoried.jstk.sampled.AudioSource;
import com.github.sikoried.jstk.sampled.RawAudioFormat;
import com.github.sikoried.jstk.util.ArrayUtils;
import com.github.sikoried.jstk.util.Distances;
import com.github.sikoried.jstk.util.Various;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Stack;

public class DtmfDecoder {
	static final char[] labels = "_123A456B789C*0#D".toCharArray();
	static final double[][] states = new double[17][9];

	static {
		// states[0] = silence, ie. [0, 0, ...]
		for (int i = 1; i < states.length; ++i) {
			states[i][0] = 1.;  // some energy
			int lb = (i - 1) / 4;
			int ub = 4 + (i - 1) % 4;

			states[i][lb + 1] = 0.5;
			states[i][ub + 1] = 0.5;
		}
	}

	private static int min(double[] a) {
		int p = 0;
		double v = a[0];

		for (int i = 1; i < a.length; i++) {
			if (a[i] < v) {
				p = i;
				v = a[i];
			}
		}

		return p;
	}

	public static String decode(FrameSource fs, boolean fold) throws IOException {
		Logger logger = LogManager.getLogger(DtmfDecoder.class + Thread.currentThread().getName());

//		for (double[] s : states)
//			logger.info(Arrays.toString(s));


		double[] buf = new double [fs.getFrameSize()];
		assert(buf.length == states[0].length);

		final int S = states.length;

		Stack<int[]> trace = new Stack<>();
		double[] costs = new double [S];

		// start trace at silence (0)
		trace.push(new int [S]);

		// for debug purposes: maintain fwd-decisions
		StringBuilder fwd_hyp = new StringBuilder();

		// consume all frames in order...
		while (fs.read(buf)) {
			buf[0] = buf[0] / 4640.; // max energy for this synthesizer
			int[] mins = new int [S];

			// for debug puroses, find min in forward-pass
			int fw_p = 0;
			double fw_v = Double.MAX_VALUE;

			// compute distance to states, find min
			for (int i = 0; i < S; i++) {
				double d = Distances.euclidean(buf, states[i]);

				// find the minimum; analogy: substitute with what letter?
				int p = 0;
				double v = costs[0] + d;
				for (int j = 1; j < S; ++j) {
					if (costs[j] + d < v) {
						p = j;
						v = costs[j] + d;
					}
				}

				mins[i] = p;
				costs[i] = v;

				if (v < fw_v)
					fw_p = p;
			}

			fwd_hyp.append(labels[fw_p]);

//			logger.info(Arrays.toString(dists));
//			logger.info(Arrays.toString(costs));
//			logger.info(Arrays.toString(mins));

			trace.push(mins);
		}

		logger.info("forward pass: " + fwd_hyp);

		// do backtrace
		StringBuilder sb = new StringBuilder();

		int i = min(costs);
		sb.append(labels[i]);
		while (trace.size() > 0) {
			i = trace.pop()[i];
			sb.append(labels[i]);
		}

		String seq = sb.reverse().toString();

		if (fold) {
			sb = new StringBuilder();
			char c = 0;
			for (char z : seq.toCharArray()) {
				if (c != z) {
					sb.append(z);
					c = z;
				}
			}
			seq = sb.toString().replaceAll("" + labels[0], "");
		}

		return seq;
	}

	public static void main(String[] args) throws UnsupportedAudioFileException, MalformedParameterStringException, IOException {
		String sWindow = "hamm,25,10";
		String inFile = args[0];

		AudioSource as = new AudioFileReader(inFile, RawAudioFormat.create("f:"+inFile), true);
		Window w = Window.create(as, sWindow);
		FFT fft = new FFT(w);
		DTMF fs = new DTMF(fft);

		String seq = decode(fs, true);

		System.out.println(seq);
	}
}
