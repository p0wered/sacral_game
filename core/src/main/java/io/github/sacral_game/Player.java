package io.github.sacral_game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

public class Player {
    private Vector2 position;
    private Rectangle collisionRect;
    private float tileSize;
    private float tileHeight;
    private float speed;
    private float scale = 1.0f;

    private Texture spritesheet;
    private Animation<TextureRegion> idleAnimation;
    private Animation<TextureRegion> walkAnimation;
    private Animation<TextureRegion> attackAnimation;
    private float stateTime;
    private String currentState;
    private boolean facingRight;

    private Vector2 lastPosition;

    public Player(float x, float y, float speed, float tileSize) {
        position = new Vector2(x, y);
        lastPosition = new Vector2(x, y);
        this.speed = speed;
        this.currentState = "IDLE";
        this.facingRight = true;
        this.tileSize = tileSize;
        this.tileHeight = tileSize;

        collisionRect = new Rectangle();
        loadAnimations();
        updateCollisionRect();
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

        // Настройка масштаба относительно тайла
        TextureRegion frame = idleAnimation.getKeyFrame(0);
        float desiredHeight = tileHeight * 4f; // Высота персонажа - 2 тайла
        scale = desiredHeight / frame.getRegionHeight();
    }

    public void update(float delta, TiledMap map, MapObjects collisionObjects) {
        stateTime += delta;
        lastPosition.set(position);

        handleInput(delta);
        updateCollisionRect();

        if (checkCollisions(collisionObjects)) {
            position.set(lastPosition);
            updateCollisionRect();
        }
    }

    private void handleInput(float delta) {
        boolean isMoving = false;

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
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            currentState = "ATTACK";
            stateTime = 0;
        }

        if (currentState.equals("ATTACK")) {
            if (attackAnimation.isAnimationFinished(stateTime)) {
                currentState = "IDLE";
            }
        } else {
            currentState = isMoving ? "WALK" : "IDLE";
        }
    }

    private boolean checkCollisions(MapObjects collisionObjects) {
        for (MapObject object : collisionObjects) {
            if (object instanceof TiledMapTileMapObject) {
                TiledMapTileMapObject tileObject = (TiledMapTileMapObject) object;
                TiledMapTile tile = tileObject.getTile();
                Rectangle tileRect = getTileCollisionRectangle(tile, tileObject.getX(), tileObject.getY());
                if (Intersector.overlaps(tileRect, collisionRect)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Rectangle getTileCollisionRectangle(TiledMapTile tile, float x, float y) {
        MapObjects objects = tile.getObjects();
        for (MapObject object : objects) {
            if (object instanceof RectangleMapObject) {
                Rectangle rect = ((RectangleMapObject) object).getRectangle();
                return new Rectangle(x + rect.x, y + rect.y, rect.width, rect.height);
            }
        }
        return new Rectangle(x, y, tile.getTextureRegion().getRegionWidth(),
            tile.getTextureRegion().getRegionHeight());
    }

    private void updateCollisionRect() {
        TextureRegion currentFrame = getCurrentFrame();
        float width = currentFrame.getRegionWidth() * scale * 0.2f;  // Уменьшаем ширину коллизии
        float height = currentFrame.getRegionHeight() * scale * 0.1f; // Уменьшаем высоту коллизии

        collisionRect.set(
            position.x - 25,
            position.y + 25,
            width,
            height
        );
    }

    public Sprite getSprite() {
        TextureRegion currentFrame = getCurrentFrame();
        Sprite sprite = new Sprite(currentFrame);

        float width = currentFrame.getRegionWidth() * scale;
        float height = currentFrame.getRegionHeight() * scale;

        sprite.setSize(width, height);
        sprite.setOrigin(width / 2, 0);
        sprite.setPosition(position.x - 70, position.y);

        if (!facingRight) {
            sprite.setFlip(true, false);
            sprite.setPosition(position.x - 85, position.y);
        }

        return sprite;
    }

    private TextureRegion getCurrentFrame() {
        switch (currentState) {
            case "WALK":
                return walkAnimation.getKeyFrame(stateTime, true);
            case "ATTACK":
                return attackAnimation.getKeyFrame(stateTime, false);
            default:
                return idleAnimation.getKeyFrame(stateTime, true);
        }
    }

    public Vector2 getPosition() {
        return position;
    }

    public Rectangle getCollisionRect() {
        return collisionRect;
    }

    public boolean isFacingRight() {
        return facingRight;
    }

    public void dispose() {
        spritesheet.dispose();
    }
}

