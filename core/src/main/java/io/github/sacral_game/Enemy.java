package io.github.sacral_game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
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
    private float speed = 100f;
    private Rectangle collisionRect;
    private float damage = 10;
    private float damageInterval = 1.0f;
    private float damageTimer = 0;
    private float size = 20f;

    // Добавляем переменные для обхода препятствий
    private Vector2 currentDirection;
    private float obstacleAvoidanceTimer = 0;
    private float obstacleAvoidanceInterval = 0.5f;
    private boolean isAvoidingObstacle = false;
    private static final float RAY_LENGTH = 100f;
    private static final int NUM_RAYS = 8;
    private Vector2 targetPosition;
    private float pathFindingTimer = 0;
    private float pathFindingInterval = 0.2f;

    public Enemy(float x, float y) {
        position = new Vector2(x, y);
        velocity = new Vector2();
        currentDirection = new Vector2();
        collisionRect = new Rectangle(x, y, size, size);
    }

    public void update(float delta, Player player, MapObjects collisionObjects) {
        damageTimer += delta;
        obstacleAvoidanceTimer += delta;
        pathFindingTimer += delta;

        // Сохраняем позицию игрока как целевую точку
        targetPosition = player.getPosition();

        Vector2 directionToPlayer = new Vector2(
            targetPosition.x - position.x,
            targetPosition.y - position.y
        ).nor();

        // Обновляем путь каждый интервал
        if (pathFindingTimer >= pathFindingInterval) {
            currentDirection.set(findBestDirection(collisionObjects));
            pathFindingTimer = 0;
        }

        // Рассчитываем следующую позицию
        float nextX = position.x + currentDirection.x * speed * delta;
        float nextY = position.y + currentDirection.y * speed * delta;

        Rectangle nextPositionRect = new Rectangle(
            nextX, nextY, collisionRect.width, collisionRect.height);

        // Проверяем коллизии
        if (!checkCollisions(nextPositionRect, collisionObjects)) {
            position.x = nextX;
            position.y = nextY;
        }

        // Обновляем прямоугольник коллизии
        collisionRect.setPosition(position.x, position.y);

        // Проверяем столкновение с игроком
        if (collisionRect.overlaps(player.getCollisionRect()) && damageTimer >= damageInterval) {
            player.takeDamage((int)damage);
            damageTimer = 0;
        }
    }

    private Vector2 findBestDirection(MapObjects collisionObjects) {
        Vector2 directionToTarget = new Vector2(
            targetPosition.x - position.x,
            targetPosition.y - position.y
        );

        float distanceToTarget = directionToTarget.len();
        directionToTarget.nor();

        // Если путь до цели свободен, идём напрямую
        if (!isPathBlocked(position, targetPosition, collisionObjects)) {
            return directionToTarget;
        }

        // Создаём лучи вокруг врага
        Vector2 bestDirection = new Vector2(directionToTarget);
        float bestScore = Float.NEGATIVE_INFINITY;

        for (int i = 0; i < NUM_RAYS; i++) {
            float angle = (360f / NUM_RAYS) * i;
            Vector2 rayDirection = new Vector2(1, 0).rotate(angle).nor();
            Vector2 rayEnd = new Vector2(
                position.x + rayDirection.x * RAY_LENGTH,
                position.y + rayDirection.y * RAY_LENGTH
            );

            // Оцениваем каждое направление
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

    public void draw(ShapeRenderer shapeRenderer) {
        shapeRenderer.setColor(Color.RED);
        shapeRenderer.rect(position.x, position.y, size, size);
    }

    public Vector2 getPosition() {
        return position;
    }

    public Rectangle getCollisionRect() {
        return collisionRect;
    }
}
