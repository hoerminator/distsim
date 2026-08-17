package de.jakobhoermann.distsim.ui.javafx.viewmodel;

import de.jakobhoermann.distsim.core.model.snapshot.EventLogCategory;

public record EventLogRowViewModel(
        boolean timeHeader,
        long time,
        EventLogCategory category,
        String message
) {
    public static EventLogRowViewModel timeHeader(long time) {
        return new EventLogRowViewModel(true, time, null, "t=" + time);
    }

    public static EventLogRowViewModel entry(long time, EventLogCategory category, String message) {
        return new EventLogRowViewModel(false, time, category, message);
    }

    public String plainText() {
        if (timeHeader) {
            return message;
        }
        return "  [" + category + "] " + message;
    }
}
