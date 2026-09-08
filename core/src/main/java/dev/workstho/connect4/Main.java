package dev.workstho.connect4;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
    private static final int ROWS = 6;
    private static final int COLS = 7;
    private static final int EMPTY = 0;
    private static final int PLAYER = 1;
    private static final int BOT = 2;
    private static final int SEARCH_DEPTH = 7;
    private static final int WIN_SCORE = 1000000;

    private static final float WORLD_WIDTH = 12f;
    private static final float WORLD_HEIGHT = 9f;
    private static final float UI_WIDTH = 960f;
    private static final float UI_HEIGHT = 720f;
    private static final float CELL = 1f;
    private static final float BOARD_LEFT = (WORLD_WIDTH - COLS * CELL) / 2f;
    private static final float BOARD_BOTTOM = 0.7f;
    private static final float COIN_SIZE = 0.78f;
    private static final float LOAD_DURATION = 2.4f;

    private static final Color BACKGROUND = new Color(0.08f, 0.12f, 0.22f, 1f);
    private static final Color BOARD_COLOR = new Color(0.12f, 0.38f, 0.78f, 1f);
    private static final Color BOARD_EDGE = new Color(0.07f, 0.24f, 0.52f, 1f);
    private static final Color HOLE_COLOR = new Color(0.05f, 0.08f, 0.16f, 1f);
    private static final Color PLAYER_COLOR = new Color(0.90f, 0.22f, 0.27f, 1f);
    private static final Color BOT_COLOR = new Color(1f, 0.84f, 0.08f, 1f);
    private static final Color BUTTON_COLOR = new Color(0.16f, 0.62f, 0.56f, 1f);
    private static final Color BUTTON_HOVER = new Color(0.20f, 0.74f, 0.66f, 1f);

    private SpriteBatch spriteBatch;
    private FitViewport viewport;
    private FitViewport uiViewport;
    private BitmapFont font;
    private BitmapFont titleFont;
    private GlyphLayout layout;
    private Vector2 touchPos;
    private Vector2 uiTouchPos;
    private Texture pixelTexture;
    private Texture playerCoinTexture;
    private Texture botCoinTexture;
    private Texture holeTexture;
    private Texture logoTexture;

    private int[][] board;
    private boolean[][] winningCells;
    private int currentPlayer;
    private boolean gameOver;
    private int winner;
    private boolean coinFalling;
    private int fallingPlayer;
    private int fallingCol;
    private int fallingRow;
    private float fallingX;
    private float fallingY;
    private float fallingTargetY;
    private float botThinkTimer;
    private Rectangle playAgainBounds;
    private int hoverCol;
    private boolean loading;
    private float loadTime;

    @Override
    public void create() {
        spriteBatch = new SpriteBatch();
        viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT);
        uiViewport = new FitViewport(UI_WIDTH, UI_HEIGHT);
        font = createHudFont(36);
        titleFont = createHudFont(54);
        layout = new GlyphLayout();
        touchPos = new Vector2();
        uiTouchPos = new Vector2();
        pixelTexture = createSolidTexture(Color.WHITE);
        playerCoinTexture = createCoinTexture(PLAYER_COLOR, new Color(0.62f, 0.10f, 0.14f, 1f), new Color(1f, 0.55f, 0.55f, 1f));
        botCoinTexture = createCoinTexture(BOT_COLOR, new Color(0.72f, 0.55f, 0.04f, 1f), new Color(1f, 0.95f, 0.55f, 1f));
        holeTexture = createCircleTexture(HOLE_COLOR);
        logoTexture = new Texture(Gdx.files.internal("workstho-games-logo.png"));
        logoTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        playAgainBounds = new Rectangle(UI_WIDTH / 2f - 120f, 28f, 240f, 54f);
        loading = true;
        loadTime = 0f;
        resetGame();
    }

    @Override
    public void render() {
        input();
        logic();
        draw();
    }

    private void input() {
        hoverCol = -1;
        touchPos.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(touchPos);
        uiTouchPos.set(Gdx.input.getX(), Gdx.input.getY());
        uiViewport.unproject(uiTouchPos);

        if (loading) return;

        if (gameOver) {
            if (Gdx.input.justTouched() && playAgainBounds.contains(uiTouchPos.x, uiTouchPos.y)) {
                resetGame();
            }
            return;
        }

        if (coinFalling || currentPlayer != PLAYER) return;

        hoverCol = worldToColumn(touchPos.x);
        if (hoverCol >= 0 && !columnHasSpace(hoverCol)) hoverCol = -1;

        int chosenCol = -1;
        if (Gdx.input.justTouched() && hoverCol >= 0) {
            chosenCol = hoverCol;
        } else {
            chosenCol = columnFromKeys();
        }

        if (chosenCol >= 0 && columnHasSpace(chosenCol)) {
            startDrop(chosenCol, PLAYER);
        }
    }

    private void logic() {
        float delta = Gdx.graphics.getDeltaTime();

        if (loading) {
            loadTime += delta;
            if (loadTime >= LOAD_DURATION) loading = false;
            return;
        }

        if (coinFalling) {
            fallingY -= 10f * delta;
            if (fallingY <= fallingTargetY) {
                fallingY = fallingTargetY;
                board[fallingRow][fallingCol] = fallingPlayer;
                coinFalling = false;
                finishTurn(fallingPlayer);
            }
            return;
        }

        if (gameOver || currentPlayer != BOT) return;

        botThinkTimer += delta;
        if (botThinkTimer >= 0.45f) {
            botThinkTimer = 0f;
            int botCol = chooseBotColumn();
            if (botCol >= 0) startDrop(botCol, BOT);
        }
    }

    private void draw() {
        if (loading) {
            drawLoading();
            return;
        }

        ScreenUtils.clear(BACKGROUND);
        viewport.apply();
        spriteBatch.setProjectionMatrix(viewport.getCamera().combined);
        spriteBatch.begin();

        drawRect(BOARD_EDGE, BOARD_LEFT - 0.18f, BOARD_BOTTOM - 0.18f, COLS * CELL + 0.36f, ROWS * CELL + 0.36f);
        drawRect(BOARD_COLOR, BOARD_LEFT - 0.08f, BOARD_BOTTOM - 0.08f, COLS * CELL + 0.16f, ROWS * CELL + 0.16f);

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                float x = cellX(col);
                float y = cellY(row);
                spriteBatch.setColor(Color.WHITE);
                spriteBatch.draw(holeTexture, x, y, COIN_SIZE, COIN_SIZE);
                if (board[row][col] == PLAYER) {
                    drawCoin(playerCoinTexture, x, y, winningCells[row][col]);
                } else if (board[row][col] == BOT) {
                    drawCoin(botCoinTexture, x, y, winningCells[row][col]);
                }
            }
        }

        if (!gameOver && currentPlayer == PLAYER && hoverCol >= 0 && !coinFalling) {
            int row = nextEmptyRow(hoverCol);
            if (row >= 0) {
                spriteBatch.setColor(1f, 1f, 1f, 0.4f);
                spriteBatch.draw(playerCoinTexture, cellX(hoverCol), cellY(row), COIN_SIZE, COIN_SIZE);
                spriteBatch.setColor(Color.WHITE);
            }
        }

        if (coinFalling) {
            Texture coin = fallingPlayer == PLAYER ? playerCoinTexture : botCoinTexture;
            spriteBatch.setColor(Color.WHITE);
            spriteBatch.draw(coin, fallingX, fallingY, COIN_SIZE, COIN_SIZE);
        }

        spriteBatch.end();
        drawHud();
    }

    private void resetGame() {
        board = new int[ROWS][COLS];
        winningCells = new boolean[ROWS][COLS];
        currentPlayer = PLAYER;
        gameOver = false;
        winner = EMPTY;
        coinFalling = false;
        botThinkTimer = 0f;
        hoverCol = -1;
    }

    private void startDrop(int col, int player) {
        int row = nextEmptyRow(col);
        if (row < 0) return;
        coinFalling = true;
        fallingPlayer = player;
        fallingCol = col;
        fallingRow = row;
        fallingX = cellX(col);
        fallingTargetY = cellY(row);
        fallingY = BOARD_BOTTOM + ROWS * CELL + 0.05f;
    }

    private void finishTurn(int player) {
        if (hasFour(player)) {
            markWinningCells(player);
            gameOver = true;
            winner = player;
            return;
        }
        if (isBoardFull()) {
            gameOver = true;
            winner = EMPTY;
            return;
        }
        currentPlayer = player == PLAYER ? BOT : PLAYER;
        botThinkTimer = 0f;
    }

    private int chooseBotColumn() {
        Array<Integer> valid = validColumns();
        if (valid.size == 0) return -1;

        int bestScore = Integer.MIN_VALUE;
        Array<Integer> bestCols = new Array<Integer>();
        for (int i = 0; i < valid.size; i++) {
            int col = valid.get(i);
            int row = dropPiece(board, col, BOT);
            int score = minimax(board, SEARCH_DEPTH - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, false);
            undoDrop(board, col, row);
            if (score > bestScore) {
                bestScore = score;
                bestCols.clear();
                bestCols.add(col);
            } else if (score == bestScore) {
                bestCols.add(col);
            }
        }
        return bestCols.get(MathUtils.random(bestCols.size - 1));
    }

    private int minimax(int[][] state, int depth, int alpha, int beta, boolean maximizing) {
        if (hasFour(state, BOT)) return WIN_SCORE + depth;
        if (hasFour(state, PLAYER)) return -WIN_SCORE - depth;
        if (depth == 0 || isFull(state)) return evaluate(state);

        Array<Integer> moves = orderedColumns(state);
        if (moves.size == 0) return 0;

        if (maximizing) {
            int value = Integer.MIN_VALUE;
            for (int i = 0; i < moves.size; i++) {
                int col = moves.get(i);
                int row = dropPiece(state, col, BOT);
                value = Math.max(value, minimax(state, depth - 1, alpha, beta, false));
                undoDrop(state, col, row);
                alpha = Math.max(alpha, value);
                if (alpha >= beta) break;
            }
            return value;
        }

        int value = Integer.MAX_VALUE;
        for (int i = 0; i < moves.size; i++) {
            int col = moves.get(i);
            int row = dropPiece(state, col, PLAYER);
            value = Math.min(value, minimax(state, depth - 1, alpha, beta, true));
            undoDrop(state, col, row);
            beta = Math.min(beta, value);
            if (alpha >= beta) break;
        }
        return value;
    }

    private int evaluate(int[][] state) {
        int score = 0;
        for (int row = 0; row < ROWS; row++) {
            if (state[row][3] == BOT) score += 6;
            else if (state[row][3] == PLAYER) score -= 6;
        }
        score += scoreAllWindows(state);
        return score;
    }

    private int scoreAllWindows(int[][] state) {
        int score = 0;
        int[] window = new int[4];

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS - 3; col++) {
                for (int i = 0; i < 4; i++) window[i] = state[row][col + i];
                score += scoreWindow(window);
            }
        }
        for (int col = 0; col < COLS; col++) {
            for (int row = 0; row < ROWS - 3; row++) {
                for (int i = 0; i < 4; i++) window[i] = state[row + i][col];
                score += scoreWindow(window);
            }
        }
        for (int row = 0; row < ROWS - 3; row++) {
            for (int col = 0; col < COLS - 3; col++) {
                for (int i = 0; i < 4; i++) window[i] = state[row + i][col + i];
                score += scoreWindow(window);
            }
        }
        for (int row = 3; row < ROWS; row++) {
            for (int col = 0; col < COLS - 3; col++) {
                for (int i = 0; i < 4; i++) window[i] = state[row - i][col + i];
                score += scoreWindow(window);
            }
        }
        return score;
    }

    private int scoreWindow(int[] window) {
        int bot = 0;
        int player = 0;
        int empty = 0;
        for (int i = 0; i < 4; i++) {
            if (window[i] == BOT) bot++;
            else if (window[i] == PLAYER) player++;
            else empty++;
        }
        if (bot == 4) return 500;
        if (bot == 3 && empty == 1) return 80;
        if (bot == 2 && empty == 2) return 8;
        if (player == 4) return -500;
        if (player == 3 && empty == 1) return -100;
        if (player == 2 && empty == 2) return -8;
        return 0;
    }

    private Array<Integer> validColumns() {
        return orderedColumns(board);
    }

    private Array<Integer> orderedColumns(int[][] state) {
        int[] order = {3, 2, 4, 1, 5, 0, 6};
        Array<Integer> cols = new Array<Integer>();
        for (int i = 0; i < order.length; i++) {
            if (state[ROWS - 1][order[i]] == EMPTY) cols.add(order[i]);
        }
        return cols;
    }

    private int dropPiece(int[][] state, int col, int player) {
        int row = nextEmptyRow(state, col);
        if (row >= 0) state[row][col] = player;
        return row;
    }

    private void undoDrop(int[][] state, int col, int row) {
        if (row >= 0) state[row][col] = EMPTY;
    }

    private boolean columnHasSpace(int col) {
        return board[ROWS - 1][col] == EMPTY;
    }

    private int nextEmptyRow(int col) {
        return nextEmptyRow(board, col);
    }

    private int nextEmptyRow(int[][] state, int col) {
        for (int row = 0; row < ROWS; row++) {
            if (state[row][col] == EMPTY) return row;
        }
        return -1;
    }

    private boolean isBoardFull() {
        return isFull(board);
    }

    private boolean isFull(int[][] state) {
        for (int col = 0; col < COLS; col++) {
            if (state[ROWS - 1][col] == EMPTY) return false;
        }
        return true;
    }

    private boolean hasFour(int player) {
        return hasFour(board, player);
    }

    private boolean hasFour(int[][] state, int player) {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                if (state[row][col] != player) continue;
                if (matches(state, row, col, 0, 1, player)) return true;
                if (matches(state, row, col, 1, 0, player)) return true;
                if (matches(state, row, col, 1, 1, player)) return true;
                if (matches(state, row, col, 1, -1, player)) return true;
            }
        }
        return false;
    }

    private boolean matches(int[][] state, int row, int col, int dr, int dc, int player) {
        for (int i = 0; i < 4; i++) {
            int r = row + dr * i;
            int c = col + dc * i;
            if (r < 0 || r >= ROWS || c < 0 || c >= COLS || state[r][c] != player) return false;
        }
        return true;
    }

    private void markWinningCells(int player) {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                if (board[row][col] != player) continue;
                markIfMatch(row, col, 0, 1, player);
                markIfMatch(row, col, 1, 0, player);
                markIfMatch(row, col, 1, 1, player);
                markIfMatch(row, col, 1, -1, player);
            }
        }
    }

    private void markIfMatch(int row, int col, int dr, int dc, int player) {
        if (!matches(board, row, col, dr, dc, player)) return;
        for (int i = 0; i < 4; i++) {
            winningCells[row + dr * i][col + dc * i] = true;
        }
    }

    private int worldToColumn(float worldX) {
        if (worldX < BOARD_LEFT || worldX >= BOARD_LEFT + COLS * CELL) return -1;
        if (touchPos.y < BOARD_BOTTOM || touchPos.y > BOARD_BOTTOM + ROWS * CELL) return -1;
        return (int) ((worldX - BOARD_LEFT) / CELL);
    }

    private float cellX(int col) {
        return BOARD_LEFT + col * CELL + (CELL - COIN_SIZE) / 2f;
    }

    private float cellY(int row) {
        return BOARD_BOTTOM + row * CELL + (CELL - COIN_SIZE) / 2f;
    }

    private int columnFromKeys() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_1)) return 0;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_2)) return 1;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_3)) return 2;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_4) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_4)) return 3;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_5) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_5)) return 4;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_6) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_6)) return 5;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_7) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_7)) return 6;
        return -1;
    }

    private String statusText() {
        if (gameOver) {
            if (winner == PLAYER) return "YOU WIN!";
            if (winner == BOT) return "BOT WINS!";
            return "DRAW!";
        }
        if (coinFalling && fallingPlayer == BOT) return "BOT MOVES...";
        return currentPlayer == PLAYER ? "YOUR TURN" : "BOT MOVES...";
    }

    private Color statusColor() {
        if (gameOver) {
            if (winner == PLAYER) return PLAYER_COLOR;
            if (winner == BOT) return BOT_COLOR;
            return Color.WHITE;
        }
        return currentPlayer == PLAYER ? PLAYER_COLOR : BOT_COLOR;
    }

    private void drawCoin(Texture texture, float x, float y, boolean winning) {
        if (winning) {
            float pulse = 0.08f + 0.05f * MathUtils.sin(Gdx.graphics.getFrameId() * 0.12f);
            spriteBatch.setColor(1f, 1f, 1f, 0.55f);
            spriteBatch.draw(pixelTexture, x - pulse, y - pulse, COIN_SIZE + pulse * 2f, COIN_SIZE + pulse * 2f);
        }
        spriteBatch.setColor(Color.WHITE);
        spriteBatch.draw(texture, x, y, COIN_SIZE, COIN_SIZE);
    }

    private void drawRect(Color color, float x, float y, float width, float height) {
        spriteBatch.setColor(color);
        spriteBatch.draw(pixelTexture, x, y, width, height);
        spriteBatch.setColor(Color.WHITE);
    }

    private void drawLoading() {
        ScreenUtils.clear(0.04f, 0.05f, 0.07f, 1f);
        uiViewport.apply();
        spriteBatch.setProjectionMatrix(uiViewport.getCamera().combined);
        spriteBatch.begin();

        float logoSize = 360f;
        float logoX = (UI_WIDTH - logoSize) / 2f;
        float logoY = 210f;
        spriteBatch.setColor(Color.WHITE);
        spriteBatch.draw(logoTexture, logoX, logoY, logoSize, logoSize);

        float progress = MathUtils.clamp(loadTime / LOAD_DURATION, 0f, 1f);
        float barWidth = 320f;
        float barHeight = 16f;
        float barX = (UI_WIDTH - barWidth) / 2f;
        float barY = 150f;
        drawRect(new Color(0.16f, 0.18f, 0.22f, 1f), barX - 4f, barY - 4f, barWidth + 8f, barHeight + 8f);
        drawRect(new Color(0.22f, 0.85f, 0.28f, 1f), barX, barY, barWidth * progress, barHeight);
        drawCenteredText(font, "LOADING", UI_WIDTH / 2f, 128f, new Color(0.35f, 0.95f, 0.40f, 1f));

        spriteBatch.end();
    }

    private void drawHud() {
        uiViewport.apply();
        spriteBatch.setProjectionMatrix(uiViewport.getCamera().combined);
        spriteBatch.begin();

        drawCenteredText(titleFont, "CONNECT 4", UI_WIDTH / 2f, 698f, Color.WHITE);
        drawColorKey(32f, 632f, playerCoinTexture, "YOU", PLAYER_COLOR, true);
        drawColorKey(UI_WIDTH - 32f, 632f, botCoinTexture, "BOT", BOT_COLOR, false);
        drawCenteredText(font, statusText(), UI_WIDTH / 2f, 632f, statusColor());

        if (gameOver) {
            boolean hovered = playAgainBounds.contains(uiTouchPos.x, uiTouchPos.y);
            drawRect(hovered ? BUTTON_HOVER : BUTTON_COLOR, playAgainBounds.x, playAgainBounds.y, playAgainBounds.width, playAgainBounds.height);
            drawCenteredText(font, "PLAY AGAIN", playAgainBounds.x + playAgainBounds.width / 2f, playAgainBounds.y + 38f, Color.WHITE);
        }

        spriteBatch.end();
    }

    private void drawColorKey(float x, float y, Texture coin, String label, Color color, boolean labelAfterCoin) {
        float coinSize = 28f;
        float coinY = y - 24f;
        layout.setText(font, label);
        spriteBatch.setColor(Color.WHITE);
        if (labelAfterCoin) {
            spriteBatch.draw(coin, x, coinY, coinSize, coinSize);
            font.setColor(color);
            font.draw(spriteBatch, label, x + coinSize + 10f, y);
        } else {
            font.setColor(color);
            font.draw(spriteBatch, label, x - layout.width - coinSize - 10f, y);
            spriteBatch.draw(coin, x - coinSize, coinY, coinSize, coinSize);
        }
    }

    private void drawCenteredText(BitmapFont drawFont, String text, float x, float y, Color color) {
        layout.setText(drawFont, text);
        drawFont.setColor(color);
        drawFont.draw(spriteBatch, text, x - layout.width / 2f, y);
    }

    private void drawLeftText(BitmapFont drawFont, String text, float x, float y, Color color) {
        drawFont.setColor(color);
        drawFont.draw(spriteBatch, text, x, y);
    }

    private void drawRightText(BitmapFont drawFont, String text, float x, float y, Color color) {
        layout.setText(drawFont, text);
        drawFont.setColor(color);
        drawFont.draw(spriteBatch, text, x - layout.width, y);
    }

    private BitmapFont createHudFont(int size) {
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/Anton-Regular.ttf"));
        FreeTypeFontParameter parameter = new FreeTypeFontParameter();
        parameter.size = size;
        parameter.color = Color.WHITE;
        parameter.borderWidth = 3f;
        parameter.borderColor = Color.BLACK;
        parameter.borderStraight = true;
        parameter.minFilter = Texture.TextureFilter.Linear;
        parameter.magFilter = Texture.TextureFilter.Linear;
        parameter.spaceX = 1;
        BitmapFont created = generator.generateFont(parameter);
        created.setUseIntegerPositions(true);
        generator.dispose();
        return created;
    }

    private Texture createSolidTexture(Color color) {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    private Texture createCircleTexture(Color color) {
        int size = 128;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(0, 0, 0, 0);
        pixmap.fill();
        pixmap.setBlending(Pixmap.Blending.SourceOver);
        pixmap.setColor(color);
        pixmap.fillCircle(size / 2, size / 2, size / 2 - 2);
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        return texture;
    }

    private Texture createCoinTexture(Color fill, Color rim, Color highlight) {
        int size = 128;
        int center = size / 2;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(0, 0, 0, 0);
        pixmap.fill();
        pixmap.setBlending(Pixmap.Blending.SourceOver);
        pixmap.setColor(rim);
        pixmap.fillCircle(center, center, center - 2);
        pixmap.setColor(fill);
        pixmap.fillCircle(center, center, center - 10);
        pixmap.setColor(highlight);
        pixmap.fillCircle(center - 18, center - 18, 16);
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        return texture;
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        uiViewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        spriteBatch.dispose();
        font.dispose();
        titleFont.dispose();
        pixelTexture.dispose();
        playerCoinTexture.dispose();
        botCoinTexture.dispose();
        holeTexture.dispose();
        logoTexture.dispose();
    }
}
