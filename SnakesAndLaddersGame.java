import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.QuadCurve2D;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * Snakes and Ladders - Easy Learning Project
 *
 * HOW TO RUN
 * 1) javac SnakesAndLaddersGame.java
 * 2) java SnakesAndLaddersGame
 *
 * WHY THIS FILE IS LONG
 * - We keep many Java concepts in one file so learning is easy in one place.
 * - Every important part has simple English comments.
 *
 * WHAT CONCEPTS YOU CAN SEE HERE
 * - OOP: class/object, encapsulation, inheritance, abstraction, polymorphism
 * - Interface and enum
 * - Collections: List and Map
 * - Loops, conditions, switch expression
 * - Custom exception and try-catch
 * - File I/O and serialization (save/load)
 * - Multithreading/concurrency (executors + auto-save)
 * - Annotation and reflection
 * - Swing GUI and graphics drawing
 */
public class SnakesAndLaddersGame {

    // Program starts here.
    public static void main(String[] args) {
        // Swing UI should run on EDT (Event Dispatch Thread).
        SwingUtilities.invokeLater(() -> {
            try {
                GameWindow window = new GameWindow();
                window.setVisible(true);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(
                        null,
                        "Game could not start: " + ex.getMessage(),
                        "Startup Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });
    }
}

/**
 * Enum = fixed set of constant values.
 * Here it describes what a board cell means.
 */
enum CellType {
    NORMAL,
    LADDER,
    SNAKE
}

/**
 * Custom annotation.
 * We attach it to classes/methods/fields and read it later by reflection.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
@interface EasyNote {
    String value();
}

/**
 * Interface = contract.
 * Any dice class must provide roll().
 */
interface Dice {
    int roll();
}

/**
 * Class implementing Dice contract.
 */
class RandomDice implements Dice {
    private final Random random = new Random();

    @Override
    public int roll() {
        // nextInt(6) gives 0..5, so +1 gives 1..6
        return random.nextInt(6) + 1;
    }
}

/**
 * Abstract class = partial design.
 * Child token classes must implement draw().
 */
abstract class Token {
    protected final Color color;

    public Token(Color color) {
        this.color = color;
    }

    public abstract void draw(Graphics2D g2, int centerX, int centerY, int size);
}

/**
 * Circle token concrete implementation.
 */
class CircleToken extends Token {

    public CircleToken(Color color) {
        super(color);
    }

    @Override
    public void draw(Graphics2D g2, int centerX, int centerY, int size) {
        int x = centerX - size / 2;
        int y = centerY - size / 2;

        // Shadow for depth.
        g2.setColor(new Color(0, 0, 0, 70));
        g2.fillOval(x + 2, y + 3, size, size);

        // Main fill.
        g2.setColor(color);
        g2.fillOval(x, y, size, size);

        // Border.
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(2f));
        g2.drawOval(x, y, size, size);

        // Shine.
        g2.setColor(new Color(255, 255, 255, 110));
        g2.fillOval(x + size / 4, y + size / 5, size / 3, size / 3);
    }
}

/**
 * Player class demonstrates encapsulation.
 */
class Player {
    private final String name;
    private final Token token;
    private int position;

    // Constructor overloading example 1.
    public Player(String name, Color color) {
        this(name, color, 1);
    }

    // Constructor overloading example 2.
    public Player(String name, Color color, int startPosition) {
        this.name = name;
        this.token = new CircleToken(color);
        this.position = Math.max(1, startPosition);
    }

    public String getName() {
        return name;
    }

    public Token getToken() {
        return token;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = Math.max(1, position);
    }
}

/**
 * Small serializable row for one player in save file.
 */
class PlayerSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String name;
    public final int position;

    public PlayerSnapshot(String name, int position) {
        this.name = name;
        this.position = position;
    }

    public PlayerSnapshot(Player player) {
        this(player.getName(), player.getPosition());
    }
}

/**
 * Serializable full game snapshot.
 */
class GameState implements Serializable {
    private static final long serialVersionUID = 1L;

    public final List<PlayerSnapshot> playerSnapshots;
    public final int currentPlayerIndex;
    public final String savedAt;

    public GameState(List<PlayerSnapshot> playerSnapshots, int currentPlayerIndex, String savedAt) {
        this.playerSnapshots = playerSnapshots;
        this.currentPlayerIndex = currentPlayerIndex;
        this.savedAt = savedAt;
    }
}

/**
 * Custom checked exception to demonstrate user-defined exception class.
 */
class GameDataException extends Exception {
    public GameDataException(String message) {
        super(message);
    }

    public GameDataException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Board class stores snakes and ladders using maps.
 */
class Board {
    public static final int BOARD_SIZE = 100;
    public static final int COLS = 10;
    public static final int ROWS = 10;

    private final Map<Integer, Integer> ladders = new HashMap<>();
    private final Map<Integer, Integer> snakes = new HashMap<>();

    public Board() {
        // Ladders (go up)
        ladders.put(2, 23);
        ladders.put(8, 34);
        ladders.put(20, 77);
        ladders.put(32, 68);
        ladders.put(41, 79);
        ladders.put(74, 88);

        // Snakes (go down)
        snakes.put(29, 9);
        snakes.put(38, 15);
        snakes.put(47, 5);
        snakes.put(53, 33);
        snakes.put(62, 18);
        snakes.put(86, 54);
        snakes.put(92, 70);
        snakes.put(97, 25);
        snakes.put(99, 59);
    }

    public Map<Integer, Integer> getLadders() {
        return ladders;
    }

    public Map<Integer, Integer> getSnakes() {
        return snakes;
    }

    public CellType getCellType(int cell) {
        if (ladders.containsKey(cell)) {
            return CellType.LADDER;
        } else if (snakes.containsKey(cell)) {
            return CellType.SNAKE;
        }
        return CellType.NORMAL;
    }

    public int getDestination(int cell) {
        return switch (getCellType(cell)) {
            case LADDER -> ladders.get(cell);
            case SNAKE -> snakes.get(cell);
            case NORMAL -> cell;
        };
    }

    /**
     * IMPORTANT MAPPING METHOD
     * Converts board cell (1..100) into screen pixel center.
     * We need this to draw:
     * - player tokens
     * - ladders
     * - snakes
     */
    public Point cellToPixelCenter(int cell, int x0, int y0, int cellSize) {
        // Make cell number zero-based for easier math.
        int zeroBased = cell - 1;

        // Find row and column if board was simple left->right.
        int rowFromBottom = zeroBased / COLS;
        int colInRow = zeroBased % COLS;

        // Real board is zig-zag:
        // row 0: left -> right
        // row 1: right -> left
        // row 2: left -> right, etc.
        int actualCol = (rowFromBottom % 2 == 0) ? colInRow : (COLS - 1 - colInRow);

        // Convert row/col to pixel center.
        int x = x0 + actualCol * cellSize + cellSize / 2;
        int y = y0 + (ROWS - 1 - rowFromBottom) * cellSize + cellSize / 2;
        return new Point(x, y);
    }
}

/**
 * Custom drawing panel for board graphics.
 */
class BoardPanel extends JPanel {
    private final Board board;
    private final List<Player> players;

    public BoardPanel(Board board, List<Player> players) {
        this.board = board;
        this.players = players;
        setPreferredSize(new Dimension(760, 760));
        setBackground(new Color(240, 246, 252));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Background gradient.
        GradientPaint gp = new GradientPaint(
                0, 0, new Color(228, 239, 255),
                getWidth(), getHeight(), new Color(243, 250, 233)
        );
        g2.setPaint(gp);
        g2.fillRect(0, 0, getWidth(), getHeight());

        int margin = 30;
        int boardPixelSize = Math.min(getWidth(), getHeight()) - 2 * margin;
        int cellSize = boardPixelSize / Board.COLS;
        int x0 = (getWidth() - boardPixelSize) / 2;
        int y0 = (getHeight() - boardPixelSize) / 2;

        drawCells(g2, x0, y0, cellSize);
        drawLadders(g2, x0, y0, cellSize);
        drawSnakes(g2, x0, y0, cellSize);
        drawPlayers(g2, x0, y0, cellSize);

        g2.dispose();
    }

    private void drawCells(Graphics2D g2, int x0, int y0, int cellSize) {
        // Step 1: draw checker-style 10x10 cells.
        for (int row = 0; row < Board.ROWS; row++) {
            for (int col = 0; col < Board.COLS; col++) {
                int x = x0 + col * cellSize;
                int y = y0 + row * cellSize;
                int shade = ((row + col) % 2 == 0) ? 250 : 232;

                g2.setColor(new Color(shade, shade, 255));
                g2.fillRect(x, y, cellSize, cellSize);
                g2.setColor(new Color(80, 80, 105));
                g2.drawRect(x, y, cellSize, cellSize);
            }
        }

        // Step 2: print cell numbers (1..100).
        g2.setFont(new Font("SansSerif", Font.BOLD, 13));
        g2.setColor(new Color(40, 45, 70));
        for (int cell = 1; cell <= Board.BOARD_SIZE; cell++) {
            Point p = board.cellToPixelCenter(cell, x0, y0, cellSize);
            g2.drawString(String.valueOf(cell), p.x - 14, p.y + 18);
        }
    }

    private void drawLadders(Graphics2D g2, int x0, int y0, int cellSize) {
        for (Map.Entry<Integer, Integer> entry : board.getLadders().entrySet()) {
            Point start = board.cellToPixelCenter(entry.getKey(), x0, y0, cellSize);
            Point end = board.cellToPixelCenter(entry.getValue(), x0, y0, cellSize);
            drawLadder(g2, start, end);
        }
    }

    /**
     * LADDER DRAWING (easy idea)
     * 1) Compute main direction from start -> end.
     * 2) Make perpendicular direction for ladder width.
     * 3) Draw two side rails.
     * 4) Draw small steps between rails.
     */
    private void drawLadder(Graphics2D g2, Point start, Point end) {
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double length = Math.sqrt(dx * dx + dy * dy);

        if (length < 1) {
            return;
        }

        double ux = dx / length;
        double uy = dy / length;

        // Perpendicular vector (90 degrees turned direction).
        double px = -uy;
        double py = ux;

        int railOffset = 8;

        int x1a = (int) (start.x + px * railOffset);
        int y1a = (int) (start.y + py * railOffset);
        int x2a = (int) (end.x + px * railOffset);
        int y2a = (int) (end.y + py * railOffset);

        int x1b = (int) (start.x - px * railOffset);
        int y1b = (int) (start.y - py * railOffset);
        int x2b = (int) (end.x - px * railOffset);
        int y2b = (int) (end.y - py * railOffset);

        g2.setStroke(new BasicStroke(4f));
        g2.setColor(new Color(31, 123, 85));
        g2.drawLine(x1a, y1a, x2a, y2a);
        g2.drawLine(x1b, y1b, x2b, y2b);

        g2.setStroke(new BasicStroke(2.2f));
        g2.setColor(new Color(76, 175, 80));

        int steps = 7;
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            int sx = (int) (x1a + (x2a - x1a) * t);
            int sy = (int) (y1a + (y2a - y1a) * t);
            int ex = (int) (x1b + (x2b - x1b) * t);
            int ey = (int) (y1b + (y2b - y1b) * t);
            g2.drawLine(sx, sy, ex, ey);
        }
    }

    private void drawSnakes(Graphics2D g2, int x0, int y0, int cellSize) {
        for (Map.Entry<Integer, Integer> entry : board.getSnakes().entrySet()) {
            Point head = board.cellToPixelCenter(entry.getKey(), x0, y0, cellSize);
            Point tail = board.cellToPixelCenter(entry.getValue(), x0, y0, cellSize);
            drawSnake(g2, head, tail);
        }
    }

    /**
     * SNAKE DRAWING (easy idea)
     * - draw one curved thick line as snake body
     * - draw a round head and small eyes
     */
    private void drawSnake(Graphics2D g2, Point head, Point tail) {
        // Middle control point bends the curve so snake looks natural.
        int controlX = (head.x + tail.x) / 2 + ((head.y > tail.y) ? 40 : -40);
        int controlY = (head.y + tail.y) / 2;

        g2.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(179, 44, 66));
        QuadCurve2D curve = new QuadCurve2D.Double(head.x, head.y, controlX, controlY, tail.x, tail.y);
        g2.draw(curve);

        // Head circle.
        g2.setColor(new Color(220, 70, 89));
        g2.fillOval(head.x - 11, head.y - 11, 22, 22);

        // Eyes.
        g2.setColor(Color.WHITE);
        g2.fillOval(head.x - 5, head.y - 3, 4, 4);
        g2.fillOval(head.x + 1, head.y - 3, 4, 4);
    }

    private void drawPlayers(Graphics2D g2, int x0, int y0, int cellSize) {
        // If multiple players in same cell, offset them.
        Map<Integer, Integer> positionCount = new HashMap<>();

        for (Player player : players) {
            int pos = player.getPosition();
            int count = positionCount.getOrDefault(pos, 0);
            positionCount.put(pos, count + 1);

            Point center = board.cellToPixelCenter(pos, x0, y0, cellSize);
            int tokenSize = Math.max(16, cellSize / 3);

            int offsetX = (count % 2 == 0) ? -tokenSize / 3 : tokenSize / 3;
            int offsetY = (count < 2) ? -tokenSize / 3 : tokenSize / 3;

            int drawCenterX = center.x + offsetX;
            int drawCenterY = center.y + offsetY;

            player.getToken().draw(g2, drawCenterX, drawCenterY, tokenSize);
        }
    }
}

/**
 * Main game window.
 * This one class connects:
 * - UI buttons and labels
 * - game rules
 * - save/load
 * - logging
 */
@EasyNote("GameWindow demonstrates GUI, OOP, I/O, serialization, threading, annotation and reflection.")
class GameWindow extends JFrame {

