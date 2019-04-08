package com.github.sikoried.sl.autocomplete;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class NGramModel {
	static Logger logger = LogManager.getLogger(NGramModel.class);

	public static class NGram {
		/// context; natural temporal order
		String[] w;

		/// log-prob
		double logp;

		/// backoff, also log-prob
		Double backoff;

		NGram(String[] w, double logp) {
			this(w, logp, null);
		}

		NGram(String[] w, double logp, Double backoff) {
			this.w = w;
			this.logp = logp;
			this.backoff = backoff;
		}

		/// factory function; expects arpa-style ngram line
		static NGram parse(String line) {
			String[] xs = line.split("\t");
			double logp = Double.parseDouble(xs[0]);
			String[] w= xs[1].split(" ");

			if (xs.length == 3)
				return new NGram(w, logp, Double.parseDouble(xs[2]));
			else
				return new NGram(w, logp);
		}

		double getLogp() {
			return logp;
		}

		Double getBackoff() {
			return backoff;
		}

		boolean hasBackoff() {
			return backoff != null;
		}

		int order() {
			return w.length;
		}

		/// the "history" part of the ngram
		String[] history() {
			return Arrays.copyOfRange(w, 0, w.length - 1);
		}

		/// the actual token
		String token() {
			return w[w.length - 1];
		}

		/// does w start with v? eg. "a b c" starts with "a b"
		boolean startsWith(String[] v) {
			if (w.length < v.length)
				return false;

			for (int i = 0; i < v.length; i++)
				if (!w[i].equals(v[i]))
					return false;

			return true;
		}

		/// does v (exactly) match w?
		boolean matches(String[] v) {
			if (w.length != v.length)
				return false;

			for (int i = 0; i < v.length; i++)
				if (!w[i].equals(v[i]))
					return false;

			return true;
		}

		/// does w end with v? z.b. "a b" ends with "x a b"
		boolean endsWith(String[] v) {
			if (w.length > v.length)
				return false;

			for (int i = 0; i < w.length; i++) {
				if (!w[w.length - 1 - i].equals(v[v.length - 1 - i]))
					return false;
			}

			return true;
		}

		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb
					.append(logp)
					.append('\t')
					.append(Arrays.stream(w).collect(Collectors.joining(" ")));
			if (backoff != null)
				sb.append('\t').append(backoff);

			return sb.toString();
		}
	}

	private int order;
	private List<NGram> ngrams;

	private NGramModel(List<NGram> ngrams, int order) {
		this.ngrams = ngrams;
		this.order = order;
	}

	List<NGram> getNgrams() { return ngrams; }
	int getOrder() { return order; }

	static NGramModel fromArpa(BufferedReader model) throws IOException {
		String l;
		List<NGram> ngrams = new LinkedList<>();
		int order = 0;
		while ((l = model.readLine()) != null) {
			if (l.startsWith("-")) {
				NGram ng = NGram.parse(l);
				if (ng.order() > order)
					order = ng.order();

				ngrams.add(ng);
			}
		}

		logger.info("Read " + ngrams.size() + " ngrams (order=" + order + ")");

		return new NGramModel(ngrams, order);
	}
}
