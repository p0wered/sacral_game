package io.github.sacral_game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

import java.util.ArrayList;

public class Enemy {
    private Vector2 position;
    private Vector2 velocity;
    private float speed = 130f;
    private Rectangle collisionRect;
    private float damage = 10;
    private float damageInterval = 1.0f;
    private float damageTimer = 0;
    private float size = 30f;
    private float spriteWidth = 40f;
    private float spriteHeight = 40f;
    private int health = 90;
    private boolean isStunned = false;
    private float stunDuration = 0.3f;
    private float stunTimer = 0;
    private static final float MIN_ENEMY_DISTANCE = 1f;

    private Animation<TextureRegion>[] walkAnimations;
    private Animation<TextureRegion>[] idleAnimations;
    private Animation<TextureRegion>[] attackAnimations;
    private Animation<TextureRegion>[] deathAnimations;
    private Animation<TextureRegion>[] stunAnimations;
    private float stateTime;
    private Direction currentDirection;
    private EnemyState currentState;
    private boolean isDead;

    private Vector2 moveDirection;
    private float obstacleAvoidanceTimer = 0;
    private float obstacleAvoidanceInterval = 0.5f;
    private boolean isAvoidingObstacle = false;
    private static final float RAY_LENGTH = 150f;
    private static final int NUM_RAYS = 128;
    private Vector2 targetPosition;
    private float pathFindingTimer = 0;
    private float pathFindingInterval = 0.2f;

    private float idleTimer = 0;
    private float idleThreshold = 0.5f;
    private boolean isCollisionDisabled = false;
    private float collisionDisabledTimer = 0;
    private float collisionDisabledDuration = 1f;

    private enum Direction {
        FRONT(0), BACK(1), RIGHT(2), LEFT(3);
        final int index;
        Direction(int index) { this.index = index; }
    }

    private enum EnemyState {
        IDLE, WALKING, ATTACKING, DYING, STUNNED
    }

    @SuppressWarnings("unchecked")
    public Enemy(float x, float y) {
        position = new Vector2(x, y);
        velocity = new Vector2();
        moveDirection = new Vector2();
        float collisionWidth = 25f;
        float collisionHeight = 16f;
        collisionRect = new Rectangle(x, y, collisionWidth, collisionHeight);

        walkAnimations = new Animation[4];
        idleAnimations = new Animation[4];
        attackAnimations = new Animation[4];
        deathAnimations = new Animation[4];
        stunAnimations = new Animation[4];

        loadAnimations();

        currentDirection = Direction.FRONT;
        currentState = EnemyState.IDLE;
        stateTime = 0;
        isDead = false;
    }

    private void loadAnimations() {
        loadWalkAnimations();
        loadIdleAnimations();
        loadAttackAnimations();
        loadDeathAnimations();
        loadStunAnimations();
    }

    private void loadWalkAnimations() {
        Texture walkSheet = new Texture(Gdx.files.internal("../assets/zombie/Walk.png"));
        TextureRegion[][] tmp = TextureRegion.split(walkSheet,
            walkSheet.getWidth() / 11,
            walkSheet.getHeight() / 4);

        for (int dir = 0; dir < 4; dir++) {
            TextureRegion[] walkFrames = new TextureRegion[10];
            System.arraycopy(tmp[dir], 0, walkFrames, 0, 10);
            walkAnimations[dir] = new Animation<>(0.1f, walkFrames);
        }
    }

    private void loadIdleAnimations() {
        Texture idleSheet = new Texture(Gdx.files.internal("../assets/zombie/Idle.png"));
        TextureRegion[][] tmp = TextureRegion.split(idleSheet,
            idleSheet.getWidth() / 6,
            idleSheet.getHeight() / 4);

        for (int dir = 0; dir < 4; dir++) {
            TextureRegion[] idleFrames = new TextureRegion[5];
            System.arraycopy(tmp[dir], 0, idleFrames, 0, 5);
            idleAnimations[dir] = new Animation<>(0.1f, idleFrames);
        }
    }

    private void loadAttackAnimations() {
        Texture attackSheet = new Texture(Gdx.files.internal("../assets/zombie/Attack.png"));
        TextureRegion[][] tmp = TextureRegion.split(attackSheet,
            attackSheet.getWidth() / 9,
            attackSheet.getHeight() / 4);

        for (int dir = 0; dir < 4; dir++) {
            TextureRegion[] attackFrames = new TextureRegion[8];
            System.arraycopy(tmp[dir], 0, attackFrames, 0, 8);
            attackAnimations[dir] = new Animation<>(0.1f, attackFrames);
        }
    }

    private void loadDeathAnimations() {
        Texture deathSheet = new Texture(Gdx.files.internal("../assets/zombie/Death.png"));
        TextureRegion[][] tmp = TextureRegion.split(deathSheet,
            deathSheet.getWidth() / 8,
            deathSheet.getHeight() / 4);

        for (int dir = 0; dir < 4; dir++) {
            TextureRegion[] deathFrames = new TextureRegion[7];
            System.arraycopy(tmp[dir], 0, deathFrames, 0, 7);
            deathAnimations[dir] = new Animation<>(0.3f, deathFrames);
        }
    }

