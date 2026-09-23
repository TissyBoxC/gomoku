package io.github.tissyboxc.gomoku.ui;

import io.github.tissyboxc.gomoku.core.Board;
import io.github.tissyboxc.gomoku.core.GameSession;
import io.github.tissyboxc.gomoku.model.Move;
import io.github.tissyboxc.gomoku.model.Point;
import io.github.tissyboxc.gomoku.model.Stone;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * 自绘棋盘组件，负责棋盘、棋子、胜负连线和清屏动画的绘制。
 */
public final class BoardPanel extends JPanel {
    private static final int MARGIN = 36;
    private static final int STAR_SIZE = 5;
    private static final Color WIN_COLOR = new Color(0xE5, 0x3F, 0x35);

    private GameSession session;
    private BoardTheme theme = BoardTheme.WOOD;
    private Consumer<Point> moveListener;
    private List<Point> winningLine = List.of();
    private boolean interactive = true;
    private float clearProgress = 1.0f;
    private Timer clearTimer;

    public BoardPanel(GameSession session) {
        this.session = session;
        setPreferredSize(new Dimension(680, 680));
        setMinimumSize(new Dimension(480, 480));
        setOpaque(true);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                handleMouseClick(event);
            }
        });
    }

    public void setMoveListener(Consumer<Point> moveListener) {
        this.moveListener = moveListener;
    }

    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
        setCursor(interactive
                ? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.CROSSHAIR_CURSOR)
                : java.awt.Cursor.getDefaultCursor());
    }

    public void setTheme(BoardTheme theme) {
        if (theme != null) {
            this.theme = theme;
            repaint();
        }
    }

    public void setWinningLine(List<Point> winningLine) {
        this.winningLine = winningLine == null ? List.of() : List.copyOf(winningLine);
        repaint();
    }

    /**
     * 清屏时播放一个短促的横向扫光动画。
     */
    public void playClearAnimation() {
        if (clearTimer != null && clearTimer.isRunning()) {
            clearTimer.stop();
        }
        clearProgress = 0.0f;
        clearTimer = new Timer(18, event -> {
            clearProgress += 0.055f;
            if (clearProgress >= 1.0f) {
                clearProgress = 1.0f;
                clearTimer.stop();
            }
            repaint();
        });
        clearTimer.start();
    }

    private void handleMouseClick(MouseEvent event) {
        if (!interactive || moveListener == null || session == null) {
            return;
        }
        Geometry geometry = geometry();
        int col = Math.round((event.getX() - geometry.startX()) / (float) geometry.cellSize());
        int row = Math.round((event.getY() - geometry.startY()) / (float) geometry.cellSize());
        if (!Board.isValid(row, col)) {
            return;
        }
        int pointX = geometry.startX() + col * geometry.cellSize();
        int pointY = geometry.startY() + row * geometry.cellSize();
        int tolerance = Math.max(12, geometry.cellSize() / 2);
        if (Math.abs(event.getX() - pointX) <= tolerance
                && Math.abs(event.getY() - pointY) <= tolerance) {
            moveListener.accept(new Point(row, col));
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2 = (Graphics2D) graphics.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            Geometry geometry = geometry();
            paintBoardSurface(g2, geometry);
            paintGrid(g2, geometry);
            paintStones(g2, geometry);
            paintWinningLine(g2, geometry);
            paintClearAnimation(g2, geometry);
        } finally {
            g2.dispose();
        }
    }

    private void paintBoardSurface(Graphics2D g2, Geometry geometry) {
        int inset = 16;
        int boardX = geometry.startX() - MARGIN + inset;
        int boardY = geometry.startY() - MARGIN + inset;
        int boardSize = geometry.boardPixelSize() + (MARGIN - inset) * 2;
        g2.setPaint(new GradientPaint(
                boardX,
                boardY,
                theme.getBoardColor().brighter(),
                boardX + boardSize,
                boardY + boardSize,
                theme.getBoardColor().darker()));
        g2.fill(new RoundRectangle2D.Double(boardX, boardY, boardSize, boardSize, 18, 18));
        g2.setColor(theme.getBorderColor());
        g2.setStroke(new BasicStroke(3f));
        g2.draw(new RoundRectangle2D.Double(boardX, boardY, boardSize, boardSize, 18, 18));
    }

    private void paintGrid(Graphics2D g2, Geometry geometry) {
        g2.setColor(theme.getLineColor());
        g2.setStroke(new BasicStroke(1.25f));
        int startX = geometry.startX();
        int startY = geometry.startY();
        int endX = startX + geometry.boardPixelSize();
        int endY = startY + geometry.boardPixelSize();

        for (int index = 0; index < Board.SIZE; index++) {
            int x = startX + index * geometry.cellSize();
            int y = startY + index * geometry.cellSize();
            g2.drawLine(x, startY, x, endY);
            g2.drawLine(startX, y, endX, y);
        }

        int[][] stars = {
                {3, 3}, {3, 11}, {7, 7}, {11, 3}, {11, 11}
        };
        for (int[] star : stars) {
            int x = startX + star[1] * geometry.cellSize();
            int y = startY + star[0] * geometry.cellSize();
            g2.fill(new Ellipse2D.Double(
                    x - STAR_SIZE / 2.0,
                    y - STAR_SIZE / 2.0,
                    STAR_SIZE,
                    STAR_SIZE));
        }

        g2.setFont(getFont().deriveFont(11f));
        for (int index = 0; index < Board.SIZE; index++) {
            String columnName = String.valueOf((char) ('A' + index));
            String rowName = Integer.toString(index + 1);
            int x = startX + index * geometry.cellSize();
            int y = startY + index * geometry.cellSize();
            g2.setColor(theme.getLabelColor());
            g2.drawString(columnName, x - 4, startY - 12);
            g2.drawString(rowName, startX - 24, y + 4);
        }
    }

    private void paintStones(Graphics2D g2, Geometry geometry) {
        if (session == null) {
            return;
        }
        Stone[][] grid = session.getBoard().snapshot();
        int stoneSize = Math.max(18, (int) (geometry.cellSize() * 0.86));
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                if (grid[row][col] == Stone.EMPTY) {
                    continue;
                }
                int centerX = geometry.startX() + col * geometry.cellSize();
                int centerY = geometry.startY() + row * geometry.cellSize();
                paintStone(g2, centerX, centerY, stoneSize, grid[row][col]);
            }
        }

        Move lastMove = session.getLastMove();
        if (lastMove != null) {
            int centerX = geometry.startX() + lastMove.col() * geometry.cellSize();
            int centerY = geometry.startY() + lastMove.row() * geometry.cellSize();
            g2.setColor(new Color(0xE5, 0x3F, 0x35));
            g2.fill(new Ellipse2D.Double(centerX - 4, centerY - 4, 8, 8));
        }
    }

    private void paintStone(Graphics2D g2, int centerX, int centerY, int size, Stone stone) {
        double x = centerX - size / 2.0;
        double y = centerY - size / 2.0;
        g2.setColor(new Color(0, 0, 0, 65));
        g2.fill(new Ellipse2D.Double(x + 3, y + 4, size, size));

        if (stone == Stone.BLACK) {
            g2.setPaint(new RadialGradientPaint(
                    centerX - size * 0.25f,
                    centerY - size * 0.28f,
                    Math.max(1f, size * 0.72f),
                    new float[]{0f, 0.45f, 1f},
                    new Color[]{new Color(0x6E, 0x6E, 0x6E), new Color(0x24, 0x24, 0x24), Color.BLACK}));
        } else {
            g2.setPaint(new RadialGradientPaint(
                    centerX - size * 0.25f,
                    centerY - size * 0.28f,
                    Math.max(1f, size * 0.72f),
                    new float[]{0f, 0.55f, 1f},
                    new Color[]{Color.WHITE, new Color(0xF2, 0xF2, 0xF2), new Color(0xC9, 0xC9, 0xC9)}));
        }
        g2.fill(new Ellipse2D.Double(x, y, size, size));
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(stone == Stone.BLACK ? new Color(0x11, 0x11, 0x11) : new Color(0xB0, 0xB0, 0xB0));
        g2.draw(new Ellipse2D.Double(x, y, size, size));
    }

    private void paintWinningLine(Graphics2D g2, Geometry geometry) {
        if (winningLine.size() < 2) {
            return;
        }
        g2.setColor(WIN_COLOR);
        g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Point first = winningLine.get(0);
        Point last = winningLine.get(winningLine.size() - 1);
        g2.drawLine(
                geometry.startX() + first.col() * geometry.cellSize(),
                geometry.startY() + first.row() * geometry.cellSize(),
                geometry.startX() + last.col() * geometry.cellSize(),
                geometry.startY() + last.row() * geometry.cellSize());
    }

    private void paintClearAnimation(Graphics2D g2, Geometry geometry) {
        if (clearProgress >= 1.0f) {
            return;
        }
        int boardX = geometry.startX() - MARGIN + 16;
        int boardY = geometry.startY() - MARGIN + 16;
        int boardSize = geometry.boardPixelSize() + (MARGIN - 16) * 2;
        int sweepWidth = Math.max(80, boardSize / 4);
        int sweepX = boardX - sweepWidth + (int) ((boardSize + sweepWidth * 2) * clearProgress);
        g2.setColor(new Color(255, 255, 255, 95));
        g2.fillRect(sweepX, boardY, sweepWidth, boardSize);
        float alpha = (float) (0.18 * Math.sin(Math.PI * clearProgress));
        g2.setColor(new Color(1f, 1f, 1f, Math.max(0f, alpha)));
        g2.fill(new RoundRectangle2D.Double(boardX, boardY, boardSize, boardSize, 18, 18));
    }

    private Geometry geometry() {
        int usableWidth = Math.max(1, getWidth() - MARGIN * 2);
        int usableHeight = Math.max(1, getHeight() - MARGIN * 2);
        int cellSize = Math.max(16, Math.min(usableWidth, usableHeight) / (Board.SIZE - 1));
        int boardPixelSize = cellSize * (Board.SIZE - 1);
        int startX = (getWidth() - boardPixelSize) / 2;
        int startY = (getHeight() - boardPixelSize) / 2;
        return new Geometry(startX, startY, cellSize);
    }

    private record Geometry(int startX, int startY, int cellSize) {
        int boardPixelSize() {
            return cellSize * (Board.SIZE - 1);
        }
    }
}
