package de.jakobhoermann.distsim.ui.javafx.component;

import de.jakobhoermann.distsim.core.algorithm.AlgorithmDescriptor;
import de.jakobhoermann.distsim.core.algorithm.NodeMetadata;
import de.jakobhoermann.distsim.core.model.NodeId;
import de.jakobhoermann.distsim.core.model.ResourceId;
import de.jakobhoermann.distsim.ui.javafx.viewmodel.MainViewModel;
import de.jakobhoermann.distsim.ui.javafx.viewmodel.ResourceViewModel;
import de.jakobhoermann.distsim.ui.javafx.viewmodel.TopologyNodeViewModel;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

public final class NodeDetailsPanel extends VBox {
    private final MainViewModel viewModel;
    private final Label selectedLabel = new Label("Select a node in the topology.");
    private final Label stateLabel = new Label();
    private final VBox heldResourcesBox = new VBox(4);
    private final VBox awaitedResourcesBox = new VBox(4);
    private final Label deadlockLabel = new Label();
    private final Label mutexStateLabel = new Label();
    private final Label mutexRepliesLabel = new Label();
    private final Spinner<Integer> prioritySpinner = new Spinner<>();
    private final Spinner<Integer> lamportClockSpinner = new Spinner<>();
    private final HBox priorityRow;
    private final HBox lamportClockRow;
    private boolean refreshing;