    private void loadStunAnimations() {
        Texture stunSheet = new Texture(Gdx.files.internal("../assets/zombie/Stunned.png"));
        TextureRegion[][] tmp = TextureRegion.split(stunSheet,
            stunSheet.getWidth() / 6,
            stunSheet.getHeight() / 4);

        for (int dir = 0; dir < 4; dir++) {
            TextureRegion[] stunFrames = new TextureRegion[5];
            System.arraycopy(tmp[dir], 0, stunFrames, 0, 5);
            stunAnimations[dir] = new Animation<>(0.1f, stunFrames);
        }
    }

    public void update(float delta, Player player, MapObjects collisionObjects, ArrayList<Enemy> enemies) {
        stateTime += delta;
        damageTimer += delta;
        obstacleAvoidanceTimer += delta;
        pathFindingTimer += delta;

        if (isCollisionDisabled) {
            collisionDisabledTimer += delta;
            if (collisionDisabledTimer >= collisionDisabledDuration) {
                isCollisionDisabled = false;
                collisionDisabledTimer = 0;
            }
        }

        if (isStunned) {
            stunTimer += delta;
            if (stunTimer >= stunDuration) {
                isStunned = false;
                stunTimer = 0;
            }
            currentState = EnemyState.STUNNED;
            return;
        }

        if (isDead) {
            currentState = EnemyState.DYING;
            return;
        }

        targetPosition = player.getPosition();

        if (currentState == EnemyState.IDLE && !isAttacking()) {
            idleTimer += delta;
            if (idleTimer >= idleThreshold && !isCollisionDisabled) {
                isCollisionDisabled = true;
                collisionDisabledTimer = 0;
            }
        } else {
            idleTimer = 0;
        }

        if (pathFindingTimer >= pathFindingInterval) {
            moveDirection.set(findBestDirection(collisionObjects, enemies));
            pathFindingTimer = 0;
        }

        updateDirection(moveDirection);

        float nextX = position.x + moveDirection.x * speed * delta;
        float nextY = position.y + moveDirection.y * speed * delta;

        Rectangle nextPositionRect = new Rectangle(
            nextX , nextY, collisionRect.width, collisionRect.height);

        if (!checkCollisions(nextPositionRect, collisionObjects) &&
            !checkEnemyCollisions(nextPositionRect, enemies)) {
            position.x = nextX;
            position.y = nextY;
            currentState = EnemyState.WALKING;
        } else {
            currentState = EnemyState.IDLE;
        }

        collisionRect.setPosition(position.x + 10, position.y);

        if (collisionRect.overlaps(player.getCollisionRect())) {
            currentState = EnemyState.ATTACKING;
            if (damageTimer >= damageInterval) {
                player.takeDamage((int)damage);
                damageTimer = 0;
            }
        }
    }

    private boolean checkEnemyCollisions(Rectangle nextPos, ArrayList<Enemy> enemies) {
        if (isCollisionDisabled) {
            return false;
        }

        for (Enemy otherEnemy : enemies) {
            if (otherEnemy == this || otherEnemy.isDead) continue;

            if (nextPos.overlaps(otherEnemy.getCollisionRect())) {
                return true;
            }
        }
        return false;
    }

    private void updateDirection(Vector2 direction) {
        if (direction.len2() > 0.01f) {
            if (Math.abs(direction.x) > Math.abs(direction.y)) {
                currentDirection = direction.x > 0 ? Direction.RIGHT : Direction.LEFT;
            } else {
                currentDirection = direction.y > 0 ? Direction.BACK : Direction.FRONT;
            }
        }
    }

    private Vector2 findBestDirection(MapObjects collisionObjects, ArrayList<Enemy> enemies) {
        Vector2 directionToTarget = new Vector2(
            targetPosition.x - position.x,
            targetPosition.y - position.y
        );

        float distanceToTarget = directionToTarget.len();
        directionToTarget.nor();

        if (!isPathBlocked(position, targetPosition, collisionObjects, enemies)) {
            return directionToTarget;
        }

        Vector2 bestDirection = new Vector2(directionToTarget);
        float bestScore = Float.NEGATIVE_INFINITY;

        for (int i = 0; i < NUM_RAYS; i++) {
            float angle = (360f / NUM_RAYS) * i;
            Vector2 rayDirection = new Vector2(1, 0).rotate(angle).nor();
            Vector2 rayEnd = new Vector2(
                position.x + rayDirection.x * RAY_LENGTH,
                position.y + rayDirection.y * RAY_LENGTH
            );

            float score = evaluateDirection(rayDirection, rayEnd, collisionObjects, enemies);

            if (score > bestScore) {
                bestScore = score;
                bestDirection.set(rayDirection);
            }
        }

        if (bestScore == Float.NEGATIVE_INFINITY) {
            bestDirection.set(MathUtils.random(-1f, 1f), MathUtils.random(-1f, 1f)).nor();
        }

        return bestDirection;
    }


