package de.jakobhoermann.distsim.ui.javafx.component;

import de.jakobhoermann.distsim.ui.javafx.viewmodel.MainViewModel;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;

public final class TopologyControls extends HBox {
    public TopologyControls(MainViewModel viewModel) {
        super(8);
        Button addNodeButton = new Button("Add Node");
        addNodeButton.getStyleClass().add("secondary-action");
        addNodeButton.setOnAction(event -> viewModel.addNode(viewModel.selectedAlgorithmProperty().get()));

        Button addResourceButton = new Button("Add Resource");
        addResourceButton.getStyleClass().add("secondary-action");
        addResourceButton.setOnAction(event -> viewModel.addResource());
        addResourceButton.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> {
                    var selectedAlgorithm = viewModel.selectedAlgorithmProperty().get();
                    return selectedAlgorithm != null && selectedAlgorithm.isDeadlockDetection();
                },
                viewModel.selectedAlgorithmProperty()
        ));
        addResourceButton.managedProperty().bind(addResourceButton.visibleProperty());

        getChildren().setAll(addNodeButton, addResourceButton);
        setAlignment(Pos.CENTER_LEFT);
    }
}