    public NodeDetailsPanel(MainViewModel viewModel) {
        super(8);
        this.viewModel = viewModel;

        prioritySpinner.setEditable(true);
        prioritySpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 999, 1));
        priorityRow = new HBox(8, new Label("Priority"), prioritySpinner);
        priorityRow.setAlignment(Pos.CENTER_LEFT);

        lamportClockSpinner.setEditable(true);
        lamportClockSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 9999, 0));
        lamportClockRow = new HBox(8, new Label("Lamport clock"), lamportClockSpinner);
        lamportClockRow.setAlignment(Pos.CENTER_LEFT);

        getChildren().setAll(
                selectedLabel,
                stateLabel,
                heldResourcesBox,
                awaitedResourcesBox,
                deadlockLabel,
                lamportClockRow,
                mutexStateLabel,
                mutexRepliesLabel,
                priorityRow
        );
        setPadding(new Insets(2, 0, 8, 0));

        prioritySpinner.valueProperty().addListener((observable, oldValue, newValue) -> updatePriority(newValue));
        prioritySpinner.getEditor().setOnAction(event -> commitSpinnerEditor());
        prioritySpinner.focusedProperty().addListener((observable, oldValue, focused) -> {
            if (!focused) {
                commitSpinnerEditor();
            }
        });

        lamportClockSpinner.valueProperty().addListener((observable, oldValue, newValue) -> updateLamportClock(newValue));
        lamportClockSpinner.getEditor().setOnAction(event -> commitLamportClockSpinnerEditor());
        lamportClockSpinner.focusedProperty().addListener((observable, oldValue, focused) -> {
            if (!focused) {
                commitLamportClockSpinnerEditor();
            }
        });

        viewModel.selectedDetailsNodeProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.selectedAlgorithmProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.getNodes().addListener((ListChangeListener<? super TopologyNodeViewModel>) change -> refresh());
        viewModel.getResources().addListener((ListChangeListener<? super ResourceViewModel>) change -> refresh());
        refresh();
    }

    private void refresh() {
        refreshing = true;
        TopologyNodeViewModel selectedNode = findViewNode(
                viewModel.getNodes(),
                viewModel.selectedDetailsNodeProperty().get()
        );
        boolean hasSelection = selectedNode != null;
        selectedLabel.setText(hasSelection ? selectedNode.label() : "Select a node in the topology.");
        stateLabel.setText(hasSelection ? "State: " + (selectedNode.alive() ? "RUNNING" : "STOPPED") : "");
        deadlockLabel.setText(hasSelection ? "Deadlock: " + (selectedNode.deadlockDetected() ? "detected" : "no") : "");
        mutexStateLabel.setText(hasSelection ? "Mutex: " + mutexStateText(selectedNode) : "");
        mutexRepliesLabel.setText(hasSelection
                ? "Replies: " + selectedNode.acknowledgementsReceived()
                        + ", deferred: " + selectedNode.deferredReplies()
                : "");
        refreshResourceEditors(selectedNode);
        setNodeRowsVisible(hasSelection, viewModel.selectedAlgorithmProperty().get());
        if (hasSelection) {
            prioritySpinner.getValueFactory().setValue(selectedNode.electionPriority());
            lamportClockSpinner.getValueFactory().setValue((int) Math.min(9999, selectedNode.lamportClock()));
        }
        refreshing = false;
    }

    private void updatePriority(Integer newValue) {
        TopologyNodeViewModel selectedNode = findViewNode(
                viewModel.getNodes(),
                viewModel.selectedDetailsNodeProperty().get()
        );
        if (!refreshing && selectedNode != null && newValue != null
                && newValue != selectedNode.electionPriority()) {
            viewModel.updateNodePriority(selectedNode.nodeId(), newValue);
        }
    }

    private void updateLamportClock(Integer newValue) {
        TopologyNodeViewModel selectedNode = findViewNode(
                viewModel.getNodes(),
                viewModel.selectedDetailsNodeProperty().get()
        );
        if (!refreshing && selectedNode != null && newValue != null
                && newValue.longValue() != selectedNode.lamportClock()) {
            viewModel.updateNodeLamportClock(selectedNode.nodeId(), newValue);
        }
    }

    private void setNodeRowsVisible(boolean visible, AlgorithmDescriptor algorithm) {
        boolean electionMetadataVisible = visible && algorithm != null
                && algorithm.usesMetadata(NodeMetadata.ELECTION_PRIORITY);
        boolean deadlockMetadataVisible = visible && algorithm != null
                && algorithm.usesMetadata(NodeMetadata.DEADLOCK_RESOURCES);
        boolean lamportClockVisible = visible && algorithm != null
                && algorithm.usesMetadata(NodeMetadata.LAMPORT_CLOCK);
        boolean mutexMetadataVisible = visible && algorithm != null
                && algorithm.usesMetadata(NodeMetadata.MUTEX_STATE);

        stateLabel.setVisible(visible);
        stateLabel.setManaged(visible);
        heldResourcesBox.setVisible(deadlockMetadataVisible);
        heldResourcesBox.setManaged(deadlockMetadataVisible);
        awaitedResourcesBox.setVisible(deadlockMetadataVisible);
        awaitedResourcesBox.setManaged(deadlockMetadataVisible);
        deadlockLabel.setVisible(deadlockMetadataVisible);
        deadlockLabel.setManaged(deadlockMetadataVisible);
        lamportClockRow.setVisible(lamportClockVisible);
        lamportClockRow.setManaged(lamportClockVisible);
        mutexStateLabel.setVisible(mutexMetadataVisible);
        mutexStateLabel.setManaged(mutexMetadataVisible);
        mutexRepliesLabel.setVisible(mutexMetadataVisible);
        mutexRepliesLabel.setManaged(mutexMetadataVisible);
        priorityRow.setVisible(electionMetadataVisible);
        priorityRow.setManaged(electionMetadataVisible);
    }

    private String mutexStateText(TopologyNodeViewModel selectedNode) {
        if (selectedNode.inCriticalSection()) {
            return "in critical section";
        }
        if (selectedNode.requestingCriticalSection()) {
            return "requesting";
        }
        return "idle";
    }

    private void refreshResourceEditors(TopologyNodeViewModel selectedNode) {
        heldResourcesBox.getChildren().clear();
        awaitedResourcesBox.getChildren().clear();

        heldResourcesBox.getChildren().add(new Label("Holds"));
        awaitedResourcesBox.getChildren().add(new Label("Requests"));
        if (selectedNode == null) {
            return;
        }

        for (ResourceViewModel resource : viewModel.getResources()) {
            heldResourcesBox.getChildren().add(heldResourceCheckBox(selectedNode, resource));
            awaitedResourcesBox.getChildren().add(awaitedResourceCheckBox(selectedNode, resource));
        }
    }

    private CheckBox heldResourceCheckBox(TopologyNodeViewModel selectedNode, ResourceViewModel resource) {
        CheckBox checkBox = new CheckBox(resource.label());
        checkBox.setSelected(selectedNode.heldResources().contains(resource.resourceId()));
        checkBox.setOnAction(event -> {
            if (!refreshing) {
                viewModel.setNodeHoldsResource(selectedNode.nodeId(), resource.resourceId(), checkBox.isSelected());
            }
        });
        return checkBox;
    }

    private CheckBox awaitedResourceCheckBox(TopologyNodeViewModel selectedNode, ResourceViewModel resource) {
        ResourceId resourceId = resource.resourceId();
        CheckBox checkBox = new CheckBox(resource.label());
        checkBox.setSelected(selectedNode.awaitedResources().contains(resourceId));
        checkBox.setDisable(selectedNode.heldResources().contains(resourceId));
        checkBox.setOnAction(event -> {
            if (!refreshing) {
                viewModel.setNodeAwaitsResource(selectedNode.nodeId(), resourceId, checkBox.isSelected());
            }
        });
        return checkBox;
    }

    private TopologyNodeViewModel findViewNode(List<TopologyNodeViewModel> nodes, NodeId nodeId) {
        if (nodeId == null) {
            return null;
        }
        return nodes.stream()
                .filter(node -> node.nodeId().equals(nodeId))
                .findFirst()
                .orElse(null);
    }

    private void commitSpinnerEditor() {
        try {
            prioritySpinner.increment(0);
        } catch (NumberFormatException ignored) {
            prioritySpinner.getEditor().setText(prioritySpinner.getValue().toString());
        }
    }

    private void commitLamportClockSpinnerEditor() {
        try {
            lamportClockSpinner.increment(0);
        } catch (NumberFormatException ignored) {
            lamportClockSpinner.getEditor().setText(lamportClockSpinner.getValue().toString());
        }
    }
}
