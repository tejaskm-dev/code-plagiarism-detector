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

    // Works out the mean of everything added so far.
    public double average() {
        int total = 0;
        for (int score : scores) {
            total += score;
        }
        return scores.isEmpty() ? 0.0 : (double) total / scores.size();
    }

    public int highest() {
        int best = scores.get(0);
        for (int i = 1; i < scores.size(); i++) {
            if (scores.get(i) > best) {
                best = scores.get(i);
            }
        }
        return best;
    }

    public double median() {
        List<Integer> sorted = new ArrayList<>(scores);
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }

    // How many students beat the given mark?
    public int countAbove(int limit) {
        int matches = 0;
        for (int score : scores) {
            if (score > limit) {
                matches++;
            }
        }
        return matches;
    }
}
