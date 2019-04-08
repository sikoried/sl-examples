package com.github.sikoried.sl.autocomplete;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class Generator {
    static Logger logger = LogManager.getLogger(Generator.class);

    /// Offer `n` suggestions on how to complete the given history
    static List<Pair<String, Double>> complete(NGramModel model, String[] w, int n) {
        logger.info("completing " + Arrays.toString(w));

        final int order = model.getOrder();
        final List<NGramModel.NGram> ngrams = model.getNgrams();

        // trim the history to min(len, order-1) since it will lead to misses
        if (w.length > order - 1)
            return complete(model, Arrays.copyOfRange(w, w.length - order + 1, w.length), n);

        List<Pair<String, Double>> suggestions = ngrams.stream()
                .filter(ng -> ng.w.length == w.length + 1)
                .filter(ng -> ng.startsWith(w))
                .sorted(Comparator.comparingDouble(NGramModel.NGram::getLogp).reversed())
                .limit(n)
                .map(ng -> Pair.of(ng.token(), ng.logp))
                .collect(Collectors.toList());

        // poop.
        if (suggestions.size() == 0) {
            String[] shorter = Arrays.copyOfRange(w, 1, w.length);
            logger.info("falling back to " + Arrays.toString(shorter));
            suggestions = complete(model, shorter, n);
        }

        return suggestions;
    }
}
