package io.github.sacral_game;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class App extends Game {
    @Override
    public void create() {
        Gdx.graphics.setWindowedMode(1280, 720);
        setScreen(new MainMenuScreen(this));
    }
}
