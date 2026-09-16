package com.integrityengine.ai;

import com.integrityengine.domain.CodeSubmission;
import com.integrityengine.tokenizer.ITokenizer;
import com.integrityengine.tokenizer.TokenizerFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Decides whether a file was written by a person or generated, without calling any model.
 *
 * <p>A random forest of 150 trees over the twenty-four measurements in
 * {@link StyleFeatures}, trained on a labelled corpus and serialised to
 * {@code ai-authorship-forest.txt}. Scoring is a few thousand comparisons: no network,
 * no key, no per-file cost, and the same answer every time it is asked.
 *
 * <p><b>It is allowed to say it does not know.</b> The forest returns the fraction of
 * trees voting "generated". Near 0.5 that vote is a coin toss dressed as a number, and
 * reporting it as a verdict would be the single most damaging thing this tool could do
 * — a student is on the other end of it. Scores inside {@link #UNSURE_BAND} of the
 * midpoint return {@link Verdict#UNSURE} instead, which is a real answer and should be
 * shown as one.
 *
 * <p><b>Measured performance</b> — see {@code docs/ai-detection-results.md} for the corpus,
 * the method, and the two corpus artifacts that had to be removed before the numbers
 * meant anything. On a held-out quarter of the corpus never used for tuning: 91.9%
 * accuracy if forced to choose on every file, and 96.6% on the 86% of files it was
 * confident enough to rule on. The second number is the one this class is built around,
 * and it rests on 116 decisions — treat it as "about 96%", not as a precise figure.
 *
 * <p><b>What it is not.</b> A tidy, well-taught student writes code that looks like
 * this model's "generated" class, because both are regular. The corpus's human half is
 * professional open-source code, not first-year coursework, so the score is evidence
 * for a conversation and never grounds for an accusation on its own.
 */
final class AiAuthorshipModel {

    /** Votes within this distance of 0.5 are reported as {@link Verdict#UNSURE}. */
    static final double UNSURE_BAND = 0.10;

    static final String MODEL_NAME = "stylometric-forest-v1";

    private static final String RESOURCE = "/ai-authorship-forest.txt";

    /** What the forest is willing to say about one file. */
    enum Verdict {
        LIKELY_GENERATED, LIKELY_HUMAN, UNSURE
    }

    /**
     * A tree flattened to preorder arrays. Held this way rather than as objects because
     * the whole forest is walked once per submission and the arrays stay in cache.
     */
    private record Tree(int[] feature, double[] threshold, int[] right, double[] leaf) {
    }

    private static final List<Tree> FOREST = load();

    private final TokenizerFactory factory;

    AiAuthorshipModel() {
        this(new TokenizerFactory());
    }

    AiAuthorshipModel(TokenizerFactory factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    /**
     * @return the fraction of trees voting "generated", or empty when the file is in a
     *     language with no tokenizer or is too small to measure
     */
    Optional<Double> score(CodeSubmission submission) {
        Objects.requireNonNull(submission, "submission");
        Optional<ITokenizer> tokenizer = factory.forFilename(submission.getFilename());
        if (tokenizer.isEmpty()) {
            return Optional.empty();
        }
        String source = submission.getSourceCode();
        if (source == null || source.isBlank()) {
            return Optional.empty();
        }
        if (tokenizer.get().tokenize(source).size() < StylometricExtractor.MINIMUM_TOKENS) {
            return Optional.empty();
        }
        double[] features = StyleFeatures.extract(source, tokenizer.get());
        double sum = 0;
        for (Tree tree : FOREST) {
            sum += walk(tree, features);
        }
        return Optional.of(sum / FOREST.size());
    }

    static Verdict verdict(double score) {
        if (score >= 0.5 + UNSURE_BAND) {
            return Verdict.LIKELY_GENERATED;
        }
        if (score <= 0.5 - UNSURE_BAND) {
            return Verdict.LIKELY_HUMAN;
        }
        return Verdict.UNSURE;
    }

    private static double walk(Tree tree, double[] features) {
        int node = 0;
        while (tree.feature()[node] >= 0) {
            node = features[tree.feature()[node]] <= tree.threshold()[node]
                    ? node + 1
                    : tree.right()[node];
        }
        return tree.leaf()[node];
    }

    // ------------------------------------------------------------------ loading

    private static List<Tree> load() {
        List<Tree> forest = new ArrayList<>();
        try (InputStream in = AiAuthorshipModel.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing model resource " + RESOURCE);
            }
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    forest.add(parse(line.trim().split(" ")));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + RESOURCE, e);
        }
        if (forest.isEmpty()) {
            throw new IllegalStateException("model resource " + RESOURCE + " held no trees");
        }
        return List.copyOf(forest);
    }

    /**
     * Rebuilds one preorder-serialised tree. The left child of a node always sits at the
     * next index, so only the right child needs recording; that is what the recursion
     * below returns.
     */
    private static Tree parse(String[] tokens) {
        int[] feature = new int[tokens.length];
        double[] threshold = new double[tokens.length];
        int[] right = new int[tokens.length];
        double[] leaf = new double[tokens.length];
        Cursor cursor = new Cursor();
        parseNode(tokens, cursor, feature, threshold, right, leaf);
        return new Tree(feature, threshold, right, leaf);
    }

    private static final class Cursor {
        int index;
    }

    private static int parseNode(String[] tokens, Cursor cursor,
                                 int[] feature, double[] threshold, int[] right, double[] leaf) {
        int self = cursor.index++;
        String token = tokens[self];
        if (token.charAt(0) == 'L') {
            feature[self] = -1;
            leaf[self] = Double.parseDouble(token.substring(1));
            return self;
        }
        int comma = token.indexOf(',');
        feature[self] = Integer.parseInt(token.substring(1, comma));
        threshold[self] = Double.parseDouble(token.substring(comma + 1));
        parseNode(tokens, cursor, feature, threshold, right, leaf);
        right[self] = cursor.index;
        parseNode(tokens, cursor, feature, threshold, right, leaf);
        return self;
    }
}
