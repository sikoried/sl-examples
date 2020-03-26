package com.github.sikoried.sl.iw;

import com.github.sikoried.jstk.exceptions.AlignmentException;
import com.github.sikoried.jstk.io.FrameInputStream;
import com.github.sikoried.jstk.io.FrameReader;
import com.github.sikoried.jstk.io.FrameSource;
import com.github.sikoried.jstk.stat.Mixture;
import com.github.sikoried.jstk.stat.hmm.Alignment;
import com.github.sikoried.jstk.stat.hmm.CState;
import com.github.sikoried.jstk.stat.hmm.Hmm;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import java.io.*;
import java.util.*;
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
		int num_densities = config.getInt("iw.densities", 3);

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
		Map<String, Hmm> mdls = new HashMap<>();
		Mixture mix = new Mixture(24, num_densities, true);
		for (String c : classes) {
			Hmm hmm = new Hmm(Integer.parseInt(c), (short) num_states, new CState(mix));
			hmm.setTransitions(Hmm.Topology.LINEAR);
			mdls.put(c, hmm);
		}


		// 1. compute an initial estimate, by creating linear alignments for each file and class; save
		//    the initial estimates as <name>.mdl.0
		mdls.values().forEach(Hmm::init);

		List<Pair<String, List<double[]>>> samples = new LinkedList<>();
		BufferedReader br = new BufferedReader(new FileReader(new File(listf)));
		br.lines().forEach(line -> {
			try {
				String[] p = line.split("_");
				String c = p[0];

				FrameSource fs = new FrameInputStream(new File(ft_dir, line));

				List<double[]> xs = new LinkedList<>();
				double[] buf = new double [fs.getFrameSize()];
				while (fs.read(buf))
					xs.add(buf.clone());

				samples.add(Pair.of(c, xs));
			} catch (IOException e) {
				e.printStackTrace();
			}
		});

		samples.forEach(p -> {
			try {
				Hmm hmm = mdls.get(p.getKey());
				Alignment ali = new Alignment(hmm, p.getValue());
				ali.forceLinearAlignment();
				hmm.incrementVT(ali);
			} catch (AlignmentException e) {
				e.printStackTrace();
			}
		});

		mdls.values().forEach(Hmm::reestimate);
		mdls.values().forEach(Hmm::discard);

		// write models to disk.
		for (Map.Entry<String, Hmm> e : mdls.entrySet()) {
			FileOutputStream fos = new FileOutputStream(new File(mdl_dir, e.getKey() + ".mdl.0"));
			e.getValue().write(fos);
			fos.close();
		}

		List<Pair<String, Alignment>> alis = new LinkedList<>();
		for (int it = 1; it <= num_iters; ++it) {
			logger.info("Iteration " + it);
			mdls.values().forEach(Hmm::init);

			if (realign.contains(it)) {
				logger.info("Re-aligning");
				alis.clear();
				samples.forEach(p -> {
					try {
						Hmm hmm = mdls.get(p.getKey());
						Alignment ali = new Alignment(hmm, p.getValue());

						ali.forcedAlignment();
						alis.add(Pair.of(p.getKey(), ali));
					} catch (AlignmentException e) {
						e.printStackTrace();
					}
				});
			}

			logger.info("Accumulating");
			alis.forEach(a -> {
				String cl = a.getKey();
				Alignment ali = a.getValue();
				mdls.get(cl).incrementVT(ali);
			});

			logger.info("Re-estimating");
			mdls.values().forEach(Hmm::reestimate);
			mdls.values().forEach(Hmm::discard);

			for (Map.Entry<String, Hmm> e : mdls.entrySet()) {
				FileOutputStream fos = new FileOutputStream(new File(mdl_dir, e.getKey() + ".mdl." + it));
				e.getValue().write(fos);
				fos.close();
			}

		}

	}
}
