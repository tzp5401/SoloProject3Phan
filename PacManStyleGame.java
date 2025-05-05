import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import javax.imageio.*;

// JInput imports for Xbox controller
import net.java.games.input.Controller;
import net.java.games.input.ControllerEnvironment;
import net.java.games.input.Component;


// suppress JInput INFO logs
import java.util.Random;
import java.util.logging.Logger;
import java.util.logging.Level;

// AI types for each cop’s behavior
enum AIType { CHASER, AMBUSHER, SCATTER, RANDOM }

// Officer Class (non-public so it can share this file)
class Officer {
    BufferedImage sprite;
    int x, y, dir;
    int tileSize = 20;

    private AIType ai;
    private int targetX, targetY;

    public Officer(String name, String spritePath, int x, int y, AIType ai) {
        this.x = x; this.y = y;
        this.dir = (int)(Math.random() * 4);  // 0=L,1=R,2=U,3=D
        this.ai  = ai;
        try {
            sprite = ImageIO.read(new File(spritePath));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setTarget(int px, int py) {
        this.targetX = px;
        this.targetY = py;
    }

    public void move(int[][] maze) {
        int speed = 2;
        int nextX = x, nextY = y;
        if (dir == 0)       nextX -= speed;
        else if (dir == 1)  nextX += speed;
        else if (dir == 2)  nextY -= speed;
        else if (dir == 3)  nextY += speed;

        int row = (nextY + tileSize/2) / tileSize;
        int col = (nextX + tileSize/2) / tileSize;
        if (maze[row][col] == 0) {
            x = nextX; y = nextY;
        } else {
            dir = (int)(Math.random() * 4);
        }

        if (x % tileSize == 0 && y % tileSize == 0) {
            dir = chooseDirection(maze);
        }
    }

    public void draw(Graphics g, boolean bribe) {
        int[] frameMap = {1,0,2,3};
        int frame = frameMap[dir];
        int yOffset = (bribe && sprite.getHeight()>=64) ? sprite.getHeight()/2 : 0;
        BufferedImage sub;
        try {
            sub = sprite.getSubimage(frame*32, yOffset, 32,32);
        } catch (RasterFormatException ex) {
            sub = sprite.getSubimage(frame*32, 0,32,32);
        }
        g.drawImage(sub, x, y, null);
    }

    // AI helpers
    private boolean canGo(int d, int[][] maze) {
        int tx = x + (d==1?tileSize:(d==0?-tileSize:0));
        int ty = y + (d==3?tileSize:(d==2?-tileSize:0));
        int row = (ty+tileSize/2)/tileSize, col = (tx+tileSize/2)/tileSize;
        return maze[row][col] == 0;
    }

    private int chooseDirection(int[][] maze) {
        List<Integer> turns = new ArrayList<>();
        for (int d=0; d<4; d++) if (canGo(d, maze)) turns.add(d);
        if (turns.isEmpty()) return dir;

        switch (ai) {
            case CHASER:
                return greedy(turns, targetX, targetY);
            case AMBUSHER:
                int ax = targetX + ((dir==1?4:(dir==0?-4:0)) * tileSize);
                int ay = targetY + ((dir==3?4:(dir==2?-4:0)) * tileSize);
                return greedy(turns, ax, ay);
            case SCATTER:
                return greedy(turns, 0, 0);
            case RANDOM:
            default:
                return turns.get(new Random().nextInt(turns.size()));
        }
    }

    private int greedy(List<Integer> turns, int tx, int ty) {
        double best=Double.MAX_VALUE; int pick=turns.get(0);
        for (int d:turns) {
            int cx = x + (d==1?tileSize:(d==0?-tileSize:0));
            int cy = y + (d==3?tileSize:(d==2?-tileSize:0));
            double dist = Math.pow(cx-tx,2) + Math.pow(cy-ty,2);
            if (dist<best) { best=dist; pick=d; }
        }
        return pick;
    }
}

// Main Pac-Man Style game panel class
public class PacManStyleGame extends JPanel implements ActionListener, KeyListener {

    Timer timer;
    BufferedImage robberSprite;
    BufferedImage moneySprite;
    BufferedImage backgroundImage;
    BufferedImage moneyBagImage;
    BufferedImage flippedBackgroundImage;

    int robberX = 15, robberY = 200;
    int robberDir = 0; // 0=left, 2=right, 4=up, 6=down
    int tileSize = 20;

    // Maze and dot map arrays
    int[][] maze = new int[23][23];
    int[][] dotMap = new int[23][23];

    int score = 0;
    int level = 1;
    int lives = 3;
    boolean bribeMode = false;
    int bribeTimer = 0;
    boolean horizontal = false;
    boolean inStartMenu = true;

    // ─── new controller fields ───────────────────────────────────────────────
    private Controller xboxController;
    private Component xAxis, yAxis, pov, startButton, viewLbButton;
    private int lastKeyPressed = -1;

    boolean allMoneyCollected() {
        for (int row = 0; row < dotMap.length; row++) {
            for (int col = 0; col < dotMap[0].length; col++) {
                if (dotMap[row][col] == 1 || dotMap[row][col] == 2) return false;
            }
        }
        return true;
    }

    // Officers
    Officer Ace, Stephane, Jackson, DonutMan;

    // Constructor initializes game
    public PacManStyleGame() {
        setPreferredSize(new Dimension(460, 460));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);
        loadResources();
        loadMaze();
        initMoney();

        // ── SUPPRESS JINPUT LOGGING ─────────────────────────────
        Logger jinputLog = Logger.getLogger("net.java.games.input");
        jinputLog.setLevel(Level.OFF);
        jinputLog.setUseParentHandlers(false);

        setPreferredSize(new Dimension(460, 460));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);
        loadResources();
        loadMaze();
        initMoney();

        // ─── find the Xbox controller ───────────────────────────────────
        Controller[] controllers =
                ControllerEnvironment.getDefaultEnvironment().getControllers();
        for (Controller c : controllers) {
            if (c.getType() == Controller.Type.GAMEPAD) {
                xboxController = c;
                break;
            }
        }
        if (xboxController != null) {
            xAxis = xboxController.getComponent(Component.Identifier.Axis.X);
            yAxis = xboxController.getComponent(Component.Identifier.Axis.Y);
            pov = xboxController.getComponent(Component.Identifier.Axis.POV);
            startButton = xboxController.getComponent(Component.Identifier.Button._7);
            viewLbButton= xboxController.getComponent(Component.Identifier.Button._4);
        } else {
            System.out.println("No gamepad found; using keyboard only.");
        }


        // Initialize Officers
        Ace = new Officer("Ace", "Red Cop Car.png", 180, 180, AIType.CHASER);
        Stephane  = new Officer("Stephane",  "Pink Cop Car.png", 200, 180, AIType.AMBUSHER);
        Jackson   = new Officer("Jackson", "White Cop Car.png", 220, 180, AIType.RANDOM);
        DonutMan  = new Officer("DonutMan",  "Donut Cop Car.png", 240, 180, AIType.RANDOM);

        timer = new Timer(40, this);
        timer.start();
        playSound(new File("start.wav"));
    }

