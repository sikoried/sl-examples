package com.github.sikoried.sl.iw;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

public class Classifier {
	private static Logger logger = LogManager.getLogger(Classifier.class);

	public static void main(String[] args) throws ConfigurationException {
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
		// 2. For each feature file, align each of the models
		// 3. Normalize the scores to a soft-max (to get probabilities for each class)
		// 4. Output lines of "<file> <best-class> <class-scores ...>"
	}
}
