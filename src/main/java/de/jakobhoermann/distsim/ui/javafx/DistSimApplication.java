package de.jakobhoermann.distsim.ui.javafx;

import de.jakobhoermann.distsim.core.algorithm.AlgorithmRegistry;
import de.jakobhoermann.distsim.app.service.SimulationApplicationService;
import de.jakobhoermann.distsim.core.engine.DefaultSimulationEngine;
import de.jakobhoermann.distsim.ui.javafx.component.EventLogListView;
import de.jakobhoermann.distsim.ui.javafx.component.MessageListView;
import de.jakobhoermann.distsim.ui.javafx.component.SimulationToolbar;
import de.jakobhoermann.distsim.ui.javafx.component.TopologyCanvas;
import de.jakobhoermann.distsim.ui.javafx.viewmodel.MainViewModel;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public final class DistSimApplication extends Application {
    @Override
    public void start(Stage stage) {
        MainViewModel viewModel = new MainViewModel(
                new SimulationApplicationService(new DefaultSimulationEngine())
        );
        viewModel.loadScenarioWithFreshRandomSeed(AlgorithmRegistry.defaultScenarioFor(AlgorithmRegistry.defaultAlgorithm()));

        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setTop(new SimulationToolbar(viewModel));
        root.setCenter(buildMainContent(viewModel));

        Scene scene = new Scene(root, 980, 620);
        scene.getStylesheets().add(stylesheet());
        stage.setTitle("Distributed Systems Simulator");
        stage.setScene(scene);
        stage.show();
    }

    private SplitPane buildMainContent(MainViewModel viewModel) {
        SplitPane splitPane = new SplitPane(
                buildTopologyPane(viewModel),
                buildSidePane(viewModel)
        );
        splitPane.setDividerPositions(0.65);
        return splitPane;
    }

    private VBox buildTopologyPane(MainViewModel viewModel) {
        Label statusLabel = new Label();
        statusLabel.textProperty().bind(viewModel.simulationTimeProperty().asString("Simulation time: %d"));

        TopologyCanvas topologyCanvas = new TopologyCanvas(viewModel);
        VBox pane = new VBox(10, statusLabel, topologyCanvas);
        pane.getStyleClass().add("topology-pane");
        pane.setPadding(new Insets(16));
        VBox.setVgrow(topologyCanvas, Priority.ALWAYS);
        return pane;
    }

    private VBox buildSidePane(MainViewModel viewModel) {
        EventLogListView eventLogList = new EventLogListView(viewModel);

        VBox pane = new VBox(10,
                sectionTitle("Event Log"),
                eventLogList,
                sectionTitle("Messages In Flight"),
                new MessageListView(viewModel)
        );
        pane.getStyleClass().add("side-pane");
        pane.setPadding(new Insets(16));
        VBox.setVgrow(eventLogList, Priority.ALWAYS);
        return pane;
    }

    private Label sectionTitle(String title) {
        Label label = new Label(title);
        label.getStyleClass().add("section-title");
        label.setFont(Font.font(16));
        return label;
    }

    private String stylesheet() {
        return getClass().getResource("/de/jakobhoermann/distsim/ui/javafx/application.css").toExternalForm();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
