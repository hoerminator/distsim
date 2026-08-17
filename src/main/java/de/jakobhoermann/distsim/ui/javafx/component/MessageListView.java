package de.jakobhoermann.distsim.ui.javafx.component;

import de.jakobhoermann.distsim.core.model.snapshot.MessageSnapshot;
import de.jakobhoermann.distsim.core.model.NodeId;
import de.jakobhoermann.distsim.ui.javafx.viewmodel.MainViewModel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class MessageListView extends ListView<MessageSnapshot> {
    public MessageListView(MainViewModel viewModel) {
        setItems(viewModel.getMessagesInFlight());
        setCellFactory(ignored -> new MessageCell(viewModel));
        setPlaceholder(centeredLabel("No messages are currently in flight."));
        setPrefHeight(140);
    }

    private static String formatMessage(MessageSnapshot message) {
        return "%s -> %s   delivery t=%d".formatted(
                compactNodeLabel(message.from()),
                compactNodeLabel(message.to()),
                message.scheduledDeliveryTime()
        );
    }

    private static String compactNodeLabel(NodeId nodeId) {
        String value = nodeId.value();
        int separatorIndex = value.lastIndexOf('-');
        if (separatorIndex >= 0 && separatorIndex < value.length() - 1) {
            return value.substring(separatorIndex + 1).toUpperCase();
        }
        return value;
    }

    private static Label centeredLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setAlignment(Pos.CENTER);
        return label;
    }

    private static final class MessageCell extends ListCell<MessageSnapshot> {
        private final MainViewModel viewModel;

        private MessageCell(MainViewModel viewModel) {
            this.viewModel = viewModel;
        }

        @Override
        protected void updateItem(MessageSnapshot message, boolean empty) {
            super.updateItem(message, empty);
            if (empty || message == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            Label payload = new Label(message.payload());
            payload.getStyleClass().add("message-payload");
            Label route = new Label(formatMessage(message));
            route.getStyleClass().add("message-route");
            VBox text = new VBox(2, payload, route);
            HBox.setHgrow(text, Priority.ALWAYS);

            Button dropButton = new Button("Drop");
            dropButton.getStyleClass().add("danger-action");
            dropButton.setOnAction(event -> viewModel.dropMessage(message.id()));

            HBox row = new HBox(8, text, dropButton);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(2, 0, 2, 0));
            setText(null);
            setGraphic(row);
        }
    }
}
