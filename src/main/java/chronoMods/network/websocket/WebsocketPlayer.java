package chronoMods.network.websocket;

import chronoMods.network.RemotePlayer;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import lombok.Getter;

public class WebsocketPlayer extends RemotePlayer {
    /**
     * Convenience reference to the websocket integration instance.
     */
    public static WebsocketIntegration service = null;

    @Getter(lazy = true)
    private static final Texture defaultAvatar = loadDefaultAvatar();

    public long userId;

    public WebsocketPlayer(long userId, String userName) {
        super();
        this.userId = userId;
        this.userName = userName;
        portraitImg = getDefaultAvatar();
    }

    private static Texture loadDefaultAvatar() {
        Pixmap dest = new Pixmap(182, 182, Pixmap.Format.RGBA8888);
        Pixmap src = new Pixmap(Gdx.files.internal("chrono/images/uncertain_future.png"));
        dest.drawPixmap(src,
                0, 0, src.getWidth(), src.getHeight(),
                0, 0, dest.getWidth(), dest.getHeight());
        Texture tex = new Texture(dest);
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        src.dispose();
        dest.dispose();
        return tex;
    }
}
