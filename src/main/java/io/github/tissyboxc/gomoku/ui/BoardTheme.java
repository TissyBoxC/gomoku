package io.github.tissyboxc.gomoku.ui;

import java.awt.Color;

/**
 * 棋盘背景主题。
 */
public enum BoardTheme {
    WOOD("原木", new Color(0xD8, 0xA9, 0x6A), new Color(0xB6, 0x7B, 0x43),
            new Color(0x5D, 0x3B, 0x22), new Color(0xF4, 0xE4, 0xCC)),
    JADE("青玉", new Color(0xB9, 0xD9, 0xB2), new Color(0x75, 0xA9, 0x78),
            new Color(0x24, 0x4A, 0x38), new Color(0xE8, 0xF2, 0xDE)),
    OCEAN("海蓝", new Color(0xA8, 0xCA, 0xDD), new Color(0x5F, 0x91, 0xB3),
            new Color(0x17, 0x3D, 0x55), new Color(0xE5, 0xF1, 0xF7));

    private final String displayName;
    private final Color boardColor;
    private final Color borderColor;
    private final Color lineColor;
    private final Color labelColor;

    BoardTheme(
            String displayName,
            Color boardColor,
            Color borderColor,
            Color lineColor,
            Color labelColor) {
        this.displayName = displayName;
        this.boardColor = boardColor;
        this.borderColor = borderColor;
        this.lineColor = lineColor;
        this.labelColor = labelColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Color getBoardColor() {
        return boardColor;
    }

    public Color getBorderColor() {
        return borderColor;
    }

    public Color getLineColor() {
        return lineColor;
    }

    public Color getLabelColor() {
        return labelColor;
    }
}
