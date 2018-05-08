package com.github.sikoried.sl.iw;

import com.github.sikoried.jstk.exceptions.AlignmentException;
import com.github.sikoried.jstk.io.FrameInputStream;
import com.github.sikoried.jstk.io.FrameSource;
import com.github.sikoried.jstk.stat.*;
import com.github.sikoried.jstk.stat.hmm.*;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class Trainer {
	private static Logger logger = LogManager.getLogger(Trainer.class);
	public static void main(String[] args) throws ConfigurationException, IOException, AlignmentException, ClassNotFoundException {
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

		// codebook laden, sofern vorhanden
		Mixture shared = (codebook == null
			? null
			: new Mixture(new FileInputStream(codebook)));

		// template state erstellen
		State templ = (codebook == null
				? new CState(new Mixture(24, num_densities, true))
				: new SCState(shared));

		// HMMs anlegen
		Map<String, Hmm> models = new TreeMap<>();
		for (String c : classes) {
			Hmm hmm = new Hmm(Integer.parseInt(c), (short) num_states, templ);

			// Topologie waehlen
			hmm.setTransitions(Hmm.Topology.LINEAR);

			models.put(c, hmm);
		}


		// cache dataset
		List<Pair<String, List<double[]>>> data = new LinkedList<>();
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

			data.add(Pair.of(cl, samples));
		}


		// 1. compute an initial estimate, by creating linear alignments for each file and class; save
		//    the initial estimates as <name>.mdl.0

		// initialize the models
		MleMixtureAccumulator shared_accumulator = (shared == null
			? null
			: new MleMixtureAccumulator(24, shared.nd, DensityDiagonal.class));

		for (Hmm hmm : models.values()) {
			hmm.init();

			// must set the shared accumulator (a bit of a pain)
			if (shared != null) {
				for (State s : hmm.s)
					if (s instanceof SCState)
						((SCState) s).setSharedAccumulator(shared_accumulator);
			}
		}

		// compute forced-linear
		List<Alignment> cached_alignments = new LinkedList<>();
		for (Pair<String, List<double[]>> p : data) {
			Hmm target = models.get(p.getKey());
			Alignment ali = new Alignment(target, p.getValue());
			ali.forceLinearAlignment();
			target.incrementVT(ali);

			// cache
			cached_alignments.add(ali);
		}

		// pain...
		if (shared_accumulator != null) {
			Mixture old = shared.clone();
			MleMixtureAccumulator.MleUpdate(old,
					MleDensityAccumulator.MleOptions.pDefaultOptions,
					Density.Flags.fAllParams,
					shared_accumulator,
					shared);

			shared_accumulator.flush();

			shared.write(new FileOutputStream(new File(mdl_dir, "codebook.mdl.0")));
		}

		for (Map.Entry<String, Hmm> p : models.entrySet()) {
			Hmm hmm = p.getValue();

			hmm.reestimate();
			hmm.discard();

			hmm.write(new FileOutputStream(new File(mdl_dir, p.getKey() + ".mdl.0")));
		}


		for (int i = 1; i <= num_iters; i++) {
			for (Hmm hmm : models.values())
				hmm.init();

			if (realign.contains(i)) {
				logger.info("re-aligning data...");

				// discard old ali
				cached_alignments.clear();

				double avg = 0.0;
				for (Pair<String, List<double[]>> p : data) {
					Hmm target = models.get(p.getKey());
					Alignment ali = new Alignment(target, p.getValue());
					double obj = ali.forcedAlignment();

					// cache
					cached_alignments.add(ali);

					// debug
					avg += obj;
					logger.info("cl= " + p.getKey() + " obj=" + obj);
				}

				logger.info("average obj=" + (avg / cached_alignments.size()));
			}

			Iterator<Pair<String, List<double[]>>> it1 = data.iterator();
			Iterator<Alignment> it2 = cached_alignments.iterator();

			while (it1.hasNext()) {
				Pair<String, List<double[]>> p = it1.next();
				Alignment ali = it2.next();

				models.get(p.getKey()).incrementVT(ali);
			}

			for (Map.Entry<String, Hmm> p : models.entrySet()) {
				Hmm hmm = p.getValue();

				hmm.reestimate();
				hmm.discard();

				hmm.write(new FileOutputStream(new File(mdl_dir, p.getKey() + ".mdl." + i)));
			}

			// pain...
			if (shared_accumulator != null) {
				Mixture old = shared.clone();
				MleMixtureAccumulator.MleUpdate(old,
						MleDensityAccumulator.MleOptions.pDefaultOptions,
						Density.Flags.fAllParams,
						shared_accumulator,
						shared);

				shared_accumulator.flush();

				shared.write(new FileOutputStream(new File(mdl_dir, "codebook.mdl." + i)));
			}
		}

		if (codebook != null)
			Files.createSymbolicLink((new File(mdl_dir, "codebook.mdl")).toPath(),
					(new File("codebook.mdl." + num_iters)).toPath());

		for (String cl : classes)
			Files.createSymbolicLink((new File(mdl_dir, cl + ".mdl")).toPath(),
					(new File(cl + ".mdl." + num_iters)).toPath());

		// 2. reset the accumulators
		// 3. if (cur_iter in realign): compute the forced alignment
		// 4. accumulate according to alignments (eg. `accumulateVT`)
		// 5. re-estimate the parameters (`.reestimate()`), save current estimate
		// 6. if (cur_iter < num_iters): goto 2
	}
}
