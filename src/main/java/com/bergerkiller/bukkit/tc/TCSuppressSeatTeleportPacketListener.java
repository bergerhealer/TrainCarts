package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.events.PacketSendEvent;
import com.bergerkiller.bukkit.common.protocol.PacketListener;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.seat.MemberBeforeSeatEnterEvent;
import com.bergerkiller.bukkit.tc.events.seat.MemberSeatExitEvent;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.WeakHashMap;

/**
 * Temporary (???) packet and event listener to suppress position (teleport) packets sent to players
 * when entering seats.
 */
class TCSuppressSeatTeleportPacketListener implements Listener, PacketListener {
    /**
     * Since Minecraft 1.20 the server sends a teleport/position packet to the player (that has no Bukkit event to cancel)
     * when entering carts. This packet must be suppressed in a special way.
     * BKCommonLib also has a new API to set the position of passengers which also fixes this issue. If that API exists
     * and is used, then we don't have to suppress it.
     */
    public static final boolean SUPPRESS_POST_ENTER_PLAYER_POSITION_PACKET = !Common.hasCapability("Common:EntityController:PositionPassenger") &&
                                                                             Common.evaluateMCVersion(">=", "1.20");

    public static final PacketType[] LISTENED_TYPES = new PacketType[] {
            PacketType.OUT_POSITION
    };

    private final TrainCarts traincarts;
    private final WeakHashMap<Player, SeatMoment> seatEnterMoments = new WeakHashMap<>();

    public TCSuppressSeatTeleportPacketListener(TrainCarts traincarts) {
        this.traincarts = traincarts;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public synchronized void onMemberSeatExitEvent(MemberSeatExitEvent event) {
        if (event.isPlayer()) {
            SeatMoment moment = seatEnterMoments.remove((Player) event.getEntity());

            // If not storing for this minecart, keep the old one
            if (moment != null && moment.entityId != event.getMember().getEntity().getEntityId()) {
                seatEnterMoments.put((Player) event.getEntity(), moment);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public synchronized void onMemberBeforeSeatEnterEvent(MemberBeforeSeatEnterEvent event) {
        if (event.isPlayer() && !event.getMember().getProperties().getModel().isDefault()) {
            seatEnterMoments.put((Player) event.getEntity(), new SeatMoment(event.getMember()));
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
    }

    @Override
    public synchronized void onPacketSend(PacketSendEvent event) {
        if (event.getType() == PacketType.OUT_POSITION) {
            SeatMoment moment = seatEnterMoments.remove(event.getPlayer());
            if (moment != null && CommonUtil.getServerTicks() < moment.expire) {
                event.setCancelled(true);

                // This makes the client await teleport go out of sync, so correct this
                // Must be done on main thread
                if (CommonUtil.isMainThread()) {
                    Util.resetPlayerAwaitingTeleport(event.getPlayer());
                } else {
                    CommonUtil.nextTick(() -> Util.resetPlayerAwaitingTeleport(event.getPlayer()));
                }
            }
        }
    }

    private static class SeatMoment {
        public final int expire;
        public final int entityId;

        public SeatMoment(MinecartMember<?> member) {
            this.expire = CommonUtil.getServerTicks() + 20; // 1 second time to process packet should be enough
            this.entityId = member.getEntity().getEntityId();
        }
    }
}
