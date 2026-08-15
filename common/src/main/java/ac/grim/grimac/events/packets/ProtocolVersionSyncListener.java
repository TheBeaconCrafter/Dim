package ac.grim.grimac.events.packets;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.LogUtil;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.PacketEvents;
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
    private static final Set<User> TRANSLATED_WARNINGS = ConcurrentHashMap.newKeySet();

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
            if (!messageUuid.equals(user.getUUID())) {
                LogUtil.warn("[Dim proxy-debug] Ignoring protocol payload for " + messageUuid + " on " + user.getName() + ": UUID mismatch.");
                return;
            }
            LogUtil.info("[Dim proxy-debug] Received " + CHANNEL + " for " + user.getName() + " (protocol=" + protocol + ", backend=" + backendProtocol() + ").");
            synchronize(user, protocol, "BeaconLabsVelocity");
        } catch (Exception exception) {
            LogUtil.warn("[Dim proxy-debug] Failed to read proxy protocol data for " + event.getUser().getName() + ".", exception);
        }
    }

    /**
     * Applies a trusted protocol value supplied by ViaVersion or the proxy
     * bridge. The User is the connection that delivered the payload, so the
     * ViaVersion payload does not need a second UUID field.
     */
    public static boolean synchronize(@NotNull User user, int protocol) {
        return synchronize(user, protocol, "proxy");
    }

    public static boolean synchronize(@NotNull User user, int protocol, @NotNull String source) {
        ClientVersion clientVersion = ClientVersion.getById(protocol);
        if (clientVersion == ClientVersion.UNKNOWN
                || clientVersion == ClientVersion.LOWER_THAN_SUPPORTED_VERSIONS
                || clientVersion == ClientVersion.HIGHER_THAN_SUPPORTED_VERSIONS) {
            LogUtil.warn("[Dim proxy-debug] Ignoring unsupported proxy protocol " + protocol + " for " + user.getName() + ".");
            return false;
        }

        GrimPlayer player = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(user);
        boolean existingPlayer = player != null;
        if (player != null) {
            player.applyClientVersion(clientVersion);
        } else {
            // If the sync arrives before Dim creates its player state,
            // construct it with the simulation version while leaving the
            // User's backend protocol intact for PacketEvents decoding.
            GrimAPI.INSTANCE.getPlayerDataManager().addUserAfterProtocolSync(user, clientVersion);
        }
        SYNCHRONIZED_USERS.add(user);
        String backend = backendProtocol();
        LogUtil.info("[Dim proxy-debug] Synchronized " + user.getName() + " from " + source
                + " (client=" + clientVersion + ", protocol=" + protocol
                + ", backend=" + backend + ", backendSource=PacketEventsServer, existingPlayer=" + existingPlayer + ").");

        ClientVersion backendVersion = backendVersion();
        if (backendVersion != ClientVersion.UNKNOWN
                && backendVersion != ClientVersion.LOWER_THAN_SUPPORTED_VERSIONS
                && backendVersion != ClientVersion.HIGHER_THAN_SUPPORTED_VERSIONS
                && backendVersion.getProtocolVersion() != protocol
                && TRANSLATED_WARNINGS.add(user)) {
            LogUtil.warn("[Dim proxy-debug] " + user.getName()
                    + " is using translated movement: client=" + clientVersion.getReleaseName()
                    + "/" + protocol + ", backend=" + backendVersion.getReleaseName()
                    + "/" + backendVersion.getProtocolVersion()
                    + ". Dim will simulate the client protocol, but ViaVersion must deliver valid backend movement packets.");
        }
        return true;
    }

    private static String backendProtocol() {
        ClientVersion backend = backendVersion();
        return backend == ClientVersion.UNKNOWN ? "unknown" : backend + "/" + backend.getProtocolVersion();
    }

    private static ClientVersion backendVersion() {
        if (PacketEvents.getAPI() == null || PacketEvents.getAPI().getServerManager() == null) {
            return ClientVersion.UNKNOWN;
        }
        return ClientVersion.getById(PacketEvents.getAPI().getServerManager().getVersion().getProtocolVersion());
    }

    @Override
    public void onUserDisconnect(@NotNull UserDisconnectEvent event) {
        User user = event.getUser();
        SYNCHRONIZED_USERS.remove(user);
        TRANSLATED_WARNINGS.remove(user);
    }

    public static boolean isSynchronized(User user) {
        return SYNCHRONIZED_USERS.contains(user);
    }

    /**
     * Returns whether the synchronized client protocol differs from the
     * protocol PacketEvents decodes on the backend connection. A proxy can be
     * present without translating anything when both sides use the same
     * protocol; those sessions must retain normal Grim packet semantics.
     */
    public static boolean isProtocolTranslated(User user) {
        if (!isSynchronized(user)) return false;

        GrimPlayer player = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(user);
        if (player == null) return false;

        ClientVersion backend = backendVersion();
        if (backend == ClientVersion.UNKNOWN
                || backend == ClientVersion.LOWER_THAN_SUPPORTED_VERSIONS
                || backend == ClientVersion.HIGHER_THAN_SUPPORTED_VERSIONS) {
            return false;
        }

        return player.getClientVersion().getProtocolVersion() != backend.getProtocolVersion();
    }
}
