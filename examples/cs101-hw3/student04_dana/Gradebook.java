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

    /* Number of marks above the bar. */
    public int countAbove(int bar)
    {
        int hits = 0;
        for (int mark : scores) {
            if (mark > bar) { hits++; }
        }
        return hits;
    }

    public double median()
    {
        List<Integer> ordered = new ArrayList<>(scores);
        Collections.sort(ordered);
        int mid = ordered.size() / 2;
        if (ordered.size() % 2 == 1) { return ordered.get(mid); }
        return (ordered.get(mid - 1) + ordered.get(mid)) / 2.0;
    }

    public int highest()
    {
        int top = scores.get(0);
        for (int k = 1; k < scores.size(); k++) {
            if (scores.get(k) > top) { top = scores.get(k); }
        }
        return top;
    }

    public double average()
    {
        int sum = 0;
        for (int mark : scores) { sum += mark; }
        return scores.isEmpty() ? 0.0 : (double) sum / scores.size();
    }
}
