import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import javax.imageio.*;


class Officer {
    BufferedImage sprite;
    int x, y, dir;
    int tileSize = 20;

    // Constructor loads sprite and initializes position/direction
    public Officer (String name, String spritePath, int x, int y) {
        this.x = x;
        this.y = y;
        this.dir = (int)(Math.random() * 4); // Random start direction
        try {
            sprite = ImageIO.read(new File(spritePath));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // Moves the Officer (Cop Car) based on current direction and maze constraints
    public void move(int[][] maze) {
        int speed = 2;
        int nextX = x, nextY = y;

        // Calculate intended movement
        if (dir == 0) nextX -= speed;
        else if (dir == 1) nextX += speed;
        else if (dir == 2) nextY -= speed;
        else if (dir == 3) nextY += speed;

        // Check maze collision
        int row = (nextY + tileSize / 2) / tileSize;
        int col = (nextX + tileSize / 2) / tileSize;

        // Move only if not colliding with wall
        if (maze[row][col] == 0) {
            x = nextX;
            y = nextY;
        } else {
            // Pick new random direction if blocked
            dir = (int)(Math.random() * 4);
        }


        // At grid intersection, potentially change direction
        if (x % tileSize == 0 && y % tileSize == 0) {
            int[] possibleDirs = new int[4];
            int count = 0;

            // Check all valid directions from current tile
            if (maze[y / tileSize][(x - tileSize) / tileSize] == 0) possibleDirs[count++] = 0; // left
            if (maze[y / tileSize][(x + tileSize) / tileSize] == 0) possibleDirs[count++] = 1; // right
            if (maze[(y - tileSize) / tileSize][x / tileSize] == 0) possibleDirs[count++] = 2; // up
            if (maze[(y + tileSize) / tileSize][x / tileSize] == 0) possibleDirs[count++] = 3; // down

            if (count > 0) {
                dir = possibleDirs[(int)(Math.random() * count)];
            }
        }
    }

    // Draw the Officer (Cop Car), using different frame if scared
    public void draw(Graphics g, boolean scared) {
        int frame = scared ? 1 : 0;
        g.drawImage(sprite.getSubimage(frame * 32, 0, 32, 32), x, y, null);
    }
}

// Main Pac-Man Style game panel class
public class PacManStyleGame extends JPanel implements ActionListener, KeyListener {

    Timer timer;
    BufferedImage pacmanSprite;
    BufferedImage moneySprite;
    BufferedImage backgroundImage;
    BufferedImage moneyBagImage;
    BufferedImage flippedBackgroundImage;

    int pacmanX = 15, pacmanY = 200;
    int pacmanDir = 0; // 0=left, 2=right, 4=up, 6=down
    int tileSize = 20;

    // Maze and dot map arrays
    int[][] maze = new int[23][23];
    int[][] dotMap = new int[23][23];

    int score = 0;
    int level = 1;
    int lives = 3;
    boolean scaredMode = false;
    int scaredTimer = 0;
    boolean horizontal = false;

    boolean inStartMenu = true;

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

        // Initialize Officers
        Ace = new Officer("Ace", "Cop Car.png", 180, 180);
        Stephane  = new Officer("Stephane",  "Cop Car.png", 200, 180);
        Jackson   = new Officer("Jackson",   "Cop Car.png", 220, 180);
        DonutMan  = new Officer("DonutMan",  "Cop Car.png", 240, 180);

        timer = new Timer(40, this);
        timer.start();
        playSound(new File("start.wav"));
    }

    // Load Cops vs. Robbers sprites
    void loadResources() {
        try {
            pacmanSprite = ImageIO.read(new File("joe.png"));
            moneySprite = ImageIO.read(new File("moneystack.png"));
            backgroundImage = ImageIO.read(new File("citybackground.png"));
            flippedBackgroundImage = ImageIO.read(new File("flippedcitybackground.png"));
            moneyBagImage = ImageIO.read(new File("moneybag.png"));

        } catch (IOException e) {
            e.printStackTrace();
        }
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
        maze[3][0] = 1;
        maze[3][1] = 1;
        maze[3][2] = 1;
        maze[3][3] = 1;
        maze[3][4] = 1;
        maze[3][5] = 1;

        maze[2][5] = 1;
        maze[1][5] = 1;
        maze[0][5] = 1;
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
        if (!inStartMenu) {
            updateGame();
        }
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
        int newX = pacmanX;
        int newY = pacmanY;

        // Move based on direction
        if (pacmanDir == 0) newX -= 3;
        else if (pacmanDir == 2) newX += 3;
        else if (pacmanDir == 4) newY -= 3;
        else if (pacmanDir == 6) newY += 3;

        // Check collision with maze
        int row = (newY + tileSize / 2) / tileSize;
        int col = (newX + tileSize / 2) / tileSize;

        if (maze[row][col] == 0) {
            pacmanX = newX;
            pacmanY = newY;
        }

        // Eat dot or power pellet
        row = (pacmanY + tileSize / 2) / tileSize;
        col = (pacmanX + tileSize / 2) / tileSize;

        if (dotMap[row][col] == 1) {
            dotMap[row][col] = 0;
            score += 10;
            playSound(new File("ding.wav"));
        } else if (dotMap[row][col] == 2) {
            dotMap[row][col] = 0;
            score += 50;
            playSound(new File("moneybagsound.wav"));
            scaredMode = true;
            scaredTimer = 500;
        }

        //alerts you that the scared mode is running out
        if (scaredMode) {
            scaredTimer--;
            if (scaredTimer == 50) {
                playSound(new File("tiktok2.wav"));
            }
            if (scaredTimer <= 0) {
                scaredMode = false;
            }
        }
        // update the level when all money is collected
        if (allMoneyCollected()) {
            level += 1;
            if (level == 2) {
                backgroundImage = flippedBackgroundImage; //switch the background
                horizontal = true;
                pacmanX = 385; // Reset pacman position
                pacmanY = 200;
                initMoney();
                mirrorMazeAndDots(); // mirrors the maze and money

            } else if (level == 3) {
                // we can add a boss level into here)
                pacmanX = 15;
                pacmanY = 200;
            }
        }


        // Move ghosts
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
        Rectangle pacmanRect = new Rectangle(pacmanX, pacmanY, 16, 16);
        Rectangle officerRect  = new Rectangle(officer.x, officer.y, 16, 16);

        if (pacmanRect.intersects(officerRect)) {
            if (scaredMode) {
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
                    JOptionPane.showMessageDialog(this, "Game Over!\nMoney: $" + score);
                    System.exit(0);
                } else {
                    playDeathSounds(new File("Death Sound Robber.wav"), new File("pacman_death2.wav"));
                    pacmanX = 15;
                    pacmanY = 200;
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
            String title = "Cops vs. Robbers";
            int titleWidth = g.getFontMetrics().stringWidth(title);
            g.drawString(title, (getWidth() - titleWidth) / 2, getHeight() / 2 - 50);

            g.setFont(new Font("Arial", Font.BOLD, 18));
            String prompt = "Press ENTER to start";
            int promptWidth = g.getFontMetrics().stringWidth(prompt);
            g.drawString(prompt, (getWidth() - promptWidth) / 2, getHeight() / 2);
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

        //DRAWS ONLY THE BORDER OF THE MAP. TURN THIS ON ONCE ALL THE WALLS ARE BUILT
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
        if (pacmanDir == 0) frame = 1;
        else if (pacmanDir == 2) frame = 3;
        else if (pacmanDir == 4) frame = 5;
        else if (pacmanDir == 6) frame = 7;
        g.drawImage(pacmanSprite, pacmanX, pacmanY, null);

        // Draw Officers (Cop Cars)
        Ace.draw(g, scaredMode);
        Stephane.draw(g, scaredMode);
        Jackson.draw(g, scaredMode);
        DonutMan.draw(g, scaredMode);

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
        } else {
            int key = e.getKeyCode();
            if (key == KeyEvent.VK_LEFT) pacmanDir = 0;
            if (key == KeyEvent.VK_RIGHT) pacmanDir = 2;
            if (key == KeyEvent.VK_UP) pacmanDir = 4;
            if (key == KeyEvent.VK_DOWN) pacmanDir = 6;
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
        JFrame frame = new JFrame("PacMan Game");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.add(new PacManStyleGame());
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}