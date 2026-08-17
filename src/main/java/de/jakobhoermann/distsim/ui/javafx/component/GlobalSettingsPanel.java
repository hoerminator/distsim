package de.jakobhoermann.distsim.ui.javafx.component;

import de.jakobhoermann.distsim.ui.javafx.viewmodel.MainViewModel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public final class GlobalSettingsPanel extends VBox {
    private final MainViewModel viewModel;
    private final Spinner<Integer> baseDelaySpinner = numberSpinner(1, 100, 10);
    private final Spinner<Integer> delayVarianceSpinner = numberSpinner(0, 100, 0);
    private final Spinner<Integer> messageLossSpinner = numberSpinner(0, 100, 0);
    private final Spinner<Integer> randomSeedSpinner = numberSpinner(0, 999_999, 1);
    private final Slider playbackSpeedSlider = new Slider(0.25, 4.0, 1.0);
    private final Label playbackSpeedLabel = new Label();
    private boolean refreshing;

    public GlobalSettingsPanel(MainViewModel viewModel) {
        super(8);
        this.viewModel = viewModel;

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(6);
        addRow(form, 0, "Base delay", baseDelaySpinner);
        addRow(form, 1, "Variance", delayVarianceSpinner);
        addRow(form, 2, "Loss %", messageLossSpinner);
        addRow(form, 3, "Random seed", randomSeedSpinner);
        addRow(form, 4, "Playback Speed", playbackSpeedControl());

        getChildren().setAll(form);
        setPadding(new Insets(2, 0, 8, 0));

        registerListeners(baseDelaySpinner);
        registerListeners(delayVarianceSpinner);
        registerListeners(messageLossSpinner);
        registerListeners(randomSeedSpinner);

        viewModel.baseMessageDelayProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.messageDelayVarianceProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.messageLossProbabilityPercentProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.randomSeedProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.playbackSpeedProperty().addListener((observable, oldValue, newValue) -> refresh());
        playbackSpeedSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            updatePlaybackSpeedLabel(newValue.doubleValue());
            if (!refreshing) {
                viewModel.updatePlaybackSpeed(newValue.doubleValue());
            }
        });
        refresh();
    }

    private static Spinner<Integer> numberSpinner(int min, int max, int initialValue) {
        Spinner<Integer> spinner = new Spinner<>();
        spinner.setEditable(true);
        spinner.setPrefWidth(92);
        spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, initialValue));
        return spinner;
    }

    private void addRow(GridPane form, int row, String labelText, Spinner<Integer> spinner) {
        Label label = new Label(labelText);
        label.setMinWidth(92);
        form.add(label, 0, row);
        form.add(spinner, 1, row);
        GridPane.setMargin(spinner, new Insets(0, 0, 0, 4));
    }

    private void addRow(GridPane form, int row, String labelText, HBox control) {
        Label label = new Label(labelText);
        label.setMinWidth(92);
        form.add(label, 0, row);
        form.add(control, 1, row);
        GridPane.setMargin(control, new Insets(0, 0, 0, 4));
    }

    private void registerListeners(Spinner<Integer> spinner) {
        spinner.valueProperty().addListener((observable, oldValue, newValue) -> updateSettings());
        spinner.getEditor().setOnAction(event -> commitSpinnerEditor(spinner));
        spinner.focusedProperty().addListener((observable, oldValue, focused) -> {
            if (!focused) {
                commitSpinnerEditor(spinner);
            }
        });
    }

    private void refresh() {
        refreshing = true;
        baseDelaySpinner.getValueFactory().setValue(clampToInt(viewModel.baseMessageDelayProperty().get(), 1, 100));
        delayVarianceSpinner.getValueFactory().setValue(clampToInt(viewModel.messageDelayVarianceProperty().get(), 0, 100));
        messageLossSpinner.getValueFactory().setValue(clampToInt(
                viewModel.messageLossProbabilityPercentProperty().get(),
                0,
                100
        ));
        randomSeedSpinner.getValueFactory().setValue(clampToInt(viewModel.randomSeedProperty().get(), 0, 999_999));
        playbackSpeedSlider.setValue(viewModel.playbackSpeedProperty().get());
        updatePlaybackSpeedLabel(viewModel.playbackSpeedProperty().get());
        refreshing = false;
    }

    private void updateSettings() {
        if (refreshing) {
            return;
        }
        viewModel.updateSimulationSettings(
                baseDelaySpinner.getValue(),
                delayVarianceSpinner.getValue(),
                messageLossSpinner.getValue(),
                randomSeedSpinner.getValue()
        );
    }

    private void commitSpinnerEditor(Spinner<Integer> spinner) {
        try {
            spinner.increment(0);
        } catch (NumberFormatException ignored) {
            spinner.getEditor().setText(spinner.getValue().toString());
        }
    }

    private int clampToInt(long value, int min, int max) {
        return (int) Math.max(min, Math.min(max, value));
    }

    private HBox playbackSpeedControl() {
        playbackSpeedSlider.setShowTickMarks(true);
        playbackSpeedSlider.setShowTickLabels(false);
        playbackSpeedSlider.setMajorTickUnit(1.0);
        playbackSpeedSlider.setBlockIncrement(0.25);
        playbackSpeedSlider.setPrefWidth(150);
        playbackSpeedLabel.setMinWidth(42);
        HBox control = new HBox(8, playbackSpeedSlider, playbackSpeedLabel);
        control.setAlignment(Pos.CENTER_LEFT);
        return control;
    }

    private void updatePlaybackSpeedLabel(double value) {
        playbackSpeedLabel.setText("%.2fx".formatted(value));
    }
}