    // Core game objects
    private final Board board = new Board();
    private final List<Player> players = new ArrayList<>();
    private final Dice dice = new RandomDice();

    // UI components
    private final BoardPanel boardPanel;
    private final JLabel turnLabel = new JLabel();
    private final JLabel diceLabel = new JLabel("Dice: -");
    private final JTextArea logArea = new JTextArea(12, 24);
    private final JButton rollButton = new JButton("Roll Dice");
    private final JButton restartButton = new JButton("Restart");
    private final JButton saveButton = new JButton("Save Game");
    private final JButton loadButton = new JButton("Load Game");
    private final JButton conceptsButton = new JButton("Show Concepts");

    // Background workers for concurrency demo:
    // ioExecutor = save/load/log file tasks
    // autoSaveExecutor = auto-save every N seconds
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService autoSaveExecutor = Executors.newSingleThreadScheduledExecutor();

    @EasyNote("Path to binary serialized save file")
    private static final Path SAVE_FILE = Path.of("saved_game.dat");
    private static final Path TEXT_STATE_FILE = Path.of("game_state.txt");
    private static final Path LOG_FILE = Path.of("game_log.txt");
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private int currentPlayerIndex = 0;
    private boolean isAnimating = false;
    private boolean gameOver = false;

    public GameWindow() {
        setTitle("Snakes and Ladders - Beginner + Full Concepts");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1180, 860);
        setLocationRelativeTo(null);

