package com.rusefi.server;

import com.rusefi.io.IoStream;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class ApplicationConnectionState {
    private final UserDetails userDetails;
    @NotNull
    private final IoStream clientStream;
    private final ControllerConnectionState state;

    public ApplicationConnectionState(UserDetails userDetails, ApplicationRequest applicationRequest, IoStream clientStream, ControllerConnectionState state) {
        this.userDetails = Objects.requireNonNull(userDetails, "userDetails");
        this.clientStream = Objects.requireNonNull(clientStream, "clientStream");
        this.state = Objects.requireNonNull(state, "state");

        if (clientStream.getStreamStats().getPreviousPacketArrivalTime() == 0)
            throw new IllegalStateException("Invalid state - no packets on " + this);
        if (!state.isUsed())
            throw new IllegalArgumentException("state is supposed to be used by us");
    }

    @NotNull
    public IoStream getClientStream() {
        return clientStream;
    }

    public UserDetails getUserDetails() {
        return userDetails;
    }

    public void close() {
        try {
            clientStream.close();
        } finally {
            state.release();
        }
    }

    @Override
    public String toString() {
        return "ApplicationConnectionState{" +
                "userDetails=" + userDetails +
                '}';
    }
}