    private float evaluateDirection(Vector2 direction, Vector2 rayEnd,
                                    MapObjects collisionObjects, ArrayList<Enemy> enemies) {
        float angleToTarget = direction.angle(new Vector2(
            targetPosition.x - position.x,
            targetPosition.y - position.y
        ));

        float score = 1000 - angleToTarget;

        if (isPathBlocked(position, rayEnd, collisionObjects, enemies)) {
            score -= 500;
        }

        float distanceToTarget = position.dst(targetPosition);

        float dynamicMinEnemyDistance = MIN_ENEMY_DISTANCE;
        if (distanceToTarget < MIN_ENEMY_DISTANCE * 2) {
            dynamicMinEnemyDistance = MIN_ENEMY_DISTANCE / 2;
        }

        for (Enemy otherEnemy : enemies) {
            if (otherEnemy == this || otherEnemy.isDead) continue;

            float distToEnemy = position.dst(otherEnemy.position);
            if (distToEnemy < dynamicMinEnemyDistance) {
                float penaltyMultiplier = Math.max(0.2f, distanceToTarget / MIN_ENEMY_DISTANCE);
                score -= (dynamicMinEnemyDistance - distToEnemy) * 10 * penaltyMultiplier;
            }
        }

        Vector2 potentialPosition = new Vector2(
            position.x + direction.x * RAY_LENGTH,
            position.y + direction.y * RAY_LENGTH
        );

        float newDistanceToTarget = potentialPosition.dst(targetPosition);

        if (newDistanceToTarget < distanceToTarget) {
            float distanceBonus = Math.min(400, 200 * (distanceToTarget / MIN_ENEMY_DISTANCE));
            score += distanceBonus;
        }

        return score;
    }

    private boolean isPathBlocked(Vector2 start, Vector2 end,
                                  MapObjects collisionObjects, ArrayList<Enemy> enemies) {
        Vector2 direction = new Vector2(end).sub(start);
        float distance = direction.len();
        direction.nor();

        int numSteps = (int)(distance / (size / 2));
        for (int i = 0; i < numSteps; i++) {
            float stepX = start.x + direction.x * i * (size / 2);
            float stepY = start.y + direction.y * i * (size / 2);

            Rectangle testRect = new Rectangle(stepX, stepY, size, size);
            if (checkCollisions(testRect, collisionObjects) ||
                checkEnemyCollisions(testRect, enemies)) {
                return true;
            }
        }

        return false;
    }

    private boolean checkCollisions(Rectangle nextPos, MapObjects collisionObjects) {
        if (isCollisionDisabled) {
            return false;
        }

        for (MapObject object : collisionObjects) {
            if (object instanceof TiledMapTileMapObject tileObject) {
                TiledMapTile tile = tileObject.getTile();
                if (tile != null && tile.getObjects().getCount() > 0) {
                    Rectangle tileRect = ((RectangleMapObject)tile.getObjects().get(0)).getRectangle();
                    tileRect = new Rectangle(
                        tileObject.getX() + tileRect.x,
                        tileObject.getY() + tileRect.y,
                        tileRect.width,
                        tileRect.height
                    );
                    if (nextPos.overlaps(tileRect)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public TextureRegion getCurrentFrame() {
        Animation<TextureRegion>[] currentAnimations;
        switch (currentState) {
            case WALKING:
                currentAnimations = walkAnimations;
                break;
            case ATTACKING:
                currentAnimations = attackAnimations;
                break;
            case DYING:
                currentAnimations = deathAnimations;
                break;
            case STUNNED:
                currentAnimations = stunAnimations;
                break;
            default:
                currentAnimations = idleAnimations;
        }
        return currentAnimations[currentDirection.index].getKeyFrame(stateTime,
            currentState != EnemyState.DYING);
    }

    public void draw(SpriteBatch batch) {
        TextureRegion currentFrame = getCurrentFrame();
        batch.draw(currentFrame, position.x, position.y, 80f, 80f);
    }

    public Vector2 getPosition() {
        return position;
    }

    public Rectangle getCollisionRect() {
        return collisionRect;
    }

    public void takeDamage(int damage) {
        if (isDead) return;

        health -= damage;
        isStunned = true;
        stunTimer = 0;

        if (health <= 0) {
            die();
        }
    }

    public boolean isDead() {
        return isDead;
    }

    private boolean isAttacking() {
        return currentState == EnemyState.ATTACKING;
    }


    public void die() {
        if (!isDead) {
            isDead = true;
            stateTime = 0;
            currentState = EnemyState.DYING;
        }
    }

    public boolean isDeathAnimationComplete() {
        return currentState == EnemyState.DYING &&
            deathAnimations[currentDirection.index].isAnimationFinished(stateTime);
    }
}
