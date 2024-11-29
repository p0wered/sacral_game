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
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

public class Enemy {
    private Vector2 position;
    private Vector2 velocity;
    private float speed = 60f;
    private Rectangle collisionRect;
    private float damage = 10;
    private float damageInterval = 1.0f;
    private float damageTimer = 0;
    private float size = 40f;

    // Анимационные компоненты
    private Animation<TextureRegion>[] walkAnimations;
    private Animation<TextureRegion>[] idleAnimations;
    private Animation<TextureRegion>[] attackAnimations;
    private Animation<TextureRegion>[] deathAnimations;
    private float stateTime;
    private Direction currentDirection;
    private EnemyState currentState;
    private boolean isDead;

    // Для движения и избегания препятствий
    private Vector2 moveDirection;
    private float obstacleAvoidanceTimer = 0;
    private float obstacleAvoidanceInterval = 0.5f;
    private boolean isAvoidingObstacle = false;
    private static final float RAY_LENGTH = 100f;
    private static final int NUM_RAYS = 8;
    private Vector2 targetPosition;
    private float pathFindingTimer = 0;
    private float pathFindingInterval = 0.2f;

    // Направления и состояния
    private enum Direction {
        FRONT(0), BACK(1), RIGHT(2), LEFT(3);
        final int index;
        Direction(int index) { this.index = index; }
    }

    private enum EnemyState {
        IDLE, WALKING, ATTACKING, DYING
    }

    @SuppressWarnings("unchecked")
    public Enemy(float x, float y) {
        position = new Vector2(x, y);
        velocity = new Vector2();
        moveDirection = new Vector2();
        collisionRect = new Rectangle(x, y, size, size);

        // Инициализация анимаций
        walkAnimations = new Animation[4];
        idleAnimations = new Animation[4];
        attackAnimations = new Animation[4];
        deathAnimations = new Animation[4];

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
    }

