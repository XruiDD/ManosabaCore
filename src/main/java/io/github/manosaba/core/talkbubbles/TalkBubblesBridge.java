package io.github.manosaba.core.talkbubbles;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Wire-format encoder + sender for the TalkBubbles client mod's S2C plugin
 * message channel ({@value #CHANNEL}).
 *
 * <p>Payload layout (mirrors {@code PacketByteBuf.writeUuid()} + {@code writeString(s, 32767)}):
 * <pre>
 *   bytes 0..7   : sender UUID hi (big-endian long)
 *   bytes 8..15  : sender UUID lo (big-endian long)
 *   bytes 16..   : VarInt UTF-8 byte length, then the UTF-8 bytes (no terminator)
 * </pre>
 * Vanilla clients (without the mod) silently skip the packet because they do not
 * register the channel during the {@code minecraft:register} handshake.</p>
 */
public final class TalkBubblesBridge {

    public static final String CHANNEL = "talkbubbles:bubble";
    public static final int MAX_MESSAGE_BYTES = 32767;

    private TalkBubblesBridge() {
    }

    /**
     * Send one bubble for {@code senderUuid} carrying {@code message} to
     * {@code recipient} if (and only if) the recipient's client has
     * registered the {@value #CHANNEL} channel.
     */
    public static void send(@NotNull JavaPlugin plugin,
                            @NotNull UUID senderUuid,
                            @NotNull String message,
                            @NotNull Player recipient) {
        if (!recipient.getListeningPluginChannels().contains(CHANNEL)) {
            return; // vanilla / mod missing
        }
        byte[] payload = encode(senderUuid, message);
        if (payload == null) {
            return;
        }
        try {
            recipient.sendPluginMessage(plugin, CHANNEL, payload);
        } catch (IllegalStateException ignored) {
            // Recipient disconnected between the channel check and the send.
        }
    }

    public static byte @Nullable [] encode(@NotNull UUID sender, @NotNull String message) {
        byte[] msgBytes = message.getBytes(StandardCharsets.UTF_8);
        if (msgBytes.length > MAX_MESSAGE_BYTES) {
            // Walk back over UTF-8 continuation bytes (10xxxxxx) so we cut at a code-point boundary.
            int safe = MAX_MESSAGE_BYTES;
            while (safe > 0 && (msgBytes[safe] & 0xC0) == 0x80) {
                safe--;
            }
            byte[] cut = new byte[safe];
            System.arraycopy(msgBytes, 0, cut, 0, safe);
            msgBytes = cut;
        }
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeLong(sender.getMostSignificantBits());
        out.writeLong(sender.getLeastSignificantBits());
        try {
            writeVarInt(out, msgBytes.length);
        } catch (IOException impossible) {
            return null;
        }
        out.write(msgBytes);
        return out.toByteArray();
    }

    private static void writeVarInt(@NotNull DataOutput out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }
}
