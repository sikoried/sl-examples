package com.github.sikoried.sl.live;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

public class ED {
    private static double min(double d1, double... ds) {
        double m = d1;
        for (double d : ds)
            m = Math.min(m, d);
        return m;
    }

    public static double edit(String a, String b,
                              double cr, double ci, double cd) {
        int la = a.length();
        int lb = b.length();
        double D[][] = new double [la+1][lb+1];

        for (int i = 1; i <= la; i++) D[i][0] = i*ci;
        for (int j = 1; j <= lb; j++) D[0][j] = j*cd;

        for (int i = 1; i <= la; i++) {
            for (int j = 1; j <= lb; j++) {
                double d = (a.charAt(i-1) == b.charAt(j-1)
                        ? 0
                        : cr);

                D[i][j] = min(D[i-1][j-1]+d,
                        D[i-1][j]+cd,
                        D[i][j-1]+ci);
            }
        }

        return D[la][lb];
    }

    public static List<Pair<String, Long>> loadVocab() throws IOException, URISyntaxException {
        URL zipUrl = ED.class.getResource("/count_1w.txt.zip");
        File zipFile = new File(zipUrl.toURI());
        ZipFile zip = new ZipFile(zipFile);
        BufferedReader br = new BufferedReader(new InputStreamReader(
                zip.getInputStream(zip.getEntry("count_1w.txt"))));

        return br.lines().map(l -> l.split("\\s+"))
                .map(a -> Pair.of(a[0], Long.parseLong(a[1])))
                .sorted(Comparator.comparing(Pair::getLeft))
                .collect(Collectors.toList());
    }

    public static List<Triple<String, Double, Long>> suggest(
            List<Pair<String, Long>> vocab,
            String cand,
            int n) {
        return vocab.stream()
                .map(p -> Triple.of(p.getLeft(),
                    edit(cand, p.getLeft(), 1, 1, 1),
                    p.getRight()))
                .sorted(Comparator.comparing(Triple<String, Double, Long>::getMiddle)
                    .thenComparing(Comparator.comparing(Triple<String, Double, Long>::getRight).reversed()))
                .limit(n)
                .collect(Collectors.toList());
    }

    public static List<Pair<String, Double>> suggest2(
            List<Pair<String, Long>> vocab,
            String cand,
            int n) {
        double offs = Math.log(vocab.stream().mapToLong(Pair::getRight).sum());
        return vocab.stream()
                .map(p -> Pair.of(p.getLeft(),
                        -Math.log(1. + Math.min(6.0, edit(cand, p.getLeft(), 1, 1, 1)))
                                + (Math.log(p.getRight()) - offs) * 0.05))
                .sorted(Comparator.comparing(Pair<String, Double>::getRight).reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    public static void main(String[] args) throws IOException, URISyntaxException {
        Scanner in = new Scanner(System.in);

        List<Pair<String, Long>> vocab = ED.loadVocab();

        String s;
        while ((s = in.nextLine()) != null) {
//            ED.suggest(vocab, s, 5)
//                    .forEach(System.out::println);
            ED.suggest2(vocab, s, 5)
                    .forEach(System.out::println);
        }
    }
}
