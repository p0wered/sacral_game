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

import java.util.ArrayList;

public class Player {
    private Vector2 position;
    private Rectangle collisionRect;
    private float tileSize;
    private float tileHeight;
    private float speed;
    private float scale = 1.6f;
    private int maxHealth = 80;
    private int currentHealth;
    private boolean isInvulnerable = false;
    private float invulnerabilityTimer = 0;
    private final float INVULNERABILITY_DURATION = 1.0f;
    private boolean isDeathAnimationFinished = false;
    private boolean isDead = false;

    private float attackRange = 50f; // Радиус атаки
    private int attackDamage = 20; // Урон от атаки
    private float attackCooldown = 0.5f; // Время перезарядки
    private float attackTimer = 0;

    private Texture idleTextureUp, idleTextureDown, idleTextureLeft, idleTextureRight;
    private Texture walkTextureUp, walkTextureDown, walkTextureLeft, walkTextureRight;
    private Texture attackTextureUp, attackTextureDown, attackTextureLeft, attackTextureRight;
    private Texture deathTexture;

    private Animation<TextureRegion> idleAnimationUp, idleAnimationDown, idleAnimationLeft, idleAnimationRight;
    private Animation<TextureRegion> walkAnimationUp, walkAnimationDown, walkAnimationLeft, walkAnimationRight;
    private Animation<TextureRegion> attackAnimationUp, attackAnimationDown, attackAnimationLeft, attackAnimationRight;
    private Animation<TextureRegion> deathAnimation;

    private float stateTime;
    private String currentState;
    private String currentDirection = "DOWN";
    private Vector2 lastPosition;
    public boolean isAttacking;

    public Player(float x, float y, float speed, float tileSize) {
        position = new Vector2(x, y);
        lastPosition = new Vector2(x, y);
        this.speed = speed;
        this.currentState = "IDLE";
        this.tileSize = tileSize;
        this.tileHeight = tileSize;
        this.currentHealth = maxHealth;

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

        deathTexture = new Texture("../assets/special_animations/spr_player_death.png");

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

        deathAnimation = createAnimation(deathTexture, 8);
    }

    public void update(float delta, TiledMap map, MapObjects collisionObjects) {
        if (isDead) {
            if (!isDeathAnimationFinished) {
                stateTime += delta;
                if (deathAnimation.isAnimationFinished(stateTime)) {
                    isDeathAnimationFinished = true;
                    // TODO Возрождение игрока
                }
            }
            return;
        }

        stateTime += delta;
        lastPosition.set(position);

        if (isInvulnerable) {
            invulnerabilityTimer += delta;
            if (invulnerabilityTimer >= INVULNERABILITY_DURATION) {
                isInvulnerable = false;
                invulnerabilityTimer = 0;
            }
        }

        attackTimer += delta;

        handleInput(delta);
        updateCollisionRect();

        if (checkCollisions(collisionObjects)) {
            position.set(lastPosition);
            updateCollisionRect();
        }
    }

    private int lastPressedKey = -1;

