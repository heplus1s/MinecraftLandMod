package dev.jdguo.land.ui;

/** Shared drawing/hit-test geometry; coordinates use scaled GUI pixels. */
public record LandBoardLayout(int cell, int x, int y, int span) {
    public static LandBoardLayout fit(int width, int height, int span) {
        int cell = Math.max(2, Math.min(14, Math.min((width - 72) / span, (height - 144) / span)));
        return new LandBoardLayout(cell, (width - span * cell) / 2, 38, span);
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseY >= y && mouseX < x + span * cell && mouseY < y + span * cell;
    }
}
