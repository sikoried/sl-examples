package com.github.sikoried.sl.iw;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Trainer {
	private static Logger logger = LogManager.getLogger(Trainer.class);
	public static void main(String[] args) throws ConfigurationException, IOException {
		if (args.length != 1) {
			logger.error("Usage: Trainer config.properties");
			System.exit(0);
		}

		Configurations configs = new Configurations();

		Configuration config = configs.properties(new File(args[0]));

		// training list
		String listf = config.getString("iw.list");

		// number of overall iterations
		int num_iters = config.getInt("iw.iterations", 4);

		// iterations when to re-align the data
		Set<Integer> realign = Arrays.stream(config.getString("iw.realign").split(",\\s*"))
				.map(Integer::parseInt)
				.collect(Collectors.toSet());

		// number of states per class/model
		int num_states = config.getInt("iw.states", 4);

		// class names (will be `<name>.<iter>.mdl` for intermediate and `<name>.mdl` for final
		String[] classes = config.getString("iw.classes").split(",\\s*");

		// IO prefixes
		String mdl_dir = config.getString("iw.mdldir", "mdl");
		String ft_dir = config.getString("iw.ftdir", "ft");

		// SCState: codebook, if any
		String codebook = config.getString("iw.codebook", null);

		// CState: number of densities per-state
		int num_densities = config.getInt("iw.densities", 1);

		logger.info("classes = " + Arrays.toString(classes));
		logger.info("list = " + listf);
		logger.info("num_iters = " + num_iters);
		logger.info("realign = " + realign.toString());
		logger.info("num_states = " + num_states);
		logger.info("mdl_dir = " + mdl_dir);
		logger.info("ft_dir = " + ft_dir);
		logger.info("codebook = " + codebook);
		logger.info("num_densities = " + num_densities);

		// TODO
		// 0. for each class, allocate a HMM; use SCState if codebook is specified, CState otherwise
		// 1. compute an initial estimate, by creating linear alignments for each file and class; save
		//    the initial estimates as <name>.mdl.0
		// 2. reset the accumulators
		// 3. if (cur_iter in realign): compute the forced alignment
		// 4. accumulate according to alignments (eg. `accumulateVT`)
		// 5. re-estimate the parameters (`.reestimate()`), save current estimate
		// 6. if (cur_iter < num_iters): goto 2
	}
}
