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

    public Enemy(float x, float y) {
        position = new Vector2(x, y);
        velocity = new Vector2();
        currentDirection = new Vector2();
        collisionRect = new Rectangle(x, y, size, size);
    }

    public void update(float delta, Player player, MapObjects collisionObjects) {
        damageTimer += delta;
        obstacleAvoidanceTimer += delta;

        // Вычисляем направление к игроку
        Vector2 directionToPlayer = new Vector2(
            player.getPosition().x - position.x,
            player.getPosition().y - position.y
        ).nor();

        // Если не обходим препятствие, двигаемся к игроку
        if (!isAvoidingObstacle) {
            currentDirection.set(directionToPlayer);
        }

        // Рассчитываем предполагаемую следующую позицию
        float nextX = position.x + currentDirection.x * speed * delta;
        float nextY = position.y + currentDirection.y * speed * delta;

        Rectangle nextPositionRect = new Rectangle(
            nextX, nextY, collisionRect.width, collisionRect.height);

        // Проверяем коллизии
        boolean collisionDetected = checkCollisions(nextPositionRect, collisionObjects);

        if (collisionDetected) {
            if (!isAvoidingObstacle || obstacleAvoidanceTimer >= obstacleAvoidanceInterval) {
                // Пытаемся найти новое направление
                findNewDirection(directionToPlayer, collisionObjects); // Передаем collisionObjects
                obstacleAvoidanceTimer = 0;
                isAvoidingObstacle = true;
            }
        } else {
            isAvoidingObstacle = false;
            // Двигаемся в текущем направлении
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

    private void findNewDirection(Vector2 directionToPlayer, MapObjects collisionObjects) {
        // Массив возможных направлений для обхода (по часовой стрелке)
        float[] angles = {90, -90, 135, -135, 180};

        for (float angle : angles) {
            // Поворачиваем вектор направления к игроку на заданный угол
            Vector2 testDirection = new Vector2(directionToPlayer).rotate(angle).nor();

            // Проверяем, возможно ли движение в этом направлении
            float testX = position.x + testDirection.x * speed * 0.1f;
            float testY = position.y + testDirection.y * speed * 0.1f;

            Rectangle testRect = new Rectangle(testX, testY, collisionRect.width, collisionRect.height);

            if (!checkCollisions(testRect, collisionObjects)) {
                currentDirection.set(testDirection);
                return;
            }
        }

        // Если все направления заблокированы, остаемся на месте
        currentDirection.setZero();
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
