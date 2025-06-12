package scr;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class ContinuousCharReader extends JFrame {
    private final List<CharListener> listeners = new ArrayList<>();
    private final Set<Character> pressedKeys = new HashSet<>();

    public ContinuousCharReader() {
        setTitle("Continuous Character Reader");
        setSize(300, 100);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new FlowLayout());

        JTextField inputField = new JTextField(20);
        inputField.setFocusable(true);
        add(inputField);

        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                char ch = Character.toLowerCase(e.getKeyChar());

                if (!pressedKeys.contains(ch)) {
                    pressedKeys.add(ch);
                    notifyListeners(ch, true);
                }

                if (ch == 'q') {
                    System.exit(0);
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                char ch = Character.toLowerCase(e.getKeyChar());
                if (pressedKeys.contains(ch)) {
                    pressedKeys.remove(ch);
                    notifyListeners(ch, false);
                }
            }
        });

        setVisible(true);
        inputField.requestFocusInWindow();
    }

    public void addCharListener(CharListener listener) {
        listeners.add(listener);
    }

    private void notifyListeners(char ch, boolean pressed) {
        for (CharListener listener : listeners) {
            listener.charChanged(ch, pressed);
        }
    }

    // Modificata: ora include anche lo stato (premuto o rilasciato)
    public interface CharListener {
        void charChanged(char ch, boolean pressed);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ContinuousCharReader cr = new ContinuousCharReader();
            cr.addCharListener((ch, pressed) -> {
                System.out.println("Tasto " + ch + " " + (pressed ? "premuto" : "rilasciato"));
            });
        });
    }
}
