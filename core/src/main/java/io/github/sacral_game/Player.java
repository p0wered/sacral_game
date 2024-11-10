package io.github.sacral_game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

public class Player {
    private Vector2 position;
    private Rectangle collisionRect;
    private float tileSize;
    private float tileHeight;
    private float speed;
    private float scale = 1.6f;

    private Texture idleTextureUp, idleTextureDown, idleTextureLeft, idleTextureRight;
    private Texture walkTextureUp, walkTextureDown, walkTextureLeft, walkTextureRight;
    private Texture attackTextureUp, attackTextureDown, attackTextureLeft, attackTextureRight;

    private Animation<TextureRegion> idleAnimationUp, idleAnimationDown, idleAnimationLeft, idleAnimationRight;
    private Animation<TextureRegion> walkAnimationUp, walkAnimationDown, walkAnimationLeft, walkAnimationRight;
    private Animation<TextureRegion> attackAnimationUp, attackAnimationDown, attackAnimationLeft, attackAnimationRight;

    private float stateTime;
    private String currentState;
    private String currentDirection = "DOWN"; // Направление по умолчанию
    private Vector2 lastPosition;

    public Player(float x, float y, float speed, float tileSize) {
        position = new Vector2(x, y);
        lastPosition = new Vector2(x, y);
        this.speed = speed;
        this.currentState = "IDLE";
        this.tileSize = tileSize;
        this.tileHeight = tileSize;

        collisionRect = new Rectangle();
        loadAnimations();
        updateCollisionRect();
    }

    private Animation<TextureRegion> createAnimation(Texture texture, int frameCount) {
        int frameWidth = texture.getWidth() / frameCount;
        int frameHeight = texture.getHeight();
        TextureRegion[][] tmp = TextureRegion.split(texture, frameWidth, frameHeight);

        TextureRegion[] frames = new TextureRegion[frameCount];
        for (int i = 0; i < frameCount; i++) {
            frames[i] = tmp[0][i];
        }
        return new Animation<>(0.1f, frames);
    }

    private void loadAnimations() {
        idleTextureUp = new Texture("../assets/back_animations/spr_player_back_idle.png");
        idleTextureDown = new Texture("../assets/front_animations/spr_player_front_idle.png");
        idleTextureLeft = new Texture("../assets/side_animations/spr_player_left_idle.png");
        idleTextureRight = new Texture("../assets/side_animations/spr_player_right_idle.png");

        walkTextureUp = new Texture("../assets/back_animations/spr_player_back_walk.png");
        walkTextureDown = new Texture("../assets/front_animations/spr_player_front_walk.png");
        walkTextureLeft = new Texture("../assets/side_animations/spr_player_left_walk.png");
        walkTextureRight = new Texture("../assets/side_animations/spr_player_right_walk.png");

        attackTextureUp = new Texture("../assets/back_animations/spr_player_back_attack.png");
        attackTextureDown = new Texture("../assets/front_animations/spr_player_front_attack.png");
        attackTextureLeft = new Texture("../assets/side_animations/spr_player_left_attack.png");
        attackTextureRight = new Texture("../assets/side_animations/spr_player_right_attack.png");

        idleAnimationUp = createAnimation(idleTextureUp, 12);
        idleAnimationDown = createAnimation(idleTextureDown, 12);
        idleAnimationLeft = createAnimation(idleTextureLeft, 12);
        idleAnimationRight = createAnimation(idleTextureRight, 12);

        walkAnimationUp = createAnimation(walkTextureUp, 6);
        walkAnimationDown = createAnimation(walkTextureDown, 6);
        walkAnimationLeft = createAnimation(walkTextureLeft, 6);
        walkAnimationRight = createAnimation(walkTextureRight, 6);

        attackAnimationUp = createAnimation(attackTextureUp, 7);
        attackAnimationDown = createAnimation(attackTextureDown, 7);
        attackAnimationLeft = createAnimation(attackTextureLeft, 7);
        attackAnimationRight = createAnimation(attackTextureRight, 7);
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
            isMoving = true;
            currentDirection = "LEFT";
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            position.x += speed * delta;
            isMoving = true;
            currentDirection = "RIGHT";
        }
        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            position.y += speed * delta;
            isMoving = true;
            currentDirection = "UP";
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            position.y -= speed * delta;
            isMoving = true;
            currentDirection = "DOWN";
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            currentState = "ATTACK";
            stateTime = 0;
        }

        if (currentState.equals("ATTACK")) {
            if (getAttackAnimation().isAnimationFinished(stateTime)) {
                currentState = "IDLE";
            }
        } else {
            currentState = isMoving ? "WALK" : "IDLE";
        }
    }

    private Animation<TextureRegion> getIdleAnimation() {
        switch (currentDirection) {
            case "UP": return idleAnimationUp;
            case "DOWN": return idleAnimationDown;
            case "LEFT": return idleAnimationLeft;
            case "RIGHT": return idleAnimationRight;
            default: return idleAnimationDown;
        }
    }

    private Animation<TextureRegion> getWalkAnimation() {
        switch (currentDirection) {
            case "UP": return walkAnimationUp;
            case "DOWN": return walkAnimationDown;
            case "LEFT": return walkAnimationLeft;
            case "RIGHT": return walkAnimationRight;
            default: return walkAnimationDown;
        }
    }

    private Animation<TextureRegion> getAttackAnimation() {
        switch (currentDirection) {
            case "UP": return attackAnimationUp;
            case "DOWN": return attackAnimationDown;
            case "LEFT": return attackAnimationLeft;
            case "RIGHT": return attackAnimationRight;
            default: return attackAnimationDown;
        }
    }

    private TextureRegion getCurrentFrame() {
        switch (currentState) {
            case "WALK":
                return getWalkAnimation().getKeyFrame(stateTime, true);
            case "ATTACK":
                return getAttackAnimation().getKeyFrame(stateTime, false);
            default:
                return getIdleAnimation().getKeyFrame(stateTime, true);
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
        float width = currentFrame.getRegionWidth() * scale * 0.25f;  // Уменьшаем ширину коллизии
        float height = currentFrame.getRegionHeight() * scale * 0.15f;

        collisionRect.set(
            position.x,
            position.y,
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
        sprite.setPosition(position.x - 37, position.y - 28);

        return sprite;
    }

    public Vector2 getPosition() {
        return position;
    }

    public Rectangle getCollisionRect() {
        return collisionRect;
    }

    public void dispose() {
        idleTextureUp.dispose();
        idleTextureDown.dispose();
        idleTextureLeft.dispose();
        idleTextureRight.dispose();

        walkTextureUp.dispose();
        walkTextureDown.dispose();
        walkTextureLeft.dispose();
        walkTextureRight.dispose();

        attackTextureUp.dispose();
        attackTextureDown.dispose();
        attackTextureLeft.dispose();
        attackTextureRight.dispose();
    }
}

