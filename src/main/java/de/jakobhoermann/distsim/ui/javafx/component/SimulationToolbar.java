package de.jakobhoermann.distsim.ui.javafx.component;

import de.jakobhoermann.distsim.core.algorithm.AlgorithmDescriptor;
import de.jakobhoermann.distsim.core.algorithm.AlgorithmRegistry;
import de.jakobhoermann.distsim.core.model.AlgorithmCategory;
import de.jakobhoermann.distsim.core.model.NodeId;
import de.jakobhoermann.distsim.core.model.snapshot.AlgorithmRunSummary;
import de.jakobhoermann.distsim.ui.javafx.viewmodel.MainViewModel;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Separator;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.util.List;

public final class SimulationToolbar extends ToolBar {
    private static final double BASE_AUTO_STEP_DELAY_MILLIS = 450;
    private boolean updatingAlgorithmSelector;

    public SimulationToolbar(MainViewModel viewModel) {
        getStyleClass().add("main-toolbar");

        Timeline autoStepTimeline = new Timeline();
        autoStepTimeline.setCycleCount(Animation.INDEFINITE);
        configureAutoStepTimeline(autoStepTimeline, viewModel);
        viewModel.playbackSpeedProperty().addListener((observable, oldValue, newValue) -> {
            boolean wasRunning = autoStepTimeline.getStatus() == Animation.Status.RUNNING;
            autoStepTimeline.stop();
            configureAutoStepTimeline(autoStepTimeline, viewModel);
            if (wasRunning) {
                autoStepTimeline.playFromStart();
            }
        });

        Button startButton = new Button("Start");
        startButton.getStyleClass().add("primary-action");
        startButton.setOnAction(event -> startSimulation(viewModel, autoStepTimeline));

        Button pauseButton = new Button("Pause");
        pauseButton.getStyleClass().add("secondary-action");
        pauseButton.setOnAction(event -> {
            autoStepTimeline.stop();
            viewModel.pause();
        });

        Button stepButton = new Button("Step");
        stepButton.getStyleClass().add("secondary-action");
        stepButton.setOnAction(event -> {
            autoStepTimeline.stop();
            stepSimulation(viewModel);
        });

        Button resetButton = new Button("Reset");
        resetButton.getStyleClass().add("secondary-action");

        ComboBox<AlgorithmCategory> categorySelector = categorySelector();
        ComboBox<AlgorithmDescriptor> algorithmSelector = algorithmSelector();
        categorySelector.setOnAction(event -> {
            autoStepTimeline.stop();
            populateAlgorithmSelector(categorySelector, algorithmSelector, viewModel);
        });
        algorithmSelector.setOnAction(event -> {
            if (updatingAlgorithmSelector) {
                return;
            }
            autoStepTimeline.stop();
            AlgorithmDescriptor selectedAlgorithm = algorithmSelector.getSelectionModel().getSelectedItem();
            if (selectedAlgorithm != null) {
                viewModel.selectAlgorithm(selectedAlgorithm);
                viewModel.loadScenario(AlgorithmRegistry.defaultScenarioFor(selectedAlgorithm.id()));
            }
        });
        categorySelector.getSelectionModel().select(AlgorithmCategory.ELECTION);
        populateAlgorithmSelector(categorySelector, algorithmSelector, viewModel);

        resetButton.setOnAction(event -> {
            autoStepTimeline.stop();
            AlgorithmDescriptor selectedAlgorithm = algorithmSelector.getSelectionModel().getSelectedItem();
            viewModel.selectAlgorithm(selectedAlgorithm);
            if (selectedAlgorithm != null) {
                viewModel.loadScenarioWithFreshRandomSeed(AlgorithmRegistry.defaultScenarioFor(selectedAlgorithm.id()));
            }
        });

        Label runningLabel = new Label();
        runningLabel.getStyleClass().add("status-pill");
        runningLabel.textProperty().bind(
                Bindings.when(viewModel.runningProperty()).then("Running").otherwise("Paused")
        );

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        MenuButton globalSettingsButton = new MenuButton("Global Settings");
        globalSettingsButton.getStyleClass().add("secondary-action");
        CustomMenuItem globalSettingsItem = new CustomMenuItem(new GlobalSettingsPanel(viewModel));
        globalSettingsItem.setHideOnClick(false);
        globalSettingsButton.getItems().setAll(globalSettingsItem);

        EvaluationWindow evaluationWindow = new EvaluationWindow(viewModel);
        Button evaluationButton = new Button("Evaluation");
        evaluationButton.getStyleClass().add("secondary-action");
        evaluationButton.setOnAction(event -> evaluationWindow.show(getScene() == null ? null : getScene().getWindow()));

        Label evaluationSummaryLabel = new Label();
        evaluationSummaryLabel.getStyleClass().add("evaluation-summary");
        Runnable refreshEvaluationSummary = () -> updateEvaluationSummary(viewModel, evaluationSummaryLabel);
        viewModel.algorithmRunSummaryProperty().addListener((observable, oldValue, newValue) -> refreshEvaluationSummary.run());
        viewModel.simulationTimeProperty().addListener((observable, oldValue, newValue) -> refreshEvaluationSummary.run());
        refreshEvaluationSummary.run();

        getItems().setAll(
                startButton,
                pauseButton,
                stepButton,
                resetButton,
                toolbarLabel("Category"),
                categorySelector,
                toolbarLabel("Algorithm"),
                algorithmSelector,
                new Separator(),
                toolbarLabel("Topology"),
                new TopologyControls(viewModel),
                new Separator(),
                globalSettingsButton,
                new Separator(),
                evaluationButton,
                spacer,
                runningLabel,
                new Separator(),
                evaluationSummaryLabel
        );
        setPadding(new Insets(8));
    }

