package com.github.sikoried.sl.autocomplete;

import com.github.sikoried.sl.autocorrect.Distances;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AutoCorrectContext {
    public List<Pair<String, Double>> suggest(NGramModel model, String[] history, String query, int maxEdit, int numSuggest) {
        // use <s> for beginning-of-sentence, no OOV allowed
        assert history.length > 0;
        assert model.getVocab().contains(query);

        List<Pair<String, Double>> li = new LinkedList<>();

        // exact hit, no suggestion
        if (model.getVocab().contains(query)) {
            li.add(Pair.of(query, 0.0));
            return li;
        }

        return model.getNgrams().stream()
                .filter(n -> n.matchHistory(history))  // only n-grams matching the history
                .filter(n -> Distances.edit(query, n.token()) < maxEdit)  // only words that are close enough
                .map(n -> Pair.of(n.token(), n.logp))
                .sorted()
                .limit(numSuggest)
                .collect(Collectors.toList());
    }
}
