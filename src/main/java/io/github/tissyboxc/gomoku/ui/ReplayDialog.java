package io.github.tissyboxc.gomoku.ui;

import io.github.tissyboxc.gomoku.core.GameSession;
import io.github.tissyboxc.gomoku.model.Move;
import io.github.tissyboxc.gomoku.replay.ReplayFileService;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * 回放窗口，按步加载历史落子。
 */
public final class ReplayDialog extends JDialog {
    private final List<Move> moves;
    private final GameSession replaySession = new GameSession();
    private final BoardPanel boardPanel = new BoardPanel(replaySession);
    private final JLabel statusLabel = new JLabel();
    private int currentStep;
    private Timer autoPlayTimer;

    public ReplayDialog(Window owner, ReplayFileService.ReplayData replayData) {
        super(owner, "对局回放", ModalityType.MODELESS);
        this.moves = replayData.moves();
        replaySession.setBlackName(replayData.blackName());
        replaySession.setWhiteName(replayData.whiteName());
        boardPanel.setInteractive(false);
        boardPanel.setPreferredSize(new java.awt.Dimension(650, 650));

        setLayout(new BorderLayout());
        add(boardPanel, BorderLayout.CENTER);
        add(createControlPanel(), BorderLayout.SOUTH);
        setSize(820, 820);
        setLocationRelativeTo(owner);
        showStep(0);
    }

    private JPanel createControlPanel() {
        JButton firstButton = new JButton("第一步");
        JButton previousButton = new JButton("上一步");
        JButton autoButton = new JButton("自动播放");
        JButton nextButton = new JButton("下一步");
        JButton lastButton = new JButton("最后一步");

        firstButton.addActionListener(event -> showStep(0));
        previousButton.addActionListener(event -> showStep(currentStep - 1));
        nextButton.addActionListener(event -> showStep(currentStep + 1));
        lastButton.addActionListener(event -> showStep(moves.size()));
        autoButton.addActionListener(event -> toggleAutoPlay(autoButton));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        controls.add(firstButton);
        controls.add(previousButton);
        controls.add(autoButton);
        controls.add(nextButton);
        controls.add(lastButton);
        controls.add(statusLabel);
        return controls;
    }

    private void toggleAutoPlay(JButton autoButton) {
        if (autoPlayTimer != null && autoPlayTimer.isRunning()) {
            autoPlayTimer.stop();
            autoButton.setText("自动播放");
            return;
        }
        autoButton.setText("暂停");
        autoPlayTimer = new Timer(650, event -> {
            if (currentStep >= moves.size()) {
                autoPlayTimer.stop();
                autoButton.setText("自动播放");
                return;
            }
            showStep(currentStep + 1);
        });
        autoPlayTimer.start();
    }

    private void showStep(int step) {
        currentStep = Math.max(0, Math.min(step, moves.size()));
        replaySession.loadMoves(moves.subList(0, currentStep));
        boardPanel.setWinningLine(currentStep == moves.size()
                ? replaySession.getWinningLine()
                : List.of());
        statusLabel.setText("第 " + currentStep + " / " + moves.size() + " 步");
        boardPanel.repaint();
    }
}
