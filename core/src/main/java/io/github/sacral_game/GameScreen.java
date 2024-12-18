package io.github.sacral_game;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

public class GameScreen implements Screen {
    private OrthographicCamera camera;
    private final Vector2 cameraTarget = new Vector2();
    private Viewport viewport;
    private SpriteBatch batch;
    private ShapeRenderer shapeRenderer;
    private Player player;
    private ArrayList<Enemy> enemies;
    private boolean isGameOver = false;
    private Game game;
    private Stage gameOverStage;
    private BitmapFont font;

    private TiledMap map;
    private OrthogonalTiledMapRenderer mapRenderer;
    private MapObjects collisionObjects;
    private MapObjects noCollisionObjects1;
    private MapObjects noCollisionObjects2;
    private int tileWidth;
    private int tileHeight;

    private int enemyGenerationCount = 0;
    private int baseEnemySpawnCount = 2;
    private float enemySpawnMultiplier = 1.2f;

    private static final float VIEWPORT_WIDTH = 640;
    private static final float VIEWPORT_HEIGHT = 360;
    private static final boolean DEBUG_MODE = false;

    private ArrayList<DrawableObject> drawableObjects;

    public GameScreen(Game game) {
        this.game = game;
        initializeBaseComponents();
        loadMap();
        createPlayer();
        drawableObjects = new ArrayList<>();
        enemies = new ArrayList<>();

        gameOverStage = new Stage(viewport);
        font = new BitmapFont();
        font.getData().setScale(2);

        updateCameraInstantly();
        spawnEnemy();
        spawnEnemy();
        spawnEnemy();
    }

    private Enemy spawnNewEnemy() {
        float leftBound = camera.position.x - viewport.getWorldWidth() / 2;
        float rightBound = camera.position.x + viewport.getWorldWidth() / 2;
        float bottomBound = camera.position.y - viewport.getWorldHeight() / 2;
        float topBound = camera.position.y + viewport.getWorldHeight() / 2;
        float spawnMargin = 40f;

        float collisionCheckSize = 48f;

        float x, y;
        boolean validPosition;
        int attempts = 0;

        do {
            validPosition = true;
            int side = MathUtils.random(3);

            y = switch (side) {
                case 0 -> {
                    x = MathUtils.random(leftBound - spawnMargin, rightBound + spawnMargin);
                    yield topBound + spawnMargin;
                }
                case 1 -> {
                    x = rightBound + spawnMargin;
                    yield MathUtils.random(bottomBound - spawnMargin, topBound + spawnMargin);
                }
                case 2 -> {
                    x = MathUtils.random(leftBound - spawnMargin, rightBound + spawnMargin);
                    yield bottomBound - spawnMargin;
                }
                default -> {
                    x = leftBound - spawnMargin;
                    yield MathUtils.random(bottomBound - spawnMargin, topBound + spawnMargin);
                }
            };

            Rectangle spawnRect = new Rectangle(
                x - collisionCheckSize/2,
                y - collisionCheckSize/2,
                collisionCheckSize,
                collisionCheckSize
            );

            for (MapObject object : collisionObjects) {
                if (object instanceof RectangleMapObject) {
                    Rectangle rect = ((RectangleMapObject) object).getRectangle();
                    Rectangle expandedRect = new Rectangle(
                        rect.x - 5,
                        rect.y - 5,
                        rect.width + 10,
                        rect.height + 10
                    );
                    if (expandedRect.overlaps(spawnRect)) {
                        validPosition = false;
                        break;
                    }
                }
            }

            if (!validPosition) {
                attempts++;
                continue;
            }

            for (Enemy enemy : enemies) {
                Rectangle enemyRect = new Rectangle(
                    enemy.getPosition().x - 25f,
                    enemy.getPosition().y - 25f,
                    50f,
                    50f
                );
                if (spawnRect.overlaps(enemyRect)) {
                    validPosition = false;
                    break;
                }
            }

            Rectangle playerRect = new Rectangle(
                player.getPosition().x - 100f,
                player.getPosition().y - 100f,
                200f,
                200f
            );
            if (spawnRect.overlaps(playerRect)) {
                validPosition = false;
            }

            attempts++;
        } while (!validPosition && attempts < 20);

        if (validPosition) {
            return new Enemy(x, y);
        } else {
            System.out.println("Unable to spawn enemy after 20 attempts");
            return null;
        }
    }