        // Create players.
        players.add(new Player("Player 1", new Color(0, 136, 255)));
        players.add(new Player("Player 2", new Color(255, 140, 0)));

        boardPanel = new BoardPanel(board, players);

        buildLayout();
        attachEvents();
        updateTurnLabel();
        log("Game started. Reach 100 to win.");
        startAutoSave();
        showReflectionQuickInfo();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdownBackgroundWorkers();
            }
        });
    }

    private void buildLayout() {
        setLayout(new BorderLayout(10, 10));

        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(new EmptyBorder(16, 14, 16, 14));
        side.setBackground(new Color(251, 252, 255));

        Font titleFont = new Font("SansSerif", Font.BOLD, 20);
        Font normalFont = new Font("SansSerif", Font.PLAIN, 15);

        JLabel title = new JLabel("Game Control");
        title.setFont(titleFont);

        turnLabel.setFont(normalFont);
        diceLabel.setFont(normalFont);

        rollButton.setFont(new Font("SansSerif", Font.BOLD, 16));
        restartButton.setFont(new Font("SansSerif", Font.PLAIN, 15));
        saveButton.setFont(new Font("SansSerif", Font.PLAIN, 15));
        loadButton.setFont(new Font("SansSerif", Font.PLAIN, 15));
        conceptsButton.setFont(new Font("SansSerif", Font.PLAIN, 14));

        rollButton.setBackground(new Color(32, 171, 110));
        rollButton.setForeground(Color.WHITE);
        rollButton.setFocusPainted(false);

        restartButton.setBackground(new Color(230, 238, 245));
        restartButton.setFocusPainted(false);

        saveButton.setBackground(new Color(218, 236, 250));
        saveButton.setFocusPainted(false);

        loadButton.setBackground(new Color(218, 236, 250));
        loadButton.setFocusPainted(false);

        conceptsButton.setBackground(new Color(243, 234, 213));
        conceptsButton.setFocusPainted(false);

        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(logArea);

        side.add(title);
        side.add(Box.createVerticalStrut(18));
        side.add(turnLabel);
        side.add(Box.createVerticalStrut(8));
        side.add(diceLabel);
        side.add(Box.createVerticalStrut(16));
        side.add(rollButton);
        side.add(Box.createVerticalStrut(10));
        side.add(restartButton);
        side.add(Box.createVerticalStrut(10));
        side.add(saveButton);
        side.add(Box.createVerticalStrut(8));
        side.add(loadButton);
        side.add(Box.createVerticalStrut(8));
        side.add(conceptsButton);
        side.add(Box.createVerticalStrut(16));
        side.add(new JLabel("Game Log:"));
        side.add(Box.createVerticalStrut(6));
        side.add(scrollPane);

        add(boardPanel, BorderLayout.CENTER);
        add(side, BorderLayout.EAST);

        ((JComponent) getContentPane()).setBorder(new EmptyBorder(10, 10, 10, 10));
    }

    private void attachEvents() {
        // Lambda callbacks.
        rollButton.addActionListener(e -> playTurn());
        restartButton.addActionListener(e -> restartGame());
        saveButton.addActionListener(e -> saveGameAsync());
        loadButton.addActionListener(e -> loadGameAsync());
        conceptsButton.addActionListener(e -> showAnnotatedConcepts());
    }

    @EasyNote("Concurrency: run autosave every 20 seconds")
    private void startAutoSave() {
        // scheduleAtFixedRate means:
        // start after 20 sec, then run every 20 sec.
        autoSaveExecutor.scheduleAtFixedRate(() -> {
            try {
                GameState snapshot = createSnapshot();
                writeBinaryState(snapshot);
            } catch (Exception ignored) {
                // Silent fail for auto-save to keep game smooth.
            }
        }, 20, 20, TimeUnit.SECONDS);
    }

    private void playTurn() {
        // Do nothing if game already finished or animation still running.
        if (gameOver || isAnimating) {
            return;
        }

        try {
            Player current = players.get(currentPlayerIndex);
            int roll = dice.roll();

            diceLabel.setText("Dice: " + roll);
            log(current.getName() + " rolled " + roll + ".");

            int target = current.getPosition() + roll;

            // Exact-number rule:
            // Player must land exactly on 100.
            if (target > Board.BOARD_SIZE) {
                log(current.getName() + " needs exact number to reach 100.");
                nextTurn();
                return;
            }

            animateStepByStep(current, roll);
        } catch (Exception ex) {
            log("Turn failed: " + ex.getMessage());
            isAnimating = false;
            rollButton.setEnabled(true);
        }
    }

    /**
     * Animation method.
     * Move token 1 cell at a time so movement looks smooth.
     */
    private void animateStepByStep(Player player, int steps) {
        isAnimating = true;
        rollButton.setEnabled(false);

        final int[] remaining = {steps};

        javax.swing.Timer timer = new javax.swing.Timer(180, null);
        timer.addActionListener(e -> {
            if (remaining[0] > 0) {
                player.setPosition(player.getPosition() + 1);
                remaining[0]--;
                boardPanel.repaint();
            }

            if (remaining[0] == 0) {
                timer.stop();
                afterMove(player);
            }
        });

        timer.start();
    }

    private void afterMove(Player player) {
        // 1) Check where player landed.
        int landed = player.getPosition();
        CellType type = board.getCellType(landed);

        // 2) If snake/ladder cell, jump to destination.
        if (type != CellType.NORMAL) {
            int destination = board.getDestination(landed);

            if (type == CellType.LADDER) {
                log(player.getName() + " climbed ladder: " + landed + " -> " + destination);
            } else {
                log(player.getName() + " hit snake: " + landed + " -> " + destination);
            }

            player.setPosition(destination);
            boardPanel.repaint();
        }

        // 3) Win check.
        if (player.getPosition() == Board.BOARD_SIZE) {
            gameOver = true;
            log(player.getName() + " wins the game!");
            turnLabel.setText("Winner: " + player.getName());
            isAnimating = false;
            rollButton.setEnabled(false);
            return;
        }

        nextTurn();
    }

    private void nextTurn() {
        // Modulo cycles player index 0..size-1.
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        updateTurnLabel();
        isAnimating = false;
        rollButton.setEnabled(true);
    }

    private void updateTurnLabel() {
        turnLabel.setText("Turn: " + players.get(currentPlayerIndex).getName());
    }

    @EasyNote("Reset game state for new match")
    private void restartGame() {
        // Put every player back at cell 1.
        for (Player player : players) {
            player.setPosition(1);
        }

        currentPlayerIndex = 0;
        gameOver = false;
        isAnimating = false;
        rollButton.setEnabled(true);
        diceLabel.setText("Dice: -");
        logArea.setText("");

        updateTurnLabel();
        log("Game restarted. Good luck!");
        boardPanel.repaint();
    }

    @EasyNote("Serialization + File I/O in background")
    private void saveGameAsync() {
        // Run save on background thread so UI does not freeze.
        ioExecutor.submit(() -> {
            try {
                GameState snapshot = createSnapshot();
                writeBinaryState(snapshot);
                writeTextState(snapshot);

                SwingUtilities.invokeLater(() ->
                        log("Game saved: saved_game.dat + game_state.txt"));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                        log("Save failed: " + ex.getMessage()));
            }
        });
    }

    @EasyNote("Load serialized data and apply on UI thread")
    private void loadGameAsync() {
        // Run load on background thread so UI stays responsive.
        ioExecutor.submit(() -> {
            try {
                if (!Files.exists(SAVE_FILE)) {
                    SwingUtilities.invokeLater(() ->
                            log("No save file found. Save once first."));
                    return;
                }

                GameState loaded = readStateFromDisk();
                SwingUtilities.invokeLater(() -> applyLoadedState(loaded));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                        log("Load failed: " + ex.getMessage()));
            }
        });
    }

    /**
     * Build current game snapshot safely.
     * If we are on EDT, read directly.
     * If not, jump to EDT and read there.
     */
    private GameState createSnapshot() throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            return buildSnapshotOnEdt();
        }

        final GameState[] holder = new GameState[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = buildSnapshotOnEdt());
        return holder[0];
    }

    private GameState buildSnapshotOnEdt() {
        List<PlayerSnapshot> snapshots = new ArrayList<>();
        for (Player player : players) {
            snapshots.add(new PlayerSnapshot(player));
        }

        String savedAt = LocalDateTime.now().format(timeFormatter);
        return new GameState(snapshots, currentPlayerIndex, savedAt);
    }

    private void writeBinaryState(GameState state) throws IOException {
        // ObjectOutputStream converts object to bytes (serialization).
        try (ObjectOutputStream out = new ObjectOutputStream(
                Files.newOutputStream(
                        SAVE_FILE,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE
                ))) {
            out.writeObject(state);
        }
    }

    private void writeTextState(GameState state) throws IOException {
        // This is plain text file so humans can read save summary.
        try (BufferedWriter writer = Files.newBufferedWriter(
                TEXT_STATE_FILE,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        )) {
            writer.write("Saved At: " + state.savedAt);
            writer.newLine();
            writer.write("Current Turn Index: " + state.currentPlayerIndex);
            writer.newLine();
            writer.write("Players:");
            writer.newLine();

            for (PlayerSnapshot p : state.playerSnapshots) {
                writer.write("  - " + p.name + " at cell " + p.position);
                writer.newLine();
            }
        }
    }

    private GameState readStateFromDisk() throws GameDataException {
        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(SAVE_FILE))) {
            // Read one object from binary save file.
            Object obj = in.readObject();

            if (!(obj instanceof GameState)) {
                throw new GameDataException("Save file content is not a valid GameState object.");
            }

            return (GameState) obj;
        } catch (IOException | ClassNotFoundException ex) {
            throw new GameDataException("Could not read save file.", ex);
        }
    }

    private void applyLoadedState(GameState state) {
        // Stop animation flags and enable roll again.
        isAnimating = false;
        gameOver = false;
        rollButton.setEnabled(true);

        // Copy loaded player positions into current player objects.
        int minSize = Math.min(players.size(), state.playerSnapshots.size());
        for (int i = 0; i < minSize; i++) {
            players.get(i).setPosition(state.playerSnapshots.get(i).position);
        }

        for (int i = minSize; i < players.size(); i++) {
            players.get(i).setPosition(1);
        }

        currentPlayerIndex = Math.floorMod(state.currentPlayerIndex, players.size());
        updateTurnLabel();
        boardPanel.repaint();
        log("Game loaded from save file (" + state.savedAt + ").");
    }

    @EasyNote("Reflection: read private field value at runtime")
    private void showReflectionQuickInfo() {
        try {
            // Reflection lets us inspect class details during runtime.
            Field playersField = GameWindow.class.getDeclaredField("players");
            playersField.setAccessible(true);
            List<?> list = (List<?>) playersField.get(this);
            log("Reflection check: player count = " + list.size());
        } catch (Exception ex) {
            log("Reflection check failed: " + ex.getMessage());
        }
    }

    @EasyNote("Reflection + annotation scan")
    private void showAnnotatedConcepts() {
        log("Annotation scan started.");

        // Check all methods and print those that have @EasyNote.
        Method[] methods = GameWindow.class.getDeclaredMethods();
        for (Method method : methods) {
            EasyNote note = method.getAnnotation(EasyNote.class);
            if (note != null) {
                log("Method: " + method.getName() + " => " + note.value());
            }
        }

        // Check class-level annotation.
        EasyNote classNote = GameWindow.class.getAnnotation(EasyNote.class);
        if (classNote != null) {
            log("Class note: " + classNote.value());
        }

        // Check field-level annotations.
        Field[] fields = GameWindow.class.getDeclaredFields();
        for (Field field : fields) {
            EasyNote fieldNote = field.getAnnotation(EasyNote.class);
            if (fieldNote != null) {
                log("Field: " + field.getName() + " => " + fieldNote.value());
            }
        }
    }

    private void shutdownBackgroundWorkers() {
        autoSaveExecutor.shutdownNow();
        ioExecutor.shutdownNow();
    }

    // Overloaded method example (version 1).
    private void log(String message) {
        log("-", message);
    }

    // Overloaded method example (version 2).
    private void log(String prefix, String message) {
        String finalMessage = prefix + " " + message;
        logArea.append(finalMessage + System.lineSeparator());
        logArea.setCaretPosition(logArea.getDocument().getLength());
        appendLogToFileAsync(finalMessage);
    }

    private void appendLogToFileAsync(String message) {
        try {
            // Write log file in background thread to avoid UI lag.
            ioExecutor.submit(() -> {
                try {
                    String time = LocalDateTime.now().format(timeFormatter);
                    String line = time + " | " + message + System.lineSeparator();
                    Files.writeString(
                            LOG_FILE,
                            line,
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND
                    );
                } catch (IOException ignored) {
                    // Ignore log-file write errors.
                }
            });
        } catch (RejectedExecutionException ignored) {
            // Happens if app is closing and executor is already stopped.
        }
    }
}
