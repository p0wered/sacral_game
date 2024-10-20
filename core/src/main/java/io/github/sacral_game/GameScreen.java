package io.github.sacral_game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import java.util.ArrayList;
import java.util.Collections;

public class GameScreen implements Screen {
    private OrthographicCamera camera;
    private Viewport viewport;
    private SpriteBatch batch;
    private Vector2 position;
    private float speed = 170;
    private float tileSize;

    private Texture spritesheet;
    private Animation<TextureRegion> idleAnimation;
    private Animation<TextureRegion> walkAnimation;
    private Animation<TextureRegion> attackAnimation;
    private Rectangle playerCollisionRect;
    private float stateTime;
    private String currentState = "IDLE";
    private boolean facingRight = true;

    private float scale = 1.0f;

    // Новые переменные для карты
    private TiledMap map;
    private OrthogonalTiledMapRenderer mapRenderer;
    private MapObjects collisionObjects;
    private ShapeRenderer shapeRenderer;
    private int tileWidth;
    private int tileHeight;

    private class DrawableObject implements Comparable<DrawableObject> {
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

    private static final float VIEWPORT_WIDTH = 640; // Половина от текущей ширины
    private static final float VIEWPORT_HEIGHT = 360; // Половина от текущей высоты

    public GameScreen() {
        camera = new OrthographicCamera();
        viewport = new FitViewport(VIEWPORT_WIDTH, VIEWPORT_HEIGHT, camera);
        batch = new SpriteBatch();
        position = new Vector2(VIEWPORT_WIDTH / 2, VIEWPORT_HEIGHT / 2);
        shapeRenderer = new ShapeRenderer();
        playerCollisionRect = new Rectangle();
        loadMap();
        loadAnimations();
        updatePlayerCollisionRect();
    }

    private void updatePlayerCollisionRect() {
        TextureRegion frame = getCurrentFrame();
        float width = frame.getRegionWidth() * scale * 0.3f;  // Уменьшаем ширину
        float height = frame.getRegionHeight() * scale * 0.15f;  // Уменьшаем высоту, но не так сильно как ширину
        playerCollisionRect.set(
            position.x - width / 2,
            position.y - height,  // Смещаем прямоугольник вниз
            width,
            height
        );
    }

    private void loadAnimations() {
        spritesheet = new Texture("../assets/character.png");
        TextureRegion[][] tmp = TextureRegion.split(spritesheet,
            spritesheet.getWidth() / 6, spritesheet.getHeight() / 5);

        TextureRegion[] idleFrames = new TextureRegion[6];
        TextureRegion[] walkFrames = new TextureRegion[6];
        TextureRegion[] attackFrames = new TextureRegion[6];

        for (int i = 0; i < 6; i++) {
            idleFrames[i] = tmp[0][i];
            walkFrames[i] = tmp[1][i];
            attackFrames[i] = tmp[3][i];
        }

        idleAnimation = new Animation<>(0.1f, idleFrames);
        walkAnimation = new Animation<>(0.1f, walkFrames);
        attackAnimation = new Animation<>(0.075f, attackFrames);

        // Устанавливаем масштаб спрайта в соответствии с размером тайла
        scale = tileSize / idleFrames[0].getRegionHeight();

        // Настройка масштаба относительно тайла
        TextureRegion frame = idleAnimation.getKeyFrame(0);
        float desiredHeight = tileHeight * 2f; // Уменьшаем высоту персонажа до 2 тайлов
        scale = desiredHeight / frame.getRegionHeight();
    }

    private void loadMap() {
        map = new TmxMapLoader().load("../assets/Test_Map.tmx");
        mapRenderer = new OrthogonalTiledMapRenderer(map);

        tileWidth = map.getProperties().get("tilewidth", Integer.class);
        tileHeight = map.getProperties().get("tileheight", Integer.class);
        tileSize = Math.max(tileWidth, tileHeight);

        MapLayer collisionLayer = map.getLayers().get("Collision_1");
        if (collisionLayer == null) {
            Gdx.app.error("GameScreen", "Collision layer not found");
            return;
        }

        collisionObjects = collisionLayer.getObjects();
        System.out.println("Total collision objects: " + collisionObjects.getCount());

        for (MapObject object : collisionObjects) {
            if (object instanceof TiledMapTileMapObject) {
                TiledMapTileMapObject tileObject = (TiledMapTileMapObject) object;
                TiledMapTile tile = tileObject.getTile();
                Rectangle collisionRect = getTileCollisionRectangle(tile, tileObject.getX(), tileObject.getY());
                System.out.println("Collision object: x=" + collisionRect.x + ", y=" + collisionRect.y +
                    ", width=" + collisionRect.width + ", height=" + collisionRect.height);
            }
        }
    }