    private void spawnEnemy() {
        Enemy enemy = spawnNewEnemy();
        if (enemy != null) {
            enemies.add(enemy);
        }
    }

    private int calculateNewEnemyCount() {
        return Math.round(baseEnemySpawnCount * (float)Math.pow(enemySpawnMultiplier, enemyGenerationCount));
    }

    private void initializeBaseComponents() {
        camera = new OrthographicCamera();
        viewport = new FitViewport(VIEWPORT_WIDTH, VIEWPORT_HEIGHT, camera);
        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
    }

    private void loadMap() {
        map = new TmxMapLoader().load("../assets/Test_Map.tmx");
        mapRenderer = new OrthogonalTiledMapRenderer(map);

        tileWidth = map.getProperties().get("tilewidth", Integer.class);
        tileHeight = map.getProperties().get("tileheight", Integer.class);

        MapLayer collisionLayer = map.getLayers().get("Collision_1");
        if (collisionLayer != null) {
            collisionObjects = collisionLayer.getObjects();
        } else {
            Gdx.app.error("GameScreen", "Collision layer not found");
        }


        MapLayer noCollisionLayer1 = map.getLayers().get("No_Collision_1");
        if (noCollisionLayer1 != null) {
            noCollisionObjects1 = noCollisionLayer1.getObjects();
        }

        MapLayer noCollisionLayer2 = map.getLayers().get("No_Collision_2");
        if (noCollisionLayer2 != null) {
            noCollisionObjects2 = noCollisionLayer2.getObjects();
        }
    }

    private void createPlayer() {
        float tileSize = 32;
        float startX = 1400;
        float startY = 1400;
        player = new Player(startX, startY, 200f, tileSize);
    }

    private void drawHUD() {
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        float barWidth = 100;
        float barHeight = 10;
        float barX = camera.position.x - viewport.getWorldWidth() / 2 + 10;
        float barY = camera.position.y + viewport.getWorldHeight() / 2 - 20;

        shapeRenderer.setColor(Color.RED);
        shapeRenderer.rect(barX, barY, barWidth, barHeight);

        shapeRenderer.setColor(Color.GREEN);
        shapeRenderer.rect(barX, barY, barWidth * player.getHealthPercent(), barHeight);

        shapeRenderer.end();
    }

    private void update(float delta) {
        player.update(delta, map, collisionObjects);

        if (player.isDead() && player.isDeathAnimationFinished()) {
            isGameOver = true;
            return;
        }

        ArrayList<Enemy> newEnemies = new ArrayList<>();

        for (Iterator<Enemy> iterator = enemies.iterator(); iterator.hasNext();) {
            Enemy enemy = iterator.next();
            enemy.update(delta, player, collisionObjects, enemies);

            if (enemy.isDead() && enemy.isDeathAnimationComplete()) {
                iterator.remove();

                int newEnemyCount = calculateNewEnemyCount();

                for (int i = 0; i < newEnemyCount; i++) {
                    Enemy newEnemy = spawnNewEnemy();
                    if (newEnemy != null) {
                        newEnemies.add(newEnemy);
                    }
                }

                enemyGenerationCount++;
            }
        }

        enemies.addAll(newEnemies);
        updateCamera();
    }

    private void updateCamera() {
        float lerpSpeed = 0.05f;
        cameraTarget.set(player.getPosition());
        camera.position.x = camera.position.x + (cameraTarget.x - camera.position.x) * lerpSpeed;
        camera.position.y = camera.position.y + (cameraTarget.y - camera.position.y + 25) * lerpSpeed;

        camera.update();
    }

    private void updateCameraInstantly() {
        cameraTarget.set(player.getPosition());
        camera.position.x = cameraTarget.x;
        camera.position.y = cameraTarget.y + 25;
        camera.update();
    }