    // Load Cops vs. Robbers sprites
    void loadResources() {
        try {
            robberSprite = ImageIO.read(new File("joe.png"));
            moneySprite = ImageIO.read(new File("moneystack.png"));
            backgroundImage = ImageIO.read(new File("citybackground.png"));
            flippedBackgroundImage = ImageIO.read(new File("flippedcitybackground.png"));
            moneyBagImage = ImageIO.read(new File("moneybag.png"));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    List<String[]> loadLeaderboard(String filename) {
        List<String[]> list = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 2) {
                    list.add(parts); // parts[0] = name, parts[1] = score
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return list;
    }

    void saveLeaderboard(List<String[]> list, String filename) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            for (String[] entry : list) {
                writer.println(entry[0] + "," + entry[1]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void viewLeaderboard() {
        // Load leaderboard from file
        List<String[]> leaderboard = loadLeaderboard("leaderboard.txt");

        // Sort leaderboard based on score
        leaderboard.sort((a, b) -> Integer.compare(Integer.parseInt(b[1]), Integer.parseInt(a[1])));

        StringBuilder lbText = new StringBuilder("LEADERBOARD:\n");
        for (int i = 0; i < leaderboard.size(); i++) {
            lbText.append((i + 1) + ". " + leaderboard.get(i)[0] + " - $" + leaderboard.get(i)[1] + "\n");
        }

        // Show leaderboard
        JOptionPane.showMessageDialog(this, lbText.toString(), "Leaderboard", JOptionPane.INFORMATION_MESSAGE);
    }


    // Build simple maze layout
    void loadMaze() {
        for (int i = 0; i < 23; i++) {
            maze[0][i] = 1;
            maze[22][i] = 1;
            maze[i][0] = 1;
            maze[i][22] = 1;
        }

        // YOU HAVE TO MANUALLY PLACE THE WALLS ALONG THE CITY BUILDINGS. I HAVE THE WALLS PAINTED SO YOU CAN SEE HWAT YOURE DOING

        // Wall Formation and Collision For Building #1 Using Array List:
        maze[3][0] = 1;
        maze[3][1] = 1;
        maze[3][2] = 1;
        maze[3][3] = 1;
        maze[3][4] = 1;
        maze[3][5] = 1;
        maze[2][5] = 1;
        maze[1][5] = 1;
        maze[0][5] = 1;

        // Wall Formation and Collision For Building #2 Using Array List:
        maze[1][8] = 1;
        maze[2][8] = 1;
        maze[3][8] = 1;
        maze[3][9] = 1;
        maze[3][10] = 1;
        maze[3][11] = 1;
        maze[3][12] = 1;
        maze[3][13] = 1;
        maze[3][14] = 1;
        maze[3][15] = 1;
        maze[3][16] = 1;
        maze[3][17] = 1;
        maze[2][17] = 1;
        maze[1][17] = 1;

        // Wall Formation and Collision For Building #3 Using Array List:
        maze[1][19] = 1;
        maze[1][20] = 1;
        maze[1][21] = 1;
        maze[2][19] = 1;
        maze[3][19] = 1;
        maze[3][20] = 1;
        maze[3][21] = 1;

    }

    // Place money stacks and money bags
    void initMoney() {
        for (int row = 0; row < 23; row++) {
            for (int col = 0; col < 23; col++) {
                dotMap[row][col] = 0; // Clear all first
            }
        }
        // placing money stacks that don't fit a pattern
        int[][] specificDots = {
                {10,13},{10,18},{15,18},{17,18},{19,18},{21,18},
                {18,9},{18,11},{18,14},{18,16},
                {19,13},{21,13},
                {12,9},{12,11},{12,13},{12,15},{12,17},{12,19},{12,21},
                {13,9},{13,11},{13,13},{13,15},{13,17},{13,19},{13,21},
                {8,9},{8,11},{8,13},{8,15},{8,17},{8,19},{8,21}
        };
        for (int[] pos : specificDots) {
            dotMap[pos[0]][pos[1]] = 1;
        }

        // filling rows 4 and 5 on only odd columns with money
        for (int row = 4; row <= 5; row++) {
            for (int col = 1; col <= 21; col += 2) {
                dotMap[row][col] = 1;
            }
        }

        // placing money at column 6 and 7 for each row specified
        int[] rows = {2, 7, 9, 11, 13, 15, 17, 19, 21};
        for (int row : rows) {
            dotMap[row][6] = 1;
            dotMap[row][7] = 1;
        }

        //placing money bags
        dotMap[20][1] = 2;
        dotMap[17][20] = 2;
        dotMap[6][12] = 2;
        dotMap[2][18] = 2;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // 1) poll pad
        if (xboxController!=null) xboxController.poll();

        // 2) if LB pressed, show leaderboard
        if (viewLbButton!=null && viewLbButton.getPollData()==1.0f) {
            viewLeaderboard();
        }

        // 3) rest of tick
        if (inStartMenu) {
            // also allow “Start” button to dismiss menu
            if ((startButton!=null && startButton.getPollData()==1.0f) ||
                    lastKeyPressed==KeyEvent.VK_ENTER) {
                inStartMenu=false;
            }
            repaint();
            return;
        }

        updateGame();
        repaint();
    }

    int[][] mirrorArray(int[][] original, boolean horizontal) {
        int rows = original.length;
        int cols = original[0].length;
        int[][] mirrored = new int[rows][cols];

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                mirrored[row][col] = horizontal
                        ? original[row][cols - col - 1]
                        : original[rows - row - 1][col];
            }
        }
        return mirrored;
    }

    //flips the map for level 2
    void mirrorMazeAndDots() {
        maze = mirrorArray(maze, horizontal);
        dotMap = mirrorArray(dotMap, horizontal);
    }

    // Game logic and movement update
    void updateGame() {
        int newX = robberX;
        int newY = robberY;

        // ─── poll gamepad & set robberDir ───────────────────────────────────
        if (xboxController != null) {
            xboxController.poll();
            float hat = pov.getPollData();
            // use the nested POV enum, not static fields on Component
            if (hat == Component.POV.UP)    robberDir = 4;
            else if (hat == Component.POV.DOWN)  robberDir = 6;
            else if (hat == Component.POV.LEFT)  robberDir = 0;
            else if (hat == Component.POV.RIGHT) robberDir = 2;
            else {
                float xv = xAxis.getPollData(), yv = yAxis.getPollData();
                if (Math.abs(xv) > 0.5f || Math.abs(yv) > 0.5f) {
                    if (Math.abs(xv) > Math.abs(yv)) robberDir = (xv < 0 ? 0 : 2);
                    else                              robberDir = (yv < 0 ? 4 : 6);
                }
            }
        }

        // Move based on direction
        if (robberDir == 0) newX -= 3;
        else if (robberDir == 2) newX += 3;
        else if (robberDir == 4) newY -= 3;
        else if (robberDir == 6) newY += 3;

        // Check collision with maze
        int row = (newY + tileSize / 2) / tileSize;
        int col = (newX + tileSize / 2) / tileSize;

        if (maze[row][col] == 0) {
            robberX = newX;
            robberY = newY;
        }

        // Collects Money or Money Bag
        row = (robberY + tileSize / 2) / tileSize;
        col = (robberX + tileSize / 2) / tileSize;

        if (dotMap[row][col] == 1) {
            dotMap[row][col] = 0;
            score += 10;
            playSound(new File("ding.wav"));
        } else if (dotMap[row][col] == 2) {
            dotMap[row][col] = 0;
            score += 50;
            playSound(new File("moneybagsound.wav"));
            bribeMode = true;
            bribeTimer = 500;
        }

        //alerts you that the bribe mode is running out
        if (bribeMode) {
            bribeTimer--;
            if (bribeTimer == 50) {
                playSound(new File("tiktok2.wav"));
            }
            if (bribeTimer <= 0) {
                bribeMode = false;
            }
        }
        // update the level when all money is collected
        if (allMoneyCollected()) {
            level += 1;
            if (level == 2) {
                backgroundImage = flippedBackgroundImage; //switch the background
                horizontal = true;
                robberX = 385; // Reset pacman position
                robberY = 200;
                initMoney();
                mirrorMazeAndDots(); // mirrors the maze and money

            } else if (level == 3) {
                // we can add a boss level into here)
                robberX = 15;
                robberY = 200;
            }
        }

        // Targets the player
        Ace.setTarget(robberX, robberY);
        Stephane.setTarget(robberX, robberY);
        Jackson .setTarget(robberX, robberY);
        DonutMan.setTarget(robberX, robberY);

        // Move officers
        Ace.move(maze);
        Stephane.move(maze);
        Jackson.move(maze);
        DonutMan.move(maze);

        // Check for collisions
        checkCollision(Ace);
        checkCollision(Stephane);
        checkCollision(Jackson);
        checkCollision(DonutMan);
    }

    // Handle collision with Officer (Cop Car)
    void checkCollision(Officer officer) {
        Rectangle robberRect = new Rectangle(robberX, robberY, 16, 16);
        Rectangle officerRect  = new Rectangle(officer.x, officer.y, 16, 16);

        if (robberRect.intersects(officerRect)) {
            if (bribeMode) {
                // Bribe Officers
                officer.x = 180;
                officer.y = 180;
                score -= 20;
                playSound(new File("pacman_eatghost.wav"));
            } else {
                // Lose a life
                lives--;
                if (lives <= 0) {
                    playDeathSounds(new File("Death Sound Robber.wav"), new File("pacman_death2.wav"));

                    String name = JOptionPane.showInputDialog(this, "Game Over!\nEnter your name:");
                    if (name == null || name.trim().isEmpty()) name = "Anonymous";

                    List<String[]> leaderboard = loadLeaderboard("leaderboard.txt");
                    leaderboard.add(new String[]{name, String.valueOf(score)});

                    leaderboard.sort((a, b) -> Integer.compare(Integer.parseInt(b[1]), Integer.parseInt(a[1])));
                    if (leaderboard.size() > 5) leaderboard = leaderboard.subList(0, 5);

                    saveLeaderboard(leaderboard, "leaderboard.txt");

                    StringBuilder lbText = new StringBuilder("LEADERBOARD:\n");
                    for (int i = 0; i < leaderboard.size(); i++) {
                        lbText.append((i+1) + ". " + leaderboard.get(i)[0] + " - $" + leaderboard.get(i)[1] + "\n");
                    }
                    JOptionPane.showMessageDialog(this, lbText.toString());

                    System.exit(0);
                } else {
                    playDeathSounds(new File("Death Sound Robber.wav"), new File("pacman_death2.wav"));
                    robberX = 15;
                    robberY = 200;
                }
            }
        }
    }

    // Draw the game
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);


        if (backgroundImage != null) {
            g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), null);
        }

        if (inStartMenu) {
            // Title screen
            g.setColor(Color.YELLOW);
            g.setFont(new Font("Arial", Font.BOLD, 36));
            String title = "Robber vs. Cops";
            int titleWidth = g.getFontMetrics().stringWidth(title);
            g.drawString(title, (getWidth() - titleWidth) / 2, getHeight() / 2 - 50);

            g.setFont(new Font("Arial", Font.BOLD, 18));
            String prompt = "Press ENTER to start";
            int promptWidth = g.getFontMetrics().stringWidth(prompt);
            g.drawString(prompt, (getWidth() - promptWidth) / 2, getHeight() / 2);

            String leaderboardPrompt = "Press L to view leaderboard";
            int leaderboardPromptWidth = g.getFontMetrics().stringWidth(leaderboardPrompt);
            g.drawString(leaderboardPrompt, (getWidth() - leaderboardPromptWidth) / 2, getHeight() / 2 + 30);

            return;
        }

        // Draw ALL maze walls
        for (int row = 0; row < maze.length; row++) {
            for (int col = 0; col < maze[0].length; col++) {
                if (maze[row][col] == 1) {
                    g.setColor(Color.BLUE);
                    g.fillRect(col * tileSize, row * tileSize, tileSize, tileSize);
                }
            }
        }

        //DRAWS ONLY THE BORDER OF THE MAP. TURN THIS ON ONCE ALL THE WALLS ARE BUILT SO IT LOOKS LIKE THE BUILDINGS ARE THE WALLS