    private Rectangle getTileCollisionRectangle(TiledMapTile tile, float x, float y) {
        MapObjects objects = tile.getObjects();
        for (MapObject object : objects) {
            if (object instanceof RectangleMapObject) {
                Rectangle rect = ((RectangleMapObject) object).getRectangle();
                return new Rectangle(x + rect.x, y + rect.y, rect.width, rect.height);
            }
        }
        return new Rectangle(x, y, tileWidth, tileHeight);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0.2f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stateTime += delta;

        camera.position.set(position, 0);
        camera.update();

        viewport.apply();

        mapRenderer.setView(camera);
        mapRenderer.render(new int[]{0}); // Рендерим только базовый слой карты

        batch.setProjectionMatrix(camera.combined);

        handleInput(delta);
        updatePlayerCollisionRect();

        // Создаем список объектов для отрисовки
        ArrayList<DrawableObject> drawableObjects = new ArrayList<>();

        // Добавляем объекты коллизии
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

        // Добавляем игрока
        TextureRegion currentFrame = getCurrentFrame();
        Sprite playerSprite = new Sprite(currentFrame);
        float spriteWidth = currentFrame.getRegionWidth() * scale;
        float spriteHeight = currentFrame.getRegionHeight() * scale;

        playerSprite.setSize(spriteWidth, spriteHeight);
        playerSprite.setOrigin(spriteWidth / 2, spriteHeight / 3); // Устанавливаем точку вращения
        playerSprite.setPosition(
            position.x - spriteWidth / 2,
            position.y - spriteHeight / 3
        );
        drawableObjects.add(new DrawableObject(playerSprite, position.y, !facingRight));

        // Сортируем объекты по Y-координате (сверху вниз)
        Collections.sort(drawableObjects);

        // Отрисовываем отсортированные объекты
        batch.begin();
        for (DrawableObject obj : drawableObjects) {
            obj.sprite.setFlip(obj.flipX, false);
            obj.sprite.draw(batch);
        }
        batch.end();

        switch (currentState) {
            case "WALK":
                walkAnimation.getKeyFrame(stateTime, true);
                break;
            case "ATTACK":
                attackAnimation.getKeyFrame(stateTime, false);
                if (attackAnimation.isAnimationFinished(stateTime)) {
                    currentState = "IDLE";
                }
                break;
            default:
                idleAnimation.getKeyFrame(stateTime, true);
        }

        /* Отладочное рисование объектов коллизии и игрока
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(1, 0, 0, 1); // Красный цвет для объектов
        for (MapObject object : collisionObjects) {
            if (object instanceof TiledMapTileMapObject) {
                TiledMapTileMapObject tileObject = (TiledMapTileMapObject) object;
                TiledMapTile tile = tileObject.getTile();
                Rectangle collisionRect = getTileCollisionRectangle(tile, tileObject.getX(), tileObject.getY());
                shapeRenderer.rect(collisionRect.x, collisionRect.y, collisionRect.width, collisionRect.height);
            }
        }
        shapeRenderer.setColor(0, 1, 0, 1); // Зеленый цвет для игрока
        shapeRenderer.rect(playerCollisionRect.x, playerCollisionRect.y, playerCollisionRect.width, playerCollisionRect.height);
        shapeRenderer.end(); */
    }

    private TextureRegion getCurrentFrame() {
        switch (currentState) {
            case "WALK":
                return walkAnimation.getKeyFrame(stateTime, true);
            case "ATTACK":
                TextureRegion frame = attackAnimation.getKeyFrame(stateTime, false);
                if (attackAnimation.isAnimationFinished(stateTime)) {
                    currentState = "IDLE";
                }
                return frame;
            default:
                return idleAnimation.getKeyFrame(stateTime, true);
        }
    }

    private void handleInput(float delta) {
        boolean isMoving = false;
        Vector2 oldPosition = new Vector2(position);

        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            position.x -= speed * delta;
            facingRight = false;
            isMoving = true;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            position.x += speed * delta;
            facingRight = true;
            isMoving = true;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            position.y += speed * delta;
            isMoving = true;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            position.y -= speed * delta;
            isMoving = true;
        }

        if (Controllers.getControllers().size > 0) {
            Controller controller = Controllers.getControllers().first();
            float axisX = controller.getAxis(0);
            float axisY = controller.getAxis(1);

            if (Math.abs(axisX) > 0.1f) {
                position.x += axisX * speed * delta;
                facingRight = axisX > 0;
                isMoving = true;
            }
            if (Math.abs(axisY) > 0.1f) {
                position.y -= axisY * speed * delta;
                isMoving = true;
            }

            if (controller.getButton(7)) {
                currentState = "ATTACK";
                stateTime = 0;
            }
        }

        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            currentState = "ATTACK";
            stateTime = 0;
        }

        if (isMoving && currentState != "ATTACK") {
            currentState = "WALK";
        } else if (!isMoving && currentState != "ATTACK") {
            currentState = "IDLE";
        }

        updatePlayerCollisionRect();

        if (checkCollision()) {
            position.set(oldPosition);
            updatePlayerCollisionRect();
        }

        // Ограничение движения в пределах карты
        float mapWidth = map.getProperties().get("width", Integer.class) * map.getProperties().get("tilewidth", Integer.class);
        float mapHeight = map.getProperties().get("height", Integer.class) * map.getProperties().get("tileheight", Integer.class);
        float spriteWidth = walkAnimation.getKeyFrame(0).getRegionWidth() * scale;
        float spriteHeight = walkAnimation.getKeyFrame(0).getRegionHeight() * scale;
        position.x = Math.max(spriteWidth / 2, Math.min(mapWidth - spriteWidth / 2, position.x));
        position.y = Math.max(spriteHeight / 2, Math.min(mapHeight - spriteHeight / 2, position.y));
    }

    private boolean checkCollision() {
        for (MapObject object : collisionObjects) {
            if (object instanceof TiledMapTileMapObject) {
                TiledMapTileMapObject tileObject = (TiledMapTileMapObject) object;
                TiledMapTile tile = tileObject.getTile();
                Rectangle tileRect = getTileCollisionRectangle(tile, tileObject.getX(), tileObject.getY());
                if (Intersector.overlaps(tileRect, playerCollisionRect)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        camera.position.set(camera.viewportWidth / 2, camera.viewportHeight / 2, 0);
    }


    @Override
    public void show() {}

    @Override
    public void hide() {}

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void dispose() {
        batch.dispose();
        spritesheet.dispose();
        map.dispose();
        mapRenderer.dispose();
    }
}
