package chronoMods.network.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.channel.Channel;
import lombok.Getter;
import lombok.Setter;
import lombok.val;

import java.nio.Buffer;
import java.nio.ByteBuffer;

public class DataPacket extends DefaultByteBufHolder implements MyCodecs.WsPacket {

    @Getter @Setter
    private long playerId;

    public DataPacket(ByteBuf data) {
        super(data);
    }

    public static DataPacket fromNioBuffer(Channel ch, long playerId, ByteBuffer buf) {
        val buf2 = ch.alloc().buffer(8 + buf.capacity());
        ((Buffer)buf).rewind();
        buf2.writeLong(playerId).writeBytes(buf).resetWriterIndex();
        return new DataPacket(buf2);
    }
}
