package de.jakobhoermann.distsim.ui.javafx.component;

import de.jakobhoermann.distsim.core.algorithm.AlgorithmDescriptor;
import de.jakobhoermann.distsim.core.algorithm.AlgorithmRegistry;
import de.jakobhoermann.distsim.core.model.MessageKind;
import de.jakobhoermann.distsim.core.model.NodeId;
import de.jakobhoermann.distsim.core.model.snapshot.AlgorithmRunSummary;
import de.jakobhoermann.distsim.ui.javafx.viewmodel.MainViewModel;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.Comparator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EvaluationWindow {
    private static final Pattern NODE_ID_PATTERN = Pattern.compile("\\bnode-([A-Za-z0-9]+)\\b");
    private final MainViewModel viewModel;
    private final Stage stage = new Stage();
    private final Label algorithmLabel = valueLabel();
    private final Label initiatorLabel = valueLabel();
    private final Label statusLabel = valueLabel();
    private final Label durationLabel = valueLabel();
    private final Label eventsLabel = valueLabel();
    private final Label messagesLabel = valueLabel();
    private final Label activeNodesLabel = valueLabel();
    private final Label randomSeedLabel = valueLabel();
    private final Label resultLabel = valueLabel();
    private final Label exportFeedbackLabel = new Label();
    private final GridPane messagesByKindGrid = new GridPane();
    private boolean ownerInitialized;

    public EvaluationWindow(MainViewModel viewModel) {
        this.viewModel = viewModel;

        GridPane summaryGrid = new GridPane();
        summaryGrid.setHgap(12);
        summaryGrid.setVgap(7);
        addSummaryRow(summaryGrid, 0, "Algorithm", algorithmLabel);
        addSummaryRow(summaryGrid, 1, "Start node", initiatorLabel);
        addSummaryRow(summaryGrid, 2, "Status", statusLabel);
        addSummaryRow(summaryGrid, 3, "Duration", durationLabel);
        addSummaryRow(summaryGrid, 4, "Steps", eventsLabel);
        addSummaryRow(summaryGrid, 5, "Messages", messagesLabel);
        addSummaryRow(summaryGrid, 6, "Active nodes at start", activeNodesLabel);
        addSummaryRow(summaryGrid, 7, "Random seed", randomSeedLabel);
        addSummaryRow(summaryGrid, 8, "Result", resultLabel);

        Label messagesByKindTitle = new Label("Messages by Type");
        messagesByKindTitle.getStyleClass().add("evaluation-section-title");
        messagesByKindGrid.setHgap(18);
        messagesByKindGrid.setVgap(5);
        messagesByKindGrid.setMaxWidth(Double.MAX_VALUE);

        Button exportButton = new Button("Copy to clipboard");
        exportButton.getStyleClass().add("secondary-action");
        exportButton.setOnAction(event -> exportEvaluation());
        exportFeedbackLabel.getStyleClass().add("export-feedback");

        VBox root = new VBox(12, summaryGrid, messagesByKindTitle, messagesByKindGrid, exportButton, exportFeedbackLabel);
        root.getStyleClass().add("evaluation-window");
        root.setPadding(new Insets(14));
        root.setMinWidth(360);

        stage.setTitle("Algorithm Evaluation");
        Scene scene = new Scene(root, 390, 430);
        scene.getStylesheets().add(getClass()
                .getResource("/de/jakobhoermann/distsim/ui/javafx/application.css")
                .toExternalForm());
        stage.setScene(scene);

        viewModel.algorithmRunSummaryProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.simulationTimeProperty().addListener((observable, oldValue, newValue) -> refresh());
        refresh();
    }

    public void show(Window owner) {
        if (!ownerInitialized) {
            if (owner != null) {
                stage.initOwner(owner);
            }
            ownerInitialized = true;
        }
        refresh();
        if (stage.isShowing()) {
            stage.toFront();
            return;
        }
        stage.show();
    }

    private void refresh() {
        AlgorithmRunSummary summary = viewModel.algorithmRunSummaryProperty().get();
        if (summary == null || !summary.present()) {
            algorithmLabel.setText("-");
            initiatorLabel.setText("-");
            statusLabel.setText("-");
            durationLabel.setText("-");
            eventsLabel.setText("-");
            messagesLabel.setText("-");
            activeNodesLabel.setText("-");
            randomSeedLabel.setText("-");
            resultLabel.setText("-");
            updateMessagesByKind(Map.of());
            return;
        }

        long duration = summary.duration(viewModel.simulationTimeProperty().get());
        String result = summary.result().isBlank() ? "Pending" : compactNodeReferences(summary.result());
        algorithmLabel.setText(displayName(summary));
        initiatorLabel.setText(compactNodeLabel(summary.initiator()));
        statusLabel.setText(summary.completed() ? "Completed" : "In progress");
        durationLabel.setText(Long.toString(duration));
        eventsLabel.setText(Long.toString(summary.processedEvents()));
        messagesLabel.setText(summary.sentMessages() + " sent, " + summary.lostMessages() + " lost");
        activeNodesLabel.setText(Integer.toString(summary.activeNodesAtStart()));
        randomSeedLabel.setText(Long.toString(summary.randomSeed()));
        resultLabel.setText(result);
        updateMessagesByKind(summary.messagesByKind());
    }

    private void updateMessagesByKind(Map<MessageKind, Long> messagesByKind) {
        messagesByKindGrid.getChildren().clear();
        Label typeHeader = new Label("Type");
        Label countHeader = new Label("Count");
        typeHeader.getStyleClass().add("evaluation-key");
        countHeader.getStyleClass().add("evaluation-key");
        messagesByKindGrid.add(typeHeader, 0, 0);
        messagesByKindGrid.add(countHeader, 1, 0);

        if (messagesByKind.isEmpty()) {
            messagesByKindGrid.add(new Label("-"), 0, 1);
            messagesByKindGrid.add(new Label("0"), 1, 1);
            return;
        }

        int row = 1;
        for (Map.Entry<MessageKind, Long> entry : messagesByKind.entrySet().stream()
                .sorted(Comparator.comparing(messageEntry -> messageEntry.getKey().name()))
                .toList()) {
            messagesByKindGrid.add(new Label(entry.getKey().name()), 0, row);
            messagesByKindGrid.add(new Label(Long.toString(entry.getValue())), 1, row);
            row++;
        }
    }

    private void exportEvaluation() {
        ClipboardContent content = new ClipboardContent();
        content.putString(evaluationText());
        Clipboard.getSystemClipboard().setContent(content);
        showExportFeedback();
    }

    private void showExportFeedback() {
        exportFeedbackLabel.setText("Copied to clipboard");
        PauseTransition feedbackDelay = new PauseTransition(Duration.seconds(1.8));
        feedbackDelay.setOnFinished(event -> exportFeedbackLabel.setText(""));
        feedbackDelay.play();
    }

    private String evaluationText() {
        AlgorithmRunSummary summary = viewModel.algorithmRunSummaryProperty().get();
        if (summary == null || !summary.present()) {
            return "Algorithm Evaluation%nNo run available.".formatted();
        }

        long duration = summary.duration(viewModel.simulationTimeProperty().get());
        String result = summary.result().isBlank() ? "Pending" : compactNodeReferences(summary.result());
        StringBuilder builder = new StringBuilder();
        builder.append("Algorithm Evaluation").append(System.lineSeparator());
        builder.append("Algorithm: ").append(displayName(summary)).append(System.lineSeparator());
        builder.append("Start node: ").append(compactNodeLabel(summary.initiator())).append(System.lineSeparator());
        builder.append("Status: ").append(summary.completed() ? "Completed" : "In progress").append(System.lineSeparator());
        builder.append("Duration: ").append(duration).append(System.lineSeparator());
        builder.append("Steps: ").append(summary.processedEvents()).append(System.lineSeparator());
        builder.append("Messages sent: ").append(summary.sentMessages()).append(System.lineSeparator());
        builder.append("Messages lost: ").append(summary.lostMessages()).append(System.lineSeparator());
        builder.append("Active nodes at start: ").append(summary.activeNodesAtStart()).append(System.lineSeparator());
        builder.append("Random seed: ").append(summary.randomSeed()).append(System.lineSeparator());
        builder.append("Result: ").append(result).append(System.lineSeparator());
        builder.append(System.lineSeparator()).append("Messages by Type").append(System.lineSeparator());

        if (summary.messagesByKind().isEmpty()) {
            builder.append("-: 0").append(System.lineSeparator());
            return builder.toString();
        }

        summary.messagesByKind().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().name()))
                .forEach(entry -> builder.append(entry.getKey().name())
                        .append(": ")
                        .append(entry.getValue())
                        .append(System.lineSeparator()));
        return builder.toString();
    }

    private void addSummaryRow(GridPane grid, int row, String labelText, Label valueLabel) {
        Label label = new Label(labelText);
        label.getStyleClass().add("evaluation-key");
        label.setMinWidth(132);
        grid.add(label, 0, row);
        grid.add(valueLabel, 1, row);
        GridPane.setHgrow(valueLabel, Priority.ALWAYS);
    }

    private static Label valueLabel() {
        Label label = new Label();
        label.setWrapText(true);
        return label;
    }

    private String displayName(AlgorithmRunSummary summary) {
        return AlgorithmRegistry.descriptor(summary.algorithm())
                .map(AlgorithmDescriptor::displayName)
                .orElseGet(() -> summary.algorithm().toString());
    }

    private String compactNodeLabel(NodeId nodeId) {
        if (nodeId == null) {
            return "-";
        }
        String value = nodeId.value();
        int separatorIndex = value.lastIndexOf('-');
        if (separatorIndex >= 0 && separatorIndex < value.length() - 1) {
            return value.substring(separatorIndex + 1).toUpperCase();
        }
        return value;
    }

    private String compactNodeReferences(String text) {
        Matcher matcher = NODE_ID_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(1).toUpperCase()));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
