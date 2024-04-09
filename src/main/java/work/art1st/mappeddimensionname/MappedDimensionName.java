package work.art1st.mappeddimensionname;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.connection.registry.DimensionInfo;
import com.velocitypowered.proxy.protocol.packet.JoinGamePacket;
import com.velocitypowered.proxy.protocol.packet.RespawnPacket;
import dev.simplix.protocolize.api.Direction;
import dev.simplix.protocolize.api.Protocolize;
import dev.simplix.protocolize.api.listener.AbstractPacketListener;
import dev.simplix.protocolize.api.listener.PacketReceiveEvent;
import dev.simplix.protocolize.api.listener.PacketSendEvent;
import lombok.Getter;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Plugin(
        id = "mappeddimensionname",
        name = "MappedDimensionName",
        version = BuildConstants.VERSION
)
public class MappedDimensionName {
    public static class JoinGamePacketListener extends AbstractPacketListener<JoinGamePacket> {
        public JoinGamePacketListener() {
            super(JoinGamePacket.class, Direction.UPSTREAM, 0);
        }
        @Override
        public void packetReceive(PacketReceiveEvent<JoinGamePacket> event) {}
        @Override
        public void packetSend(PacketSendEvent<JoinGamePacket> event) {
            Player player = event.player().handle();
            MappedDimensionName.getInstance().getLogger().info("Mapping levelNames for " + player.getUsername());
            /* Player had not connected to the server yet. getCurrentServer() is null. */
            Optional<RegisteredServer> server = MappedDimensionName.getInstance().getPlayerServerMap().get(player);
            if (server.isPresent()) {
                String prefix = server.get().getServerInfo().getName();
                MappedDimensionName.getInstance().getLogger().info("Mapping levelNames with prefix: " + prefix);
                JoinGamePacket joinPacket = event.packet();
                DimensionInfo dimensionInfo = joinPacket.getDimensionInfo();
                setPrivateField(dimensionInfo, "levelName", mapName(dimensionInfo.getLevelName(), prefix));
                ImmutableSet<String> levelNames = getPrivateField(joinPacket, "levelNames");
                if (levelNames != null) {
                    setPrivateField(joinPacket, "levelNames", mapNames(levelNames, prefix));
                } else {
                    MappedDimensionName.getInstance().getLogger().warn("Failed to map levelNames in JoinPacket: levelNames is null.");
                }
            } else {
                MappedDimensionName.getInstance().getLogger().warn("Failed to map levelNames in JoinPacket: server is null.");
            }
        }
    }

    public static class RespawnPacketListener extends AbstractPacketListener<RespawnPacket> {
        protected RespawnPacketListener() {
            super(RespawnPacket.class, Direction.UPSTREAM, 0);
        }

        @Override
        public void packetReceive(PacketReceiveEvent<RespawnPacket> event) {}
        @Override
        public void packetSend(PacketSendEvent<RespawnPacket> event) {
            Player player = event.player().handle();
            MappedDimensionName.getInstance().getLogger().info("Mapping levelName for " + player.getUsername());
            if (player.getCurrentServer().isPresent()) {
                String prefix = player.getCurrentServer().get().getServerInfo().getName();
                MappedDimensionName.getInstance().getLogger().info("Mapping levelNames with prefix: " + prefix);
                RespawnPacket respawnPacket = event.packet();
                DimensionInfo dimensionInfo = getPrivateField(respawnPacket, "dimensionInfo");
                if (dimensionInfo != null) {
                    setPrivateField(dimensionInfo, "levelName", mapName(dimensionInfo.getLevelName(), prefix));
                } else {
                    MappedDimensionName.getInstance().getLogger().warn("Failed to map levelNames in RespawnPacket: dimensionInfo is null.");
                }
            } else {
                MappedDimensionName.getInstance().getLogger().warn("Failed to map levelNames in RespawnPacket: server is null.");
            }
        }
    }

    private static void setPrivateField(Object obj, String fieldName, Object value) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }
    @SuppressWarnings("unchecked")
    private static <T> T getPrivateField(Object obj, String fieldName) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(obj);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }
    public static String mapName(String input, String prefix) {
        if (input == null) {
            return prefix;
        }
        String[] split = input.split(":", 2);
        if (split.length == 1) {
            return input + prefix;
        } else {
            return split[0] + ":" + prefix + "_" + split[1];
        }
    }
    private static ImmutableSet<String> mapNames(ImmutableSet<String> input, String prefix) {
        return input.stream().map(name -> mapName(name, prefix)).collect(ImmutableSet.toImmutableSet());
    }

    @Inject
    @Getter
    private Logger logger;
    @Getter
    private final Map<Player, Optional<RegisteredServer>> playerServerMap = new HashMap<>();
    @Getter
    private static MappedDimensionName instance;

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        instance = this;
        Protocolize.listenerProvider().registerListener(new JoinGamePacketListener());
        Protocolize.listenerProvider().registerListener(new RespawnPacketListener());
        logger.info("Plugin enabled.");
    }
    @Subscribe
    public void onPreJoin(ServerPreConnectEvent event) {
        this.playerServerMap.put(event.getPlayer(), event.getResult().getServer());
        if (event.getResult().getServer().isPresent()) {
            this.logger.info("Player " + event.getPlayer().getUsername() + " is connecting to " + event.getResult().getServer().get().getServerInfo().getName());
        }
    }
    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        this.playerServerMap.remove(event.getPlayer());
    }
}
