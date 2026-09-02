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

    private List<Integer> sortedCopy() {
        List<Integer> copy = new ArrayList<>(scores);
        Collections.sort(copy);
        return copy;
    }

    public double average() {
        double running = 0;
        for (Integer value : sortedCopy()) {
            running = running + value;
        }
        return scores.isEmpty() ? 0.0 : running / scores.size();
    }

    public int highest() {
        List<Integer> ordered = sortedCopy();
        return ordered.get(ordered.size() - 1);
    }

    public double median() {
        List<Integer> ordered = sortedCopy();
        int n = ordered.size();
        return n % 2 == 1
                ? ordered.get(n / 2)
                : (ordered.get(n / 2 - 1) + ordered.get(n / 2)) / 2.0;
    }

    public int countAbove(int limit) {
        List<Integer> ordered = sortedCopy();
        int index = 0;
        while (index < ordered.size() && ordered.get(index) <= limit) {
            index++;
        }
        return ordered.size() - index;
    }
}
