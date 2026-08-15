package ac.grim.grimac.platform.api;

import ac.grim.grimac.platform.api.sender.Sender;

public interface PlatformServer {

    String getPlatformImplementationString();

    void dispatchCommand(Sender sender, String command);

    Sender getConsoleSender();

    void registerOutgoingPluginChannel(String name);

    /** Registers a backend-to-plugin incoming channel where the platform supports it. */
    default void registerIncomingPluginChannel(String name) {
    }

    double getTPS();
}
