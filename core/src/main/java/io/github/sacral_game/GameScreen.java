package io.github.sacral_game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
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
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import java.util.ArrayList;
import java.util.Collections;

public class GameScreen implements Screen {
    private OrthographicCamera camera;
    private Viewport viewport;
    private SpriteBatch batch;
    private ShapeRenderer shapeRenderer;
    private Player player;

    // Карта
    private TiledMap map;
    private OrthogonalTiledMapRenderer mapRenderer;
    private MapObjects collisionObjects;
    private float tileSize;
    private int tileWidth;
    private int tileHeight;

    private static final float VIEWPORT_WIDTH = 640;
    private static final float VIEWPORT_HEIGHT = 360;
    private static final boolean DEBUG_MODE = true;

    private ArrayList<DrawableObject> drawableObjects;

    public GameScreen() {
        initializeBaseComponents();
        loadMap();
        createPlayer();
        drawableObjects = new ArrayList<>();
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
        tileSize = Math.max(tileWidth, tileHeight);

        MapLayer collisionLayer = map.getLayers().get("Collision_1");
        if (collisionLayer != null) {
            collisionObjects = collisionLayer.getObjects();
        } else {
            Gdx.app.error("GameScreen", "Collision layer not found");
        }
    }

    private void createPlayer() {
        float tileSize = 32;
        float startX = VIEWPORT_WIDTH / 2;
        float startY = 100;
        player = new Player(startX, startY, 170f, tileSize);
    }

    @Override
    public void render(float delta) {
        update(delta);
        draw(delta);
    }

    private void update(float delta) {
        player.update(delta, map, collisionObjects);
        updateCamera();
    }

    private void updateCamera() {
        camera.position.set(player.getPosition(), 0);
        camera.update();
    }

    private void draw(float delta) {
        clearScreen();
        viewport.apply();

        // Рендеринг карты
        mapRenderer.setView(camera);
        mapRenderer.render(new int[]{0});

        // Подготовка объектов для отрисовки
        prepareDrawableObjects();

        // Отрисовка всех объектов
        drawObjects();

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

        // Добавление объектов карты
        for (MapObject object : collisionObjects) {
            if (object instanceof TiledMapTileMapObject) {
                TiledMapTileMapObject tileObject = (TiledMapTileMapObject) object;
                TiledMapTile tile = tileObject.getTile();
                if (tile != null && tile.getTextureRegion() != null) {
                    Sprite sprite = new Sprite(tile.getTextureRegion());
                    sprite.setPosition(tileObject.getX(), tileObject.getY());
                    drawableObjects.add(new DrawableObject(sprite, tileObject.getY(), false));
                }
            }
        }

        // Добавление игрока
        Sprite playerSprite = player.getSprite();
        drawableObjects.add(new DrawableObject(
            playerSprite,
            player.getPosition().y,
            !player.isFacingRight()
        ));

        // Сортировка объектов по Y-координате
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

    private void drawDebug() {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        // Отрисовка коллизий объектов
        shapeRenderer.setColor(1, 0, 0, 1);
        for (MapObject object : collisionObjects) {
            if (object instanceof TiledMapTileMapObject tileObject) {
                TiledMapTile tile = tileObject.getTile();
                Rectangle collisionRect = getTileCollisionRectangle(tile, tileObject.getX(), tileObject.getY());
                shapeRenderer.rect(collisionRect.x, collisionRect.y,
                    collisionRect.width, collisionRect.height);
            }
        }

        // Отрисовка коллизии игрока
        shapeRenderer.setColor(0, 1, 0, 1);
        Rectangle playerRect = player.getCollisionRect();
        shapeRenderer.rect(playerRect.x, playerRect.y,
            playerRect.width, playerRect.height);

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
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        camera.position.set(camera.viewportWidth / 2, camera.viewportHeight / 2, 0);
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapeRenderer.dispose();
        map.dispose();
        mapRenderer.dispose();
        player.dispose();
    }

    private static class DrawableObject implements Comparable<DrawableObject> {
        Sprite sprite;
        float y;
        boolean flipX;

        DrawableObject(Sprite sprite, float y, boolean flipX) {
            this.sprite = sprite;
            this.y = y;
            this.flipX = flipX;
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
