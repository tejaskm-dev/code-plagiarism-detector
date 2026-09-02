package gradebook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Gradebook {

    private final List<Integer> scores = new ArrayList<>();
    private final String courseName;

    public Gradebook(String courseName) {
        this.courseName = courseName;
    }

    public void addScore(int score) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score out of range: " + score);
        }
        scores.add(score);
    }

    public String getCourseName() {
        return courseName;
    }

    public int count() {
        return scores.size();
    }

    public double average() {
        if (scores.isEmpty()) {
            return 0.0;
        }
        long accumulator = 0L;
        int seen = 0;
        while (seen < scores.size()) {
            accumulator += scores.get(seen);
            seen = seen + 1;
        }
        return (double) accumulator / seen;
    }

    public int highest() {
        int champion = Integer.MIN_VALUE;
        for (Integer candidate : scores) {
            champion = Math.max(champion, candidate);
        }
        return champion;
    }

    public double median() {
        Integer[] boxed = scores.toArray(new Integer[0]);
        java.util.Arrays.sort(boxed);
        int half = boxed.length >> 1;
        if ((boxed.length & 1) == 1) {
            return boxed[half];
        }
        return (boxed[half - 1] + boxed[half]) / 2.0;
    }

    public int countAbove(int limit) {
        int tally = 0;
        for (int position = 0; position < scores.size(); position++) {
            tally += scores.get(position) > limit ? 1 : 0;
        }
        return tally;
    }
}
