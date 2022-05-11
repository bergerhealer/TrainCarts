package com.bergerkiller.bukkit.tc.controller.status;

import java.util.List;

/**
 * Provides debugging information about the current status of a train. This information
 * should summarize what kind of action or behavior the train is currently doing.
 */
public interface TrainStatusProvider {

    /**
     * Gets a List of status results for the train. If not empty, this should
     * explain what the train is currently doing according this provider.
     * For example, if the train is waiting for something to occur, or is
     * launching the train to a new speed.
     *
     * @return status info (unmodifiable)
     */
    List<TrainStatus> getStatusInfo();
}
