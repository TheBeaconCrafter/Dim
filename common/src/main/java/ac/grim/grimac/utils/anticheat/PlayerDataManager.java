package ac.grim.grimac.utils.anticheat;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.event.events.GrimJoinEvent;
import ac.grim.grimac.api.event.events.GrimQuitEvent;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.platform.api.player.PlatformPlayer;
import ac.grim.grimac.events.packets.ProxyAlertMessenger;
import ac.grim.grimac.platform.api.player.PlatformPlayerCache;
import ac.grim.grimac.utils.reflection.GeyserUtil;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.netty.channel.ChannelHelper;
import com.github.retrooper.packetevents.protocol.player.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class PlayerDataManager {

    // Holder — PlayerDataManager is constructed inside GrimAPI's ctor, so a
    // plain static-final would see a null GrimAPI.INSTANCE. Holder init runs
    // on first fire, after GrimAPI is fully built.
    private static final class Channels {
        static final GrimJoinEvent.Channel JOIN = GrimAPI.INSTANCE.getEventBus().get(GrimJoinEvent.class);
        static final GrimQuitEvent.Channel QUIT = GrimAPI.INSTANCE.getEventBus().get(GrimQuitEvent.class);
    }

    private final Set<User> exemptUsers = ConcurrentHashMap.newKeySet();
    private final Set<User> pendingProxyUsers = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<User, GrimPlayer> playerDataMap = new ConcurrentHashMap<>();
    private static final long PROXY_PROTOCOL_WAIT_MILLIS = 1000L;

    public boolean isExemptUser(@Nullable User user) {
        return user != null && exemptUsers.contains(user);
    }

    public void exemptUser(@Nullable User user) {
        if (user == null) return;
        exemptUsers.add(user);
    }

    public boolean clearExemptions(@Nullable User user) {
        if (user == null) return false;
        return exemptUsers.remove(user);
    }

    @Nullable
    public GrimPlayer getPlayer(final @NotNull UUID uuid) {
        // Is it safe to interact with this, or is this internal PacketEvents code?
        Object channel = PacketEvents.getAPI().getProtocolManager().getChannel(uuid);
        if (channel == null) return null;
        User user = PacketEvents.getAPI().getProtocolManager().getUser(channel);
        if (user == null) return null;
        return getPlayer(user);
    }

    @Nullable
    public GrimPlayer getPlayer(final @NotNull User user) {
        @Nullable GrimPlayer player = playerDataMap.get(user);
        if (player != null && player.platformPlayer != null && player.platformPlayer.isExternalPlayer())
            return null;
        return player;
    }

    public boolean shouldCheck(@NotNull User user) {
        if (isExemptUser(user)) return false;
        if (!ChannelHelper.isOpen(user.getChannel())) return false;

        if (user.getUUID() != null) {
            // Bedrock players don't have Java movement
            if (GeyserUtil.isBedrockPlayer(user.getUUID())) {
                exemptUser(user);
                return false;
            }

            // Has exempt permission
            GrimPlayer grimPlayer = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(user);
            if (grimPlayer != null && grimPlayer.hasPermission("grim.exempt")) {
                exemptUser(user);
                return false;
            }

            // Geyser formatted player string
            // This will never happen for Java players, as the first character in the 3rd group is always 4 (xxxxxxxx-xxxx-4xxx-xxxx-xxxxxxxxxxxx)
            if (user.getUUID().toString().startsWith("00000000-0000-0000-0009")) {
                exemptUser(user);
                return false;
            }
        }

        return true;
    }

    public void addUser(final @NotNull User user) {
        if (!shouldCheck(user)) return;

        // A proxy running ViaVersion presents the backend protocol to
        // PacketEvents. Wait briefly for the optional bridge message so all
        // version-dependent checks are constructed with the real client
        // version instead of trying to repair final fields after login.
        if (ProxyAlertMessenger.isUsingProxy() && pendingProxyUsers.add(user)) {
            ((io.netty.channel.Channel) user.getChannel()).eventLoop().schedule(() -> {
                if (pendingProxyUsers.remove(user)) {
                    addUserNow(user);
                }
            }, PROXY_PROTOCOL_WAIT_MILLIS, TimeUnit.MILLISECONDS);
            return;
        }

        addUserNow(user);
    }

    /** Creates the player immediately after the proxy protocol bridge has synchronized it. */
    public void addUserAfterProtocolSync(final @NotNull User user) {
        if (playerDataMap.containsKey(user)) return;
        pendingProxyUsers.remove(user);
        if (shouldCheck(user)) addUserNow(user);
    }

    private void addUserNow(final @NotNull User user) {
        if (playerDataMap.containsKey(user) || !shouldCheck(user)) return;
        GrimPlayer player = new GrimPlayer(user);
        playerDataMap.put(user, player);
        Channels.JOIN.fire(player);
    }

    public GrimPlayer remove(final @NotNull User user) {
        pendingProxyUsers.remove(user);
        return playerDataMap.remove(user);
    }

    public void onDisconnect(User user) {
        GrimPlayer grimPlayer = remove(user);
        if (grimPlayer != null) Channels.QUIT.fire(grimPlayer);
        clearExemptions(user);

        UUID uuid = user.getProfile().getUUID();

        // All cleanup paths should call onDisconnect; routing the session-close + toggle
        // eviction here means a stuck PE event (or a JVM-level channel
        // close that doesn't surface as UserDisconnectEvent) doesn't leak an open session.
        // hooks/toggles are NOOP when the datastore is disabled or its init failed
        // AND go NOOP mid-session if an operator runs /grim reload after flipping database.enabled to false
        // a player who joined under the prior (enabled) config and disconnects post-reload has no live writer to fire onQuit, so their session stays open (row closed_at IS NULL).
        // The next datastore-enabled boot's crash sweep stamps closed_at = last_activity for still-open rows; permanently-disabled-after-the-fact leaves the row untouched until DB is enabled again.
        GrimAPI.INSTANCE.getDataStoreLifecycle().liveWriteHooks()
                .onQuitFromUserDisconnect(user, grimPlayer, System.currentTimeMillis());
        if (uuid != null) {
            GrimAPI.INSTANCE.getDataStoreLifecycle().playerToggleStore().evict(uuid);
        }

        // Check if calling async is safe
        if (uuid == null)
            return; // folia doesn't like null getPlayer()

        PlatformPlayer quittingPlayer = PlatformPlayerCache.getInstance().getPlayer(uuid);
        if (quittingPlayer != null) {
            GrimAPI.INSTANCE.getAlertManager().handlePlayerQuit(quittingPlayer);
        }

        GrimAPI.INSTANCE.getSpectateManager().onQuit(uuid);

        // TODO (Cross-platform) confirm this is 100% correct and will always remove players from cache when necessary
        GrimAPI.INSTANCE.getPlatformPlayerFactory().invalidatePlayer(uuid);
    }

    public Collection<GrimPlayer> getEntries() {
        return playerDataMap.values();
    }

    public int size() {
        return playerDataMap.size();
    }
}
