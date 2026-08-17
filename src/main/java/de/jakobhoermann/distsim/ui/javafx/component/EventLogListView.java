package de.jakobhoermann.distsim.ui.javafx.component;

import de.jakobhoermann.distsim.core.model.snapshot.EventLogCategory;
import de.jakobhoermann.distsim.ui.javafx.viewmodel.EventLogRowViewModel;
import de.jakobhoermann.distsim.ui.javafx.viewmodel.MainViewModel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public final class EventLogListView extends ListView<EventLogRowViewModel> {
    public EventLogListView(MainViewModel viewModel) {
        super(viewModel.getEventLogRows());
        setPrefWidth(320);
        setCellFactory(ignored -> new EventLogCell());
        setPlaceholder(centeredLabel("No events have been logged yet."));
    }

    private static Label centeredLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setAlignment(Pos.CENTER);
        return label;
    }

    private static final class EventLogCell extends ListCell<EventLogRowViewModel> {
        @Override
        protected void updateItem(EventLogRowViewModel row, boolean empty) {
            super.updateItem(row, empty);
            if (empty || row == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            setText(null);
            setGraphic(row.timeHeader() ? timeHeader(row) : eventRow(row));
        }

        private HBox timeHeader(EventLogRowViewModel row) {
            Label label = new Label(row.message());
            label.setFont(Font.font(null, FontWeight.BOLD, 12));
            label.setTextFill(Color.web("#4f5b66"));
            HBox container = new HBox(label);
            container.setPadding(new Insets(4, 0, 2, 0));
            return container;
        }

        private HBox eventRow(EventLogRowViewModel row) {
            Label icon = new Label(iconFor(row.category()));
            icon.setMinWidth(22);
            icon.setAlignment(Pos.CENTER);
            icon.setFont(Font.font(null, FontWeight.BOLD, 13));
            icon.setTextFill(colorFor(row.category()));

            Label message = new Label(row.message());
            message.setWrapText(true);
            message.setTextFill(Color.web("#1f1f1f"));

            HBox container = new HBox(6, icon, message);
            container.setAlignment(Pos.TOP_LEFT);
            container.setPadding(new Insets(2, 0, 2, 12));
            return container;
        }

        private String iconFor(EventLogCategory category) {
            return switch (category) {
                case MESSAGE -> "→";
                case STATE -> "●";
                case RESULT -> "✓";
                case WARNING -> "!";
                case GENERAL -> "•";
            };
        }

        private Color colorFor(EventLogCategory category) {
            return switch (category) {
                case MESSAGE -> Color.web("#cc5a2b");
                case STATE -> Color.web("#2f6f4e");
                case RESULT -> Color.web("#1f6feb");
                case WARNING -> Color.web("#d1495b");
                case GENERAL -> Color.web("#6f6f6f");
            };
        }
    }
}
