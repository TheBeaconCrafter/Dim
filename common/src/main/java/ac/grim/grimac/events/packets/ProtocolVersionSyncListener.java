package ac.grim.grimac.events.packets;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.LogUtil;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.configuration.client.WrapperConfigClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Restores the original client protocol when ViaVersion is running on the
 * proxy. PacketEvents sees the proxy/backend protocol on the backend unless
 * this message is applied to the User connection.
 */
public final class ProtocolVersionSyncListener extends PacketListenerAbstract {

    public static final String CHANNEL = "beaconlabs:protocol_version";
    public static final String REQUEST_CHANNEL = "beaconlabs:protocol_request";
    private static final Set<User> SYNCHRONIZED_USERS = ConcurrentHashMap.newKeySet();

    @Override
    public void onPacketReceive(@NotNull PacketReceiveEvent event) {
        byte[] data;
        String channel;

        if (event.getPacketType() == PacketType.Play.Client.PLUGIN_MESSAGE) {
            WrapperPlayClientPluginMessage packet = new WrapperPlayClientPluginMessage(event);
            channel = packet.getChannelName();
            data = packet.getData();
        } else if (event.getPacketType() == PacketType.Configuration.Client.PLUGIN_MESSAGE) {
            WrapperConfigClientPluginMessage packet = new WrapperConfigClientPluginMessage(event);
            channel = packet.getChannelName();
            data = packet.getData();
        } else {
            return;
        }

        if (!CHANNEL.equals(channel)) return;

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(data))) {
            UUID messageUuid = UUID.fromString(input.readUTF());
            int protocol = input.readInt();
            User user = event.getUser();

            // The UUID prevents a queued message for another player from
            // changing this connection's protocol state.
            if (!messageUuid.equals(user.getUUID())) return;
            synchronize(user, protocol);
        } catch (Exception exception) {
            LogUtil.warn("Failed to read proxy protocol data for " + event.getUser().getName() + ".", exception);
        }
    }

    /**
     * Applies a trusted protocol value supplied by ViaVersion or the proxy
     * bridge. The User is the connection that delivered the payload, so the
     * ViaVersion payload does not need a second UUID field.
     */
    public static boolean synchronize(@NotNull User user, int protocol) {
        ClientVersion clientVersion = ClientVersion.getById(protocol);
        if (clientVersion == ClientVersion.UNKNOWN
                || clientVersion == ClientVersion.LOWER_THAN_SUPPORTED_VERSIONS
                || clientVersion == ClientVersion.HIGHER_THAN_SUPPORTED_VERSIONS) {
            LogUtil.warn("Ignoring unsupported proxy protocol " + protocol + " for " + user.getName() + ".");
            return false;
        }

        GrimPlayer player = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(user);
        if (player != null) {
            player.applyClientVersion(clientVersion);
        } else {
            // If the sync arrives before Dim creates its player state,
            // construct it with the simulation version while leaving the
            // User's backend protocol intact for PacketEvents decoding.
            GrimAPI.INSTANCE.getPlayerDataManager().addUserAfterProtocolSync(user, clientVersion);
        }
        SYNCHRONIZED_USERS.add(user);
        return true;
    }

    @Override
    public void onUserDisconnect(@NotNull UserDisconnectEvent event) {
        SYNCHRONIZED_USERS.remove(event.getUser());
    }

    public static boolean isSynchronized(User user) {
        return SYNCHRONIZED_USERS.contains(user);
    }
}
