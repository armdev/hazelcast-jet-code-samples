/*
 * Copyright (c) 2008-2017, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.hazelcast.jet.Distributed.Optional;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static java.awt.EventQueue.invokeLater;
import static java.util.Collections.emptyList;
import static java.util.Comparator.comparingDouble;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.reducing;
import static javax.swing.WindowConstants.EXIT_ON_CLOSE;

class SearchGui {
    private static final int WINDOW_X = 200;
    private static final int WINDOW_Y = 200;
    private static final int WINDOW_WIDTH = 300;
    private static final int WINDOW_HEIGHT = 350;

    private final Map<Long, String> docId2Name;
    private final Map<String, List<Entry<Long, Double>>> invertedIndex;
    private final Map<String, Boolean> stopwords;

    SearchGui(Map<Long, String> docId2Name,
              Map<String, List<Entry<Long, Double>>> invertedIndex,
              Map<String, Boolean> stopwords
    ) {
        this.docId2Name = docId2Name;
        this.invertedIndex = invertedIndex;
        this.stopwords = stopwords;
        invokeLater(this::buildFrame);
    }

    private void buildFrame() {
        final JFrame frame = new JFrame();
        frame.setBackground(Color.WHITE);
        frame.setDefaultCloseOperation(EXIT_ON_CLOSE);
        frame.setTitle("Hazelcast Jet TF-IDF");
        frame.setBounds(WINDOW_X, WINDOW_Y, WINDOW_WIDTH, WINDOW_HEIGHT);
        frame.setLayout(new BorderLayout());
        final JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout(10, 10));
        frame.add(mainPanel);
        final JTextField input = new JTextField();
        mainPanel.add(input, BorderLayout.NORTH);
        final JTextArea output = new JTextArea();
        mainPanel.add(output, BorderLayout.CENTER);
        input.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                invokeLater(() -> output.setText(search(input.getText().split("\\s+"))));
            }
        });
        frame.setVisible(true);
    }

    private String search(String... terms) {
        Map<Boolean, List<String>> byStopword = Arrays.stream(terms)
                                                      .map(String::toLowerCase)
                                                      .collect(partitioningBy(stopwords::containsKey));
        final List<String> searchTerms = byStopword.get(false);
        final String stopwordLine = byStopword.get(true).stream().collect(joining(" "));
        return (!stopwordLine.isEmpty() ? "Stopwords: " + stopwordLine + "\n--------\n" : "")
                + searchTerms.stream()
                             // retrieve all (docId, score) entries from the index
                             .flatMap(term -> Optional.ofNullable(invertedIndex.get(term))
                                                      .orElse(emptyList())
                                                      .stream())
                             // group by docId, accumulate the number of terms found in the document
                             // and the total TF-IDF score of the document
                             .collect(groupingBy(Entry<Long, Double>::getKey, reducing(
                                     new SimpleImmutableEntry<>(0L, 0.0),
                                     (acc, docScore) -> new SimpleImmutableEntry<>(
                                             acc.getKey() + 1, acc.getValue() + docScore.getValue()))))
                             .entrySet().stream()
                             // filter out documents which don't contain all the entered terms
                             .filter((Entry<?, Entry<Long, Double>> e) -> e.getValue().getKey() == searchTerms.size())
                             // sort documents by score, descending
                             .sorted(comparingDouble(
                                     (Entry<Long, Entry<Long, Double>> e) -> e.getValue().getValue()).reversed())
                             .map(e -> String.format("%5.2f %s",
                                     e.getValue().getValue() / terms.length, docId2Name.get(e.getKey())))
                             .collect(joining("\n"));
    }
}