//        for (int row = 0; row < maze.length; row++) {
//            for (int col = 0; col < maze[0].length; col++) {
//                if (maze[row][col] == 1) {
//                    // Check if the wall is part of the border (edge of the maze)
//                    if (row == 0 || row == maze.length - 1 || col == 0 || col == maze[0].length - 1) {
//                        g.setColor(Color.BLUE);  // Border color
//                        g.fillRect(col * tileSize, row * tileSize, tileSize, tileSize);
//                    }
//                }
//            }
//        }



        // Drawing money stacks and money bags
        for (int row = 0; row < dotMap.length; row++) {
            for (int col = 0; col < dotMap[0].length; col++) {
                int x = col * tileSize;
                int y = row * tileSize;

                if (dotMap[row][col] == 1) {
                    g.drawImage(moneySprite, x + 2, y + 2, 16, 16, null);
                }
                else if (dotMap[row][col] == 2 && moneyBagImage != null) {
                    g.drawImage(moneyBagImage, x + 2, y + 2, 30, 30, null);
                }
            }
        }

        // Draw Robber
        int frame = 0;
        if (robberDir == 0) frame = 1;
        else if (robberDir == 2) frame = 3;
        else if (robberDir == 4) frame = 5;
        else if (robberDir == 6) frame = 7;
        g.drawImage(robberSprite, robberX, robberY, null);

        // Draw Officers (Cop Cars)
        Ace.draw(g, bribeMode);
        Stephane.draw(g, bribeMode);
        Jackson.draw(g, bribeMode);
        DonutMan.draw(g, bribeMode);

        // Draw HUD
        g.setColor(Color.YELLOW);
        g.setFont(new Font("Arial", Font.BOLD, 14));
        g.drawString("Money: $" + score, 10, 15);

        String levelText = "Level: " + level;
        int levelTextWidth = g.getFontMetrics().stringWidth(levelText);
        g.drawString(levelText, (getWidth() - levelTextWidth) / 2, 15);

        String livesText = "Lives: " + lives;
        int livesTextWidth = g.getFontMetrics().stringWidth(livesText);
        g.drawString(livesText, getWidth() - livesTextWidth - 10, 15);
    }

    // Handle key presses
    @Override
    public void keyPressed(KeyEvent e) {
        if (inStartMenu && e.getKeyCode() == KeyEvent.VK_ENTER) {
            inStartMenu = false;
        } else if (e.getKeyCode() == KeyEvent.VK_L) {
            viewLeaderboard(); // Show the leaderboard
        } else {
            int key = e.getKeyCode();
            if (key == KeyEvent.VK_LEFT) robberDir = 0;
            if (key == KeyEvent.VK_RIGHT) robberDir = 2;
            if (key == KeyEvent.VK_UP) robberDir = 4;
            if (key == KeyEvent.VK_DOWN) robberDir = 6;
        }
    }

    public void keyReleased(KeyEvent e) {}
    public void keyTyped(KeyEvent e) {}

    // Play a short sound in a separate thread
    private void playSound(File soundFile) {
        new Thread(() -> {
            try {
                Clip clip = AudioSystem.getClip();
                clip.open(AudioSystem.getAudioInputStream(soundFile));
                clip.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // Play two death sounds sequentially
    private void playDeathSounds(File firstSound, File secondSound) {
        new Thread(() -> {
            try {
                Clip clip1 = AudioSystem.getClip();
                clip1.open(AudioSystem.getAudioInputStream(firstSound));
                clip1.start();
                Thread.sleep(clip1.getMicrosecondLength() / 1000);

                Clip clip2 = AudioSystem.getClip();
                clip2.open(AudioSystem.getAudioInputStream(secondSound));
                clip2.start();
                Thread.sleep(clip2.getMicrosecondLength() / 1000);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // Main method to launch the game window
    public static void main(String[] args) {
        JFrame frame = new JFrame("PacMan Style Game");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.add(new PacManStyleGame());
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
