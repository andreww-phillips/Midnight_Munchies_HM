package com.midnightmunchies.application.dash;

import java.util.List;
import java.util.UUID;

public interface DispatchBacklog {
    List<UUID> waitingOrderIds();
}
