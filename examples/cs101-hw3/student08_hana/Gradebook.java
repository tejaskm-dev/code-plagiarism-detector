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

    private void requireData() {
        if (scores.isEmpty()) {
            throw new IllegalStateException("no scores recorded for " + courseName);
        }
    }

    public double average() {
        requireData();
        double total = 0;
        for (int index = 0; index < scores.size(); index++) {
            total += scores.get(index);
        }
        return total / scores.size();
    }

    public int highest() {
        requireData();
        int result = scores.get(0);
        for (int index = 1; index < scores.size(); index++) {
            int candidate = scores.get(index);
            if (candidate > result) {
                result = candidate;
            }
        }
        return result;
    }

    public double median() {
        requireData();
        List<Integer> ranked = new ArrayList<>(scores);
        Collections.sort(ranked);
        int size = ranked.size();
        if (size % 2 != 0) {
            return ranked.get(size / 2);
        }
        int lower = ranked.get(size / 2 - 1);
        int upper = ranked.get(size / 2);
        return (lower + upper) / 2.0;
    }

    public int countAbove(int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must not be negative");
        }
        int found = 0;
        for (int value : scores) {
            if (value > limit) {
                found = found + 1;
            }
        }
        return found;
    }
}
