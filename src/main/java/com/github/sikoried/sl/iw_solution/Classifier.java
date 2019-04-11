package com.github.sikoried.sl.iw_solution;

import com.github.sikoried.jstk.exceptions.AlignmentException;
import com.github.sikoried.jstk.io.FrameInputStream;
import com.github.sikoried.jstk.io.FrameSource;
import com.github.sikoried.jstk.stat.Mixture;
import com.github.sikoried.jstk.stat.hmm.*;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.*;

public class Classifier {
	private static Logger logger = LogManager.getLogger(Classifier.class);

	public static void main(String[] args) throws ConfigurationException, IOException, AlignmentException {
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

		// codebook laden, sofern vorhanden
		HashMap<Integer, Mixture> codebooks = new HashMap<>();
		Mixture shared = (codebook == null
				? null
				: new Mixture(new FileInputStream(new File(mdl_dir, codebook))));

		if (shared != null)
			codebooks.put(0, shared);

		// HMMs anlegen
		Map<String, Hmm> models = new TreeMap<>();
		for (String c : classes) {
			Hmm hmm = new Hmm(new FileInputStream(new File(mdl_dir, c + ".mdl")), codebooks);
			models.put(c, hmm);
		}

		BufferedReader br = new BufferedReader(new FileReader(listf));
		String line;
		while ((line = br.readLine()) != null) {
			String cl = line.split("_")[0];

			if (!models.containsKey(cl)) {
				logger.warn("Ignoring file with invalid class " + cl);
				continue;
			}

			FrameSource fs = new FrameInputStream(new File(ft_dir, line));
			List<double[]> samples = new LinkedList<>();
			double[] buf = new double [fs.getFrameSize()];
			while (fs.read(buf))
				samples.add(buf.clone());

			Map<String, Double> scores = new TreeMap<>();
			double max = -Double.MAX_VALUE;
			String max_class = null;
			for (Map.Entry<String, Hmm> p : models.entrySet()) {
				Alignment ali = new Alignment(p.getValue(), samples);
				double score = ali.decode();

				if (score > max) {
					max = score;
					max_class = p.getKey();
				}

				scores.put(p.getKey(), score);

				logger.info("target=" + cl + " this=" + p.getKey() + " score=" + score);
			}

			logger.info("target=" + cl + " actual=" + max_class);

			logger.info(scores);

			StringBuilder sb = new StringBuilder();
			sb.append(line).append(" ").append(cl).append(" ").append(max_class).append(" ");
			sb.append(Arrays.toString(scores.entrySet().stream().sorted((a, b) -> a.getKey().compareTo(b.getKey())).map(p -> p.getValue()).toArray()));
			System.out.println(sb.toString());
		}

		// 2. For each feature file, align each of the models
		// 3. Normalize the scores to a soft-max (to get probabilities for each class)
		// 4. Output lines of "<file> <best-class> <class-scores ...>"
	}
}
