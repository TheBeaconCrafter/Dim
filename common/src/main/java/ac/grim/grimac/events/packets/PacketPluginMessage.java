package ac.grim.grimac.events.packets;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.platform.api.player.PlatformPlayer;
import ac.grim.grimac.utils.anticheat.LogUtil;
import ac.grim.grimac.utils.anticheat.MessageUtil;
import ac.grim.grimac.utils.common.arguments.CommonGrimArguments;
import ac.grim.grimac.utils.viaversion.ViaVersionUtil;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.configuration.client.WrapperConfigClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDisconnect;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PacketPluginMessage extends PacketListenerAbstract {
    private static final long PROTOCOL_BRIDGE_GRACE_MILLIS = 1500L;

    public void onPacketReceive(@NotNull PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PLUGIN_MESSAGE) {
            WrapperPlayClientPluginMessage packet = new WrapperPlayClientPluginMessage(event);
            checkChannel(event.getUser(), packet.getChannelName(), packet.getData());
        } else if (event.getPacketType() == PacketType.Configuration.Client.PLUGIN_MESSAGE) {
            WrapperConfigClientPluginMessage packet = new WrapperConfigClientPluginMessage(event);
            checkChannel(event.getUser(), packet.getChannelName(), packet.getData());
        }
    }

    private void checkChannel(User user, String channelName, byte[] data) {
        if (!"vv:proxy_details".equals(channelName)) return;
        final boolean usingProxy = ProxyAlertMessenger.isUsingProxy();
        boolean synchronizedViaVersion = usingProxy && trySynchronizeViaVersionPayload(user, data);
        if (usingProxy && !synchronizedViaVersion && !ProtocolVersionSyncListener.isSynchronized(user)) {
            requestProtocolSync(user);
        }
        // warn if they are using a proxy
        if (usingProxy && !ProtocolVersionSyncListener.isSynchronized(user)) {
            // Velocity can deliver ViaVersion's marker before its own
            // ServerConnectedEvent payload reaches this backend. Do not warn
            // during that normal handshake race; re-check after the bridge's
            // player-state grace period instead.
            GrimAPI.INSTANCE.getScheduler().getAsyncScheduler().runDelayed(
                    GrimAPI.INSTANCE.getGrimPlugin(),
                    () -> warnIfProtocolBridgeIsMissing(user),
                    PROTOCOL_BRIDGE_GRACE_MILLIS,
                    TimeUnit.MILLISECONDS
            );
        }
        // kick if they do not have a proxy configured OR they have ViaVersion installed on the backend
        if (CommonGrimArguments.KICK_ON_VIA_PROXY.value() && (!usingProxy || ViaVersionUtil.isAvailable)) {

            LogUtil.warn(user.getName() + " is being disconnected for sending ViaVersion proxy data.");

            try {
                WrapperPlayServerDisconnect disconnect = new WrapperPlayServerDisconnect(
                        MessageUtil.miniMessage(GrimAPI.INSTANCE.getConfigManager().getDisconnectPacketError())
                );
                user.sendPacket(disconnect);
            } catch (Exception e) {
                LogUtil.warn("Failed to send disconnect packet to kick " + user.getName() + "!");
            }
            user.closeConnection();
        }
    }

    private static boolean trySynchronizeViaVersionPayload(User user, byte[] data) {
        try {
            JsonObject payload = new JsonParser()
                    .parse(new String(data, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            if (!payload.has("version")) return false;
            return ProtocolVersionSyncListener.synchronize(user, payload.get("version").getAsInt());
        } catch (Exception ignored) {
            // Older ViaVersion builds may not send the JSON proxy-details
            // payload. Fall back to the BeaconLabs request/response channel.
            return false;
        }
    }

    private static void requestProtocolSync(User user) {
        UUID uuid = user.getUUID();
        if (uuid == null) return;

        PlatformPlayer platformPlayer = GrimAPI.INSTANCE.getPlatformPlayerFactory().getFromUUID(uuid);
        if (platformPlayer == null) return;

        GrimAPI.INSTANCE.getScheduler().getEntityScheduler().execute(
                platformPlayer,
                GrimAPI.INSTANCE.getGrimPlugin(),
                () -> {
                    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                         DataOutputStream output = new DataOutputStream(bytes)) {
                        output.writeUTF(uuid.toString());
                        output.flush();
                        platformPlayer.sendPluginMessage(ProtocolVersionSyncListener.REQUEST_CHANNEL, bytes.toByteArray());
                    } catch (IOException exception) {
                        LogUtil.warn("Failed to request proxy protocol data for " + user.getName() + ".", exception);
                    }
                },
                null,
                1
        );
    }

    private static void warnIfProtocolBridgeIsMissing(User user) {
        if (!ProtocolVersionSyncListener.isSynchronized(user)) {
            LogUtil.warn(
                    user.getName() + " seems to have connected through a proxy running ViaVersion without a "
                            + "Dim-compatible protocol bridge. Install the BeaconLabsVelocity bridge or "
                            + "install ViaVersion on the backend server instead."
            );
        }
    }

}
