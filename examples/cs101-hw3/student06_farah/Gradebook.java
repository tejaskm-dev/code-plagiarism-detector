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

    private int sumOf(List<Integer> values) {
        int total = 0;
        for (int value : values) {
            total += value;
        }
        return total;
    }

    private int maxOf(List<Integer> values) {
        int largest = values.get(0);
        for (int value : values) {
            if (value > largest) {
                largest = value;
            }
        }
        return largest;
    }

    public double average() {
        return scores.isEmpty() ? 0.0 : (double) sumOf(scores) / scores.size();
    }

    public int highest() {
        return maxOf(scores);
    }

    public double median() {
        List<Integer> working = new ArrayList<>(scores);
        Collections.sort(working);
        int centre = working.size() / 2;
        boolean odd = working.size() % 2 != 0;
        return odd ? working.get(centre) : (working.get(centre - 1) + working.get(centre)) / 2.0;
    }

    public int countAbove(int limit) {
        List<Integer> keepers = new ArrayList<>();
        for (int value : scores) {
            if (value > limit) {
                keepers.add(value);
            }
        }
        return keepers.size();
    }
}
