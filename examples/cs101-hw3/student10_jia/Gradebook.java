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
        if (scores.size() == 0) return 0;
        int t = 0;
        for (int s : scores) t += s;
        return (double) t / scores.size();
    }

    public int highest() {
        int h = scores.get(0);
        for (int s : scores) if (s > h) h = s;
        return h;
    }

    public double median() {
        List<Integer> c = new ArrayList<>(scores);
        Collections.sort(c);
        int m = c.size() / 2;
        return c.size() % 2 == 1 ? c.get(m) : (c.get(m - 1) + c.get(m)) / 2.0;
    }

    public int countAbove(int l) {
        int n = 0;
        for (int s : scores) if (s > l) n++;
        return n;
    }
}
