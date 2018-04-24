package com.github.sikoried.sl.autocomplete;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

class AutoCompleteTest {
	@Test
	void testAutoCompleteWerther() throws IOException {
		ClassLoader classLoader = AutoCompleteTest.class.getClassLoader();
		File file = new File(classLoader.getResource("ngram-models/werther.5g.gz").getFile());
		testAutoComplete(file);
	}

	@Test
	void testAutoCompleteWSJ() throws IOException {
		ClassLoader classLoader = AutoCompleteTest.class.getClassLoader();
		File file = new File(classLoader.getResource("ngram-models/wsj-tg.3g.gz").getFile());
		testAutoComplete(file);
	}

	@Test
	void testAutoCompleteFaust() throws IOException {
		ClassLoader classLoader = AutoCompleteTest.class.getClassLoader();
		File file = new File(classLoader.getResource("ngram-models/faust.4g.gz").getFile());
		testAutoComplete(file);
	}

	@Test
	void testAutoCompleteTheses() throws IOException {
		ClassLoader classLoader = AutoCompleteTest.class.getClassLoader();
		File file = new File(classLoader.getResource("ngram-models/hsro-theses.3g.gz").getFile());
		InputStream fileStream = new FileInputStream(file);
		InputStream gzipStream = new GZIPInputStream(fileStream);
		NGramModel ac = NGramModel.fromArpa(new BufferedReader(new InputStreamReader(gzipStream)));

		List<String> sents = new LinkedList<>();
		final int n = 10;
		final int len = 12;
		final int choices = 25;
		for (int i = 0; i < n; i++)
			sents.add(sample(ac, len, choices));

		for (String s : sents)
			System.out.println(s);
	}

	private void testAutoComplete(File file) throws IOException {
		InputStream fileStream = new FileInputStream(file);
		InputStream gzipStream = new GZIPInputStream(fileStream);
		NGramModel ac = NGramModel.fromArpa(new BufferedReader(new InputStreamReader(gzipStream)));

		List<String> sents = new LinkedList<>();
		for (int n = 0; n < 15; n++)
			sents.add(sample(ac, 15, 20));

		for (String s : sents)
			System.out.println(s);
	}

	private String sample(NGramModel ac, int len, int n) {
		Random r = new Random();
		List<String> sent = new LinkedList<>();

		// start with beginning-of-sentence symbol
		sent.add("<s>");
		for (int i = 0; i < len; i++) {
			List<Pair<String, Double>> suggestions = ac.complete(sent.toArray(new String[0]), n);
			System.out.println(suggestions);

			// make random choice
			Pair<String, Double> choice = suggestions.get(r.nextInt(suggestions.size()));

			System.out.println(choice);
			sent.add(choice.getLeft());

			if (choice.getLeft().equals("</s>")) {
				System.out.println("premature end-of-sentence");
				break;
			}
		}

		return sent.stream().collect(Collectors.joining(" "));
	}
}