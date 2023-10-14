package chronoMods.sfw.websocket.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
public class ControlPacket {

    @Getter @Setter
    private ControlCode kind;

    @Setter
    private Object data;

    @SuppressWarnings("unchecked")
    public <T> T getData() {
        return (T) data;
    }
}
