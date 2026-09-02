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
        return scores.stream().mapToInt(Integer::intValue).average().orElse(0.0);
    }

    public int highest() {
        return scores.stream().mapToInt(Integer::intValue).max().orElseThrow();
    }

    public double median() {
        int[] ordered = scores.stream().mapToInt(Integer::intValue).sorted().toArray();
        if (ordered.length == 0) {
            return 0.0;
        }
        int mid = ordered.length / 2;
        return ordered.length % 2 == 1 ? ordered[mid] : (ordered[mid - 1] + ordered[mid]) / 2.0;
    }

    public int countAbove(int limit) {
        return (int) scores.stream().filter(value -> value > limit).count();
    }
}
