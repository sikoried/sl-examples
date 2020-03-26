package com.github.sikoried.sl.iw;

import com.github.sikoried.jstk.arch.Codebook;
import com.github.sikoried.jstk.exceptions.AlignmentException;
import com.github.sikoried.jstk.io.FrameInputStream;
import com.github.sikoried.jstk.io.FrameSource;
import com.github.sikoried.jstk.stat.hmm.Alignment;
import com.github.sikoried.jstk.stat.hmm.Hmm;
import com.github.sikoried.jstk.util.Arithmetics;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.*;

public class Classifier {
	private static Logger logger = LogManager.getLogger(Classifier.class);

	public static void main(String[] args) throws ConfigurationException, FileNotFoundException, IOException {
		if (args.length != 1) {
			logger.error("Usage: Trainer config.properties");
			System.exit(0);
		}

		Configurations configs = new Configurations();

		Configuration config = configs.properties(new File(args[0]));

		String listf = config.getString("iw.list");

		// class names (will be `<name>.<iter>.mdl` for intermediate and `<name>.mdl` for final
		String[] classes = config.getString("iw.classes").split(",\\s*");

		// IO prefixes
		String mdl_dir = config.getString("iw.mdldir", "mdl");
		String ft_dir = config.getString("iw.ftdir", "ft");

		// SCState: codebook, if any
		String codebook = config.getString("iw.codebook", null);

		// TODO
		// 1. Load the model files (and codebook)
		Map<String, Hmm> mdls = new HashMap<>();

		for (String cl : classes) {
			Hmm hmm = new Hmm(new FileInputStream(new File(mdl_dir, cl + ".mdl.20")), null);
			mdls.put(cl, hmm);
		}

		BufferedReader br = new BufferedReader(new FileReader(new File(listf)));
		br.lines().forEach(line -> {
			try {
				String[] p = line.split("_");
				String correct = p[0];

				FrameSource fs = new FrameInputStream(new File(ft_dir, line));

				List<double[]> xs = new LinkedList<>();
				double[] buf = new double [fs.getFrameSize()];
				while (fs.read(buf))
					xs.add(buf.clone());

				double[] scores = new double [classes.length];
				String[] names = new String[classes.length];
				int i = 0;
				for (Map.Entry<String, Hmm> e : mdls.entrySet()) {
					Alignment ali = new Alignment(e.getValue(), xs);
					scores[i] = ali.forcedAlignment();
					names[i++] = e.getKey();
				}

				Arithmetics.makesumto1(scores);
				int max = Arithmetics.maxi(scores);

				logger.info(line + " " + names[max] + " " + Arrays.toString(scores)
						+ " " + Arrays.toString(names));

			} catch (IOException e) {
				e.printStackTrace();
			} catch (AlignmentException e) {
				e.printStackTrace();
			}
		});

		// 2. For each feature file, align each of the models
		// 3. Normalize the scores to a soft-max (to get probabilities for each class)
		// 4. Output lines of "<file> <best-class> <class-scores ...>"
	}
}