    private void draw(float delta) {
        clearScreen();
        viewport.apply();

        mapRenderer.setView(camera);
        mapRenderer.render(new int[]{0}); // Отрисовка слоя фона карты
        prepareDrawableObjects();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        for (DrawableObject obj : drawableObjects) {
            obj.sprite.setFlip(obj.flipX, false);
            obj.sprite.draw(batch);
        }
        batch.end();

        if (DEBUG_MODE) {
            drawDebug();
        }
    }

    private void clearScreen() {
        Gdx.gl.glClearColor(0, 0, 0.2f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }

    private void prepareDrawableObjects() {
        drawableObjects.clear();

        for (MapObject object : collisionObjects) {
            if (object instanceof TiledMapTileMapObject tileObject) {
                addTileObjectToDrawable(tileObject);
            }
        }

        if (noCollisionObjects1 != null) {
            for (MapObject object : noCollisionObjects1) {
                if (object instanceof TiledMapTileMapObject tileObject) {
                    addTileObjectToDrawable(tileObject);
                }
            }
        }

        // Добавляем объекты без коллизии со второго слоя
        if (noCollisionObjects2 != null) {
            for (MapObject object : noCollisionObjects2) {
                if (object instanceof TiledMapTileMapObject tileObject) {
                    addTileObjectToDrawable(tileObject);
                }
            }
        }

        Sprite playerSprite = player.getSprite();
        drawableObjects.add(new DrawableObject(
            playerSprite,
            player.getPosition().y
        ));

        for (Enemy enemy : enemies) {
            TextureRegion currentFrame = enemy.getCurrentFrame();
            Sprite enemySprite = new Sprite(currentFrame);
            enemySprite.setPosition(enemy.getPosition().x, enemy.getPosition().y);
            enemySprite.setSize(40f, 40f);

            DrawableObject enemyDrawable = new DrawableObject(
                enemySprite,
                enemy.getPosition().y
            );
            drawableObjects.add(enemyDrawable);
        }

        Collections.sort(drawableObjects);
    }

    private void drawObjects() {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        for (DrawableObject obj : drawableObjects) {
            obj.sprite.setFlip(obj.flipX, false);
            obj.sprite.draw(batch);
        }
        batch.end();
    }

    private void addTileObjectToDrawable(TiledMapTileMapObject tileObject) {
        TiledMapTile tile = tileObject.getTile();
        if (tile != null && tile.getTextureRegion() != null) {
            Sprite sprite = new Sprite(tile.getTextureRegion());
            sprite.setPosition(tileObject.getX(), tileObject.getY());
            drawableObjects.add(new DrawableObject(sprite, tileObject.getY()));
        }
    }

    private void drawDebug() {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        shapeRenderer.setColor(1, 0, 0, 1);
        for (MapObject object : collisionObjects) {
            if (object instanceof TiledMapTileMapObject tileObject) {
                TiledMapTile tile = tileObject.getTile();
                Rectangle collisionRect = getTileCollisionRectangle(tile, tileObject.getX(), tileObject.getY());
                shapeRenderer.rect(collisionRect.x, collisionRect.y, collisionRect.width, collisionRect.height);
            }
        }

        shapeRenderer.setColor(0, 1, 0, 1);
        Rectangle playerRect = player.getCollisionRect();
        shapeRenderer.rect(playerRect.x, playerRect.y, playerRect.width, playerRect.height);

        shapeRenderer.setColor(1, 0, 1, 1); // Фиолетовый цвет
        if (player.isAttacking()) { // Проверяем, атакует ли игрок
            Rectangle attackRect = player.getAttackRect();
            shapeRenderer.rect(attackRect.x, attackRect.y, attackRect.width, attackRect.height);
        }

        shapeRenderer.setColor(1, 1, 0, 1);
        for (Enemy enemy : enemies) {
            Rectangle enemyRect = enemy.getCollisionRect();
            shapeRenderer.rect(enemyRect.x, enemyRect.y, enemyRect.width, enemyRect.height);
        }

        shapeRenderer.end();
    }

    private Rectangle getTileCollisionRectangle(TiledMapTile tile, float x, float y) {
        if (tile != null) {
            MapObjects objects = tile.getObjects();
            for (MapObject object : objects) {
                if (object instanceof RectangleMapObject) {
                    Rectangle rect = ((RectangleMapObject) object).getRectangle();
                    return new Rectangle(x + rect.x, y + rect.y, rect.width, rect.height);
                }
            }
        }
        return new Rectangle(x, y, tileWidth, tileHeight);
    }

    @Override
    public void render(float delta) {
        if (isGameOver) {
            showGameOverScreen();
            return;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            player.attack(enemies);
        }

        update(delta);
        draw(delta);
        drawHUD();
    }

    private void showGameOverScreen() {
        draw(Gdx.graphics.getDeltaTime());

        if (gameOverStage.getActors().size == 0) {
            Viewport viewport = new FitViewport(1280, 720);
            gameOverStage.setViewport(viewport);
            viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
            Gdx.input.setInputProcessor(gameOverStage);

            Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pixmap.setColor(0, 0, 0, 0.7f);
            pixmap.fill();
            Texture dimTexture = new Texture(pixmap);
            pixmap.dispose();

            Image dimBackground = new Image(dimTexture);
            dimBackground.setSize(viewport.getWorldWidth(), viewport.getWorldHeight());
            dimBackground.setPosition(0, 0);

            gameOverStage.addActor(dimBackground);

            Table table = new Table();
            table.setFillParent(true);

            FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("../assets/font.ttf"));
            FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
            parameter.size = 32;
            parameter.characters = FreeTypeFontGenerator.DEFAULT_CHARS + "абвгдеёжзийклмнопрстуфхцчшщъыьэюяАБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ";
            font = generator.generateFont(parameter);

            FreeTypeFontGenerator.FreeTypeFontParameter titleParameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
            titleParameter.size = 48;
            titleParameter.characters = FreeTypeFontGenerator.DEFAULT_CHARS + "абвгдеёжзийклмнопрстуфхцчшщъыьэюяАБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ";
            BitmapFont titleFont = generator.generateFont(titleParameter);

            generator.dispose();

            Label.LabelStyle titleStyle = new Label.LabelStyle();
            titleStyle.font = titleFont;

            Label gameOverLabel = new Label("ВЫ ПОМЕРЛИ!", titleStyle);

            TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
            buttonStyle.font = font;
            buttonStyle.up = new TextureRegionDrawable(new TextureRegion(new Texture(Gdx.files.internal("button_bg.png"))));

            TextButton restartButton = new TextButton("Рестарт", buttonStyle);
            restartButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    game.setScreen(new GameScreen(game));
                }
            });

            TextButton menuButton = new TextButton("В главное меню", buttonStyle);
            menuButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    game.setScreen(new MainMenuScreen(game));
                }
            });

            table.add(gameOverLabel).padBottom(50).row();
            table.add(restartButton).size(390, 140).padBottom(20).row();
            table.add(menuButton).size(390, 140);

            gameOverStage.addActor(table);

            gameOverStage.getCamera().position.set(
                gameOverStage.getViewport().getWorldWidth() / 2,
                gameOverStage.getViewport().getWorldHeight() / 2,
                0
            );
        }

        gameOverStage.act(Gdx.graphics.getDeltaTime());
        gameOverStage.draw();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        camera.position.set(camera.viewportWidth / 2, camera.viewportHeight / 2, 0);
        gameOverStage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapeRenderer.dispose();
        map.dispose();
        mapRenderer.dispose();
        player.dispose();
        gameOverStage.dispose();
        font.dispose();
    }

    private static class DrawableObject implements Comparable<DrawableObject> {
        Sprite sprite;
        float y;
        boolean flipX;

        DrawableObject(Sprite sprite, float y) {
            this.sprite = sprite;
            this.y = y;
        }

        @Override
        public int compareTo(DrawableObject other) {
            return Float.compare(other.y, this.y);
        }
    }

    @Override public void show() {}
    @Override public void hide() {}
    @Override public void pause() {}
    @Override public void resume() {}
}