    private void loadWalkAnimations() {
        Texture walkSheet = new Texture(Gdx.files.internal("../assets/zombie/Walk.png"));
        TextureRegion[][] tmp = TextureRegion.split(walkSheet,
            walkSheet.getWidth() / 11, // Предполагаем 8 кадров в строке
            walkSheet.getHeight() / 4); // 4 направления

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
            idleAnimations[dir] = new Animation<>(0.1f, idleFrames); // Используем idleAnimations вместо walkAnimations
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
            attackAnimations[dir] = new Animation<>(0.1f, attackFrames); // Используем attackAnimations вместо walkAnimations
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
            deathAnimations[dir] = new Animation<>(0.1f, deathFrames); // Используем deathAnimations вместо walkAnimations
        }
    }

    public void update(float delta, Player player, MapObjects collisionObjects) {
        stateTime += delta;
        damageTimer += delta;
        obstacleAvoidanceTimer += delta;
        pathFindingTimer += delta;

        if (isDead) {
            currentState = EnemyState.DYING;
            if (deathAnimations[currentDirection.index].isAnimationFinished(stateTime)) {
                return;
            }
        }

        // Сохраняем позицию игрока как целевую точку
        targetPosition = player.getPosition();

        // Обновляем путь каждый интервал
        if (pathFindingTimer >= pathFindingInterval) {
            moveDirection.set(findBestDirection(collisionObjects));
            pathFindingTimer = 0;
        }

        // Обновляем направление анимации на основе движения
        updateDirection(moveDirection);

        // Рассчитываем следующую позицию
        float nextX = position.x + moveDirection.x * speed * delta;
        float nextY = position.y + moveDirection.y * speed * delta;

        Rectangle nextPositionRect = new Rectangle(
            nextX, nextY, collisionRect.width, collisionRect.height);

        // Проверяем коллизии
        if (!checkCollisions(nextPositionRect, collisionObjects)) {
            position.x = nextX;
            position.y = nextY;
            currentState = EnemyState.WALKING;
        } else {
            currentState = EnemyState.IDLE;
        }

        // Обновляем прямоугольник коллизии
        collisionRect.setPosition(position.x, position.y);

        // Проверяем столкновение с игроком
        if (collisionRect.overlaps(player.getCollisionRect())) {
            currentState = EnemyState.ATTACKING;
            if (damageTimer >= damageInterval) {
                player.takeDamage((int)damage);
                damageTimer = 0;
            }
        }
    }

    private void updateDirection(Vector2 direction) {
        if (direction.len2() > 0.01f) { // Проверяем, что враг действительно движется
            if (Math.abs(direction.x) > Math.abs(direction.y)) {
                currentDirection = direction.x > 0 ? Direction.RIGHT : Direction.LEFT;
            } else {
                currentDirection = direction.y > 0 ? Direction.BACK : Direction.FRONT;
            }
        }
    }

    private Vector2 findBestDirection(MapObjects collisionObjects) {
        Vector2 directionToTarget = new Vector2(
            targetPosition.x - position.x,
            targetPosition.y - position.y
        );

        float distanceToTarget = directionToTarget.len();
        directionToTarget.nor();

        if (!isPathBlocked(position, targetPosition, collisionObjects)) {
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

            float score = evaluateDirection(rayDirection, rayEnd, collisionObjects);

            if (score > bestScore) {
                bestScore = score;
                bestDirection.set(rayDirection);
            }
        }

        return bestDirection;
    }

    private float evaluateDirection(Vector2 direction, Vector2 rayEnd, MapObjects collisionObjects) {
        // Базовая оценка - насколько направление близко к цели
        float angleToTarget = direction.angle(new Vector2(
            targetPosition.x - position.x,
            targetPosition.y - position.y
        ));

        float score = 1000 - angleToTarget;

        // Проверяем, не заблокировано ли направление
        if (isPathBlocked(position, rayEnd, collisionObjects)) {
            score -= 500;
        }

        // Дополнительный бонус за направления, которые ведут ближе к цели
        Vector2 potentialPosition = new Vector2(
            position.x + direction.x * RAY_LENGTH,
            position.y + direction.y * RAY_LENGTH
        );

        float currentDistanceToTarget = position.dst(targetPosition);
        float newDistanceToTarget = potentialPosition.dst(targetPosition);

        if (newDistanceToTarget < currentDistanceToTarget) {
            score += 200;
        }

        return score;
    }

    private boolean isPathBlocked(Vector2 start, Vector2 end, MapObjects collisionObjects) {
        Vector2 direction = new Vector2(end).sub(start);
        float distance = direction.len();
        direction.nor();

        // Проверяем путь с помощью нескольких точек
        int numSteps = (int)(distance / (size / 2));
        for (int i = 0; i < numSteps; i++) {
            float stepX = start.x + direction.x * i * (size / 2);
            float stepY = start.y + direction.y * i * (size / 2);

            Rectangle testRect = new Rectangle(stepX, stepY, size, size);
            if (checkCollisions(testRect, collisionObjects)) {
                return true;
            }
        }

        return false;
    }

    private boolean checkCollisions(Rectangle nextPos, MapObjects collisionObjects) {
        // Проверяем коллизии со всеми объектами на карте
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

    private TextureRegion getCurrentFrame() {
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
            default:
                currentAnimations = idleAnimations;
        }
        return currentAnimations[currentDirection.index].getKeyFrame(stateTime,
            currentState != EnemyState.DYING);
    }

    public void draw(SpriteBatch batch) {
        TextureRegion currentFrame = getCurrentFrame();
        batch.draw(currentFrame,
            position.x, position.y,
            size, size);
    }

    public Vector2 getPosition() {
        return position;
    }

    public Rectangle getCollisionRect() {
        return collisionRect;
    }

    public void die() {
        if (!isDead) {
            isDead = true;
            stateTime = 0;
            currentState = EnemyState.DYING;
        }
    }
}
