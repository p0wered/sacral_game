package io.github.sacral_game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapObjects;
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

public class GameScreen implements Screen {
    private OrthographicCamera camera;
    private SpriteBatch batch;
    private Vector2 position;
    private float speed = 200;
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

    public GameScreen() {
        camera = new OrthographicCamera();
        camera.setToOrtho(false, 1280, 720);
        batch = new SpriteBatch();
        position = new Vector2(640, 360);
        shapeRenderer = new ShapeRenderer();
        playerCollisionRect = new Rectangle();
        loadMap(); // Загружаем карту перед анимациями, чтобы получить размер тайла
        loadAnimations();
        updatePlayerCollisionRect();
    }

    private void updatePlayerCollisionRect() {
        TextureRegion frame = getCurrentFrame();
        float width = frame.getRegionWidth() * scale * 0.3f;  // Уменьшаем ширину
        float height = frame.getRegionHeight() * scale * 0.45f;  // Уменьшаем высоту, но не так сильно как ширину
        playerCollisionRect.set(
            position.x - width / 2,
            position.y - height / 4,  // Смещаем прямоугольник вниз
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
        float desiredHeight = tileHeight * 2f; // Уменьшаем высоту персонажа до 1.5 тайла
        scale = desiredHeight / frame.getRegionHeight();
    }

    private void loadMap() {
        map = new TmxMapLoader().load("../assets/Test_Map.tmx");
        mapRenderer = new OrthogonalTiledMapRenderer(map);

        tileWidth = map.getProperties().get("tilewidth", Integer.class);
        tileHeight = map.getProperties().get("tileheight", Integer.class);
        tileSize = Math.max(tileWidth, tileHeight); // Используем максимальный размер для масштабирования

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
                System.out.println("Collision object: x=" + tileObject.getX() + ", y=" + tileObject.getY() +
                    ", width=" + tileWidth + ", height=" + tileHeight);
            }
        }
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0.2f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stateTime += delta;

        camera.position.set(position, 0);
        camera.update();

        mapRenderer.setView(camera);
        mapRenderer.render();

        batch.setProjectionMatrix(camera.combined);

        handleInput(delta);
        updatePlayerCollisionRect();

        // Отрисовка объектов коллизии
        batch.begin();
        for (MapObject object : collisionObjects) {
            if (object instanceof TiledMapTileMapObject) {
                TiledMapTileMapObject tileObject = (TiledMapTileMapObject) object;
                TiledMapTile tile = tileObject.getTile();
                if (tile != null && tile.getTextureRegion() != null) {
                    batch.draw(tile.getTextureRegion(), tileObject.getX(), tileObject.getY());
                }
            }
        }
        batch.end();

        // Отрисовка игрока
        batch.begin();
        TextureRegion currentFrame = getCurrentFrame();
        batch.draw(currentFrame,
            facingRight ? position.x - currentFrame.getRegionWidth() * scale / 2 : position.x + currentFrame.getRegionWidth() * scale / 2,
            position.y - currentFrame.getRegionHeight() * scale / 3,  // Смещаем спрайт вниз
            facingRight ? currentFrame.getRegionWidth() * scale : -currentFrame.getRegionWidth() * scale,
            currentFrame.getRegionHeight() * scale);
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

        // Отладочное рисование объектов коллизии и игрока
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(1, 0, 0, 1); // Красный цвет для объектов
        for (MapObject object : collisionObjects) {
            if (object instanceof TiledMapTileMapObject) {
                TiledMapTileMapObject tileObject = (TiledMapTileMapObject) object;
                shapeRenderer.rect(tileObject.getX(), tileObject.getY(), tileWidth, tileHeight);
            }
        }
        shapeRenderer.setColor(0, 1, 0, 1); // Зеленый цвет для игрока
        shapeRenderer.rect(playerCollisionRect.x, playerCollisionRect.y, playerCollisionRect.width, playerCollisionRect.height);
        shapeRenderer.end();
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
                Rectangle tileRect = new Rectangle(tileObject.getX(), tileObject.getY(), tileWidth, tileHeight);
                if (Intersector.overlaps(tileRect, playerCollisionRect)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void resize(int width, int height) {
        camera.setToOrtho(false, width, height);
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