    private void handleInput(float delta) {
        if (isDead) {
            return;
        }

        boolean isMoving = false;
        if (Gdx.input.isKeyJustPressed(Input.Keys.W)) lastPressedKey = Input.Keys.W;
        if (Gdx.input.isKeyJustPressed(Input.Keys.S)) lastPressedKey = Input.Keys.S;
        if (Gdx.input.isKeyJustPressed(Input.Keys.A)) lastPressedKey = Input.Keys.A;
        if (Gdx.input.isKeyJustPressed(Input.Keys.D)) lastPressedKey = Input.Keys.D;

        if (lastPressedKey != -1 && !Gdx.input.isKeyPressed(lastPressedKey)) {
            lastPressedKey = -1;
        }

        if (lastPressedKey != -1 && Gdx.input.isKeyPressed(lastPressedKey)) {
            switch (lastPressedKey) {
                case Input.Keys.W:
                    position.y += speed * delta;
                    isMoving = true;
                    currentDirection = "UP";
                    break;
                case Input.Keys.S:
                    position.y -= speed * delta;
                    isMoving = true;
                    currentDirection = "DOWN";
                    break;
                case Input.Keys.A:
                    position.x -= speed * delta;
                    isMoving = true;
                    currentDirection = "LEFT";
                    break;
                case Input.Keys.D:
                    position.x += speed * delta;
                    isMoving = true;
                    currentDirection = "RIGHT";
                    break;
            }
        }

        else if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            position.y += speed * delta;
            isMoving = true;
            currentDirection = "UP";
        }
        else if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            position.y -= speed * delta;
            isMoving = true;
            currentDirection = "DOWN";
        }
        else if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            position.x -= speed * delta;
            isMoving = true;
            currentDirection = "LEFT";
        }
        else if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            position.x += speed * delta;
            isMoving = true;
            currentDirection = "RIGHT";
        }

        // Проверяем атаку
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) && !currentState.equals("ATTACK")) {
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
        if (isDead) {
            return deathAnimation.getKeyFrame(stateTime, false);
        }

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

    public void takeDamage(int damage) {
        if (!isInvulnerable && !isDead) {
            currentHealth -= damage;
            if (currentHealth <= 0) {
                currentHealth = 0;
                die();
            }
            isInvulnerable = true;
            invulnerabilityTimer = 0;
        }
    }

    private void die() {
        isDead = true;
        stateTime = 0;
        currentHealth = 0;
    }

    public void attack(ArrayList<Enemy> enemies) {
        if (attackTimer < attackCooldown) {
            return;
        }

        attackTimer = 0;
        isAttacking = true;

        Rectangle attackRect = new Rectangle();
        float attackRange = 40;
        float attackWidth = 20;

        switch (currentDirection) {
            case "UP":
                attackRect.set(
                    position.x - attackWidth / 2 + 14,
                    position.y + collisionRect.height, // Область атаки выше игрока
                    attackWidth,
                    attackRange
                );
                break;
            case "DOWN":
                attackRect.set(
                    position.x - attackWidth / 2 + 14,
                    position.y - attackRange, // Область атаки ниже игрока
                    attackWidth,
                    attackRange
                );
                break;
            case "LEFT":
                attackRect.set(
                    position.x - attackRange, // Область атаки слева от игрока
                    position.y,
                    attackRange,
                    attackWidth
                );
                break;
            case "RIGHT":
                attackRect.set(
                    position.x + collisionRect.width, // Область атаки справа от игрока
                    position.y,
                    attackRange,
                    attackWidth
                );
                break;
        }

        for (Enemy enemy : enemies) {
            if (attackRect.overlaps(enemy.getCollisionRect())) {
                enemy.takeDamage(attackDamage);
            }
        }
    }

    public Rectangle getAttackRect() {
        Rectangle attackRect = new Rectangle();
        float attackRange = 30;
        float attackWidth = 20;

        switch (currentDirection) {
            case "UP":
                attackRect.set(
                    position.x - attackWidth / 2 + 14,
                    position.y + collisionRect.height, // Область атаки выше игрока
                    attackWidth,
                    attackRange
                );
                break;
            case "DOWN":
                attackRect.set(
                    position.x - attackWidth / 2 + 14,
                    position.y - attackRange, // Область атаки ниже игрока
                    attackWidth,
                    attackRange
                );
                break;
            case "LEFT":
                attackRect.set(
                    position.x - attackRange, // Область атаки слева от игрока
                    position.y,
                    attackRange,
                    attackWidth
                );
                break;
            case "RIGHT":
                attackRect.set(
                    position.x + collisionRect.width, // Область атаки справа от игрока
                    position.y,
                    attackRange,
                    attackWidth
                );
                break;
        }

        return attackRect;
    }

    public void heal(int amount) {
        currentHealth += amount;
        if (currentHealth > maxHealth) currentHealth = maxHealth;
    }

    public boolean isAlive() {
        return currentHealth > 0;
    }

    public int getCurrentHealth() {
        return currentHealth;
    }

    public float getHealthPercent() {
        return (float) currentHealth / maxHealth;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public boolean isDead() {
        return isDead;
    }

    public boolean isDeathAnimationFinished() {
        return isDeathAnimationFinished;
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

        deathTexture.dispose();
    }

    public boolean isAttacking() {
        return isAttacking;
    }
}