    private Label toolbarLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("toolbar-label");
        return label;
    }

    private void configureAutoStepTimeline(Timeline autoStepTimeline, MainViewModel viewModel) {
        autoStepTimeline.getKeyFrames().setAll(new KeyFrame(
                Duration.millis(playbackDelayMillis(viewModel.playbackSpeedProperty().get())),
                event -> {
                    viewModel.step();
                    if (!viewModel.runningProperty().get()) {
                        autoStepTimeline.stop();
                    }
                }
        ));
    }

    private double playbackDelayMillis(double playbackSpeed) {
        return BASE_AUTO_STEP_DELAY_MILLIS / Math.max(0.25, playbackSpeed);
    }

    private ComboBox<AlgorithmCategory> categorySelector() {
        ComboBox<AlgorithmCategory> selector = new ComboBox<>(FXCollections.observableArrayList(
                AlgorithmRegistry.availableCategories()
        ));
        selector.setConverter(new StringConverter<>() {
            @Override
            public String toString(AlgorithmCategory category) {
                return category == null ? "" : category.displayName();
            }

            @Override
            public AlgorithmCategory fromString(String value) {
                return null;
            }
        });
        selector.setPrefWidth(165);
        return selector;
    }

    private ComboBox<AlgorithmDescriptor> algorithmSelector() {
        ComboBox<AlgorithmDescriptor> selector = new ComboBox<>();
        selector.setConverter(new StringConverter<>() {
            @Override
            public String toString(AlgorithmDescriptor algorithm) {
                return algorithm == null ? "" : algorithm.displayName();
            }

            @Override
            public AlgorithmDescriptor fromString(String value) {
                return null;
            }
        });
        selector.setPrefWidth(150);
        return selector;
    }

    private void updateEvaluationSummary(MainViewModel viewModel, Label evaluationSummaryLabel) {
        AlgorithmRunSummary summary = viewModel.algorithmRunSummaryProperty().get();
        if (summary == null || !summary.present()) {
            evaluationSummaryLabel.setText("No run");
            return;
        }

        long duration = summary.duration(viewModel.simulationTimeProperty().get());
        evaluationSummaryLabel.setText("t=" + duration
                + " | steps " + summary.processedEvents()
                + " | msg " + summary.sentMessages()
                + " | lost " + summary.lostMessages());
    }

    private void populateAlgorithmSelector(
            ComboBox<AlgorithmCategory> categorySelector,
            ComboBox<AlgorithmDescriptor> algorithmSelector,
            MainViewModel viewModel
    ) {
        AlgorithmCategory selectedCategory = categorySelector.getSelectionModel().getSelectedItem();
        List<AlgorithmDescriptor> algorithms = algorithmsFor(selectedCategory);
        updatingAlgorithmSelector = true;
        algorithmSelector.setItems(FXCollections.observableArrayList(algorithms));

        AlgorithmDescriptor currentAlgorithm = viewModel.selectedAlgorithmProperty().get();
        AlgorithmDescriptor algorithmToSelect = algorithms.contains(currentAlgorithm)
                ? currentAlgorithm
                : algorithms.stream().findFirst().orElse(null);
        algorithmSelector.getSelectionModel().select(algorithmToSelect);
        updatingAlgorithmSelector = false;

        if (algorithmToSelect != null) {
            viewModel.selectAlgorithm(algorithmToSelect);
            viewModel.loadScenario(AlgorithmRegistry.defaultScenarioFor(algorithmToSelect.id()));
        }
    }

    private static List<AlgorithmDescriptor> algorithmsFor(AlgorithmCategory category) {
        return AlgorithmRegistry.algorithmsFor(category);
    }

    private void startSimulation(MainViewModel viewModel, Timeline autoStepTimeline) {
        if (autoStepTimeline.getStatus() == Animation.Status.RUNNING) {
            return;
        }
        NodeId selectedNode = viewModel.selectedStartNodeProperty().get();
        AlgorithmDescriptor selectedAlgorithm = viewModel.selectedAlgorithmProperty().get();
        if (selectedNode != null && selectedAlgorithm != null) {
            if (!viewModel.hasPendingEventsProperty().get()) {
                viewModel.startAlgorithm(selectedAlgorithm, selectedNode);
            }
            viewModel.play();
            autoStepTimeline.playFromStart();
        }
    }

    private void stepSimulation(MainViewModel viewModel) {
        NodeId selectedNode = viewModel.selectedStartNodeProperty().get();
        AlgorithmDescriptor selectedAlgorithm = viewModel.selectedAlgorithmProperty().get();
        if (selectedNode != null && selectedAlgorithm != null && !viewModel.hasPendingEventsProperty().get()) {
            viewModel.startAlgorithm(selectedAlgorithm, selectedNode);
        }
        viewModel.step();
    }
}
