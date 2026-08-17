package de.jakobhoermann.distsim.ui.javafx.component;

import de.jakobhoermann.distsim.core.algorithm.AlgorithmDescriptor;
import de.jakobhoermann.distsim.core.algorithm.AlgorithmIds;
import de.jakobhoermann.distsim.core.algorithm.TopologyProfile;
import de.jakobhoermann.distsim.core.model.MessageKind;
import de.jakobhoermann.distsim.core.model.NodeId;
import de.jakobhoermann.distsim.core.model.ResourceId;
import de.jakobhoermann.distsim.core.model.snapshot.MessageSnapshot;
import de.jakobhoermann.distsim.ui.javafx.viewmodel.MainViewModel;
import de.jakobhoermann.distsim.ui.javafx.viewmodel.ResourceViewModel;
import de.jakobhoermann.distsim.ui.javafx.viewmodel.TopologyNodeViewModel;
import javafx.collections.ListChangeListener;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TopologyCanvas extends Pane {
    private static final double NODE_RADIUS = 28;
    private static final double RESOURCE_WIDTH = 112;
    private static final double RESOURCE_HEIGHT = 40;
    private static final double ARROW_HEAD_LENGTH = 16;
    private static final double ARROW_HEAD_WIDTH = 10;
    private static final double CANVAS_PADDING = 48;
    private static final Color CANVAS_BACKGROUND = Color.web("#f8fafc");
    private static final Color CANVAS_GRID = Color.web("#e3eaf1");
    private static final Color EDGE_COLOR = Color.web("#cbd5df");
    private static final Color MESSAGE_COLOR = Color.web("#ea580c");
    private static final Color TEXT_DARK = Color.web("#17202a");

    private final MainViewModel viewModel;
    private final Canvas canvas = new Canvas();
    private final ContextMenu contextMenu = new ContextMenu();
    private final NodeDetailsPanel nodeDetailsPanel;
    private final CustomMenuItem nodeDetailsItem;

    public TopologyCanvas(MainViewModel viewModel) {
        this.viewModel = viewModel;
        this.nodeDetailsPanel = new NodeDetailsPanel(viewModel);
        this.nodeDetailsPanel.setMinWidth(240);
        this.nodeDetailsItem = new CustomMenuItem(nodeDetailsPanel);
        this.nodeDetailsItem.setHideOnClick(false);

        getStyleClass().add("topology-canvas");
        setMinSize(320, 260);
        getChildren().setAll(canvas);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());

        viewModel.getNodes().addListener((ListChangeListener<? super TopologyNodeViewModel>) change -> render());
        viewModel.getResources().addListener((ListChangeListener<? super ResourceViewModel>) change -> render());
        viewModel.getMessagesInFlight().addListener((ListChangeListener<? super MessageSnapshot>) change -> render());
        viewModel.selectedAlgorithmProperty().addListener((observable, oldValue, newValue) -> render());
        viewModel.selectedStartNodeProperty().addListener((observable, oldValue, newValue) -> render());
        canvas.widthProperty().addListener((observable, oldValue, newValue) -> render());
        canvas.heightProperty().addListener((observable, oldValue, newValue) -> render());

        installNodeSelectionHandler();
        installNodeContextMenu();
        render();
    }

    private void installNodeSelectionHandler() {
        canvas.setOnMouseClicked(event -> {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            contextMenu.hide();
            RenderNode node = findNodeAt(event.getX(), event.getY());
            viewModel.selectDetailsNode(node == null ? null : node.nodeId());
        });
    }

    private void installNodeContextMenu() {
        contextMenu.setAutoHide(true);
        canvas.setOnContextMenuRequested(event -> {
            RenderNode node = findNodeAt(event.getX(), event.getY());
            RenderResource resource = findResourceAt(event.getX(), event.getY());
            if (node == null) {
                if (resource != null) {
                    viewModel.selectDetailsNode(null);
                    MenuItem removeResourceItem = new MenuItem("Remove Resource");
                    removeResourceItem.setOnAction(action -> viewModel.removeResource(resource.resourceId()));
                    contextMenu.getItems().setAll(removeResourceItem);
                    contextMenu.show(canvas, event.getScreenX(), event.getScreenY());
                    event.consume();
                    return;
                }
                contextMenu.hide();
                event.consume();
                return;
            }

            viewModel.selectDetailsNode(node.nodeId());

            MenuItem startNodeItem = new MenuItem("Use as Start Node");
            startNodeItem.setDisable(node.nodeId().equals(viewModel.selectedStartNodeProperty().get()));
            startNodeItem.setOnAction(action -> viewModel.selectStartNode(node.nodeId()));

            MenuItem requestCriticalSectionItem = new MenuItem("Request Critical Section");
            requestCriticalSectionItem.setDisable(!node.alive() || !isMutexSelected());
            requestCriticalSectionItem.setOnAction(action -> {
                AlgorithmDescriptor selectedAlgorithm = viewModel.selectedAlgorithmProperty().get();
                if (selectedAlgorithm != null && selectedAlgorithm.isMutex()) {
                    viewModel.startAlgorithm(selectedAlgorithm, node.nodeId());
                }
            });

            MenuItem powerItem = new MenuItem(node.alive() ? "Turn Off" : "Turn On");
            powerItem.setOnAction(action -> viewModel.setNodeActive(node.nodeId(), !node.alive()));

            MenuItem removeItem = new MenuItem("Remove Node");
            removeItem.setOnAction(action -> viewModel.removeNode(node.nodeId()));

            if (isMutexSelected()) {
                contextMenu.getItems().setAll(
                        nodeDetailsItem,
                        new SeparatorMenuItem(),
                        startNodeItem,
                        requestCriticalSectionItem,
                        powerItem,
                        removeItem
                );
            } else {
                contextMenu.getItems().setAll(
                        nodeDetailsItem,
                        new SeparatorMenuItem(),
                        startNodeItem,
                        powerItem,
                        removeItem
                );
            }
            contextMenu.show(canvas, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    private void render() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        if (canvas.getWidth() <= 0 || canvas.getHeight() <= 0) {
            return;
        }

        drawCanvasBackground(gc);

        if (isDeadlockDetectionSelected()) {
            renderDeadlockGraph(gc);
            return;
        }

        renderElectionGraph(gc);
    }

    private void renderElectionGraph(GraphicsContext gc) {
        List<RenderNode> renderNodes = layoutNodes(viewModel.getNodes());

        gc.setStroke(EDGE_COLOR);
        gc.setLineWidth(1.5);
        drawTopologyEdges(gc, viewModel.selectedAlgorithmProperty().get(), renderNodes);

        for (RenderNode node : renderNodes) {
            drawNode(gc, node, node.nodeId().equals(viewModel.selectedStartNodeProperty().get()));
        }

        for (MessageSnapshot message : viewModel.getMessagesInFlight()) {
            RenderNode fromNode = findNode(renderNodes, message.from().value());
            RenderNode toNode = findNode(renderNodes, message.to().value());
            if (fromNode != null && toNode != null) {
                drawMessageArrow(gc, fromNode, toNode, message);
            }
        }
    }

    private void renderDeadlockGraph(GraphicsContext gc) {
        Map<NodeId, RenderNode> processNodes = new LinkedHashMap<>();
        for (RenderNode node : layoutProcessesForDeadlock(viewModel.getNodes())) {
            processNodes.put(node.nodeId(), node);
        }

        Map<ResourceId, RenderResource> resources = new LinkedHashMap<>();
        for (RenderResource resource : layoutResourcesForDeadlock(viewModel.getResources())) {
            resources.put(resource.resourceId(), resource);
        }

        for (RenderNode node : processNodes.values()) {
            for (ResourceId resourceId : node.awaitedResources()) {
                RenderResource resource = resources.get(resourceId);
                if (resource != null) {
                    boolean highlighted = node.deadlockDetected()
                            || (resource.owner() != null && isDeadlocked(processNodes.get(resource.owner())));
                    drawDashedArrow(
                            gc,
                            node.x() + NODE_RADIUS,
                            node.y(),
                            resource.x() - RESOURCE_WIDTH / 2,
                            resource.y(),
                        highlighted ? Color.web("#d1495b") : Color.web("#cc7a29"),
                        highlighted ? 3 : 2
                    );
                }
            }
        }

        for (RenderResource resource : resources.values()) {
            if (resource.owner() == null) {
                continue;
            }
            RenderNode owner = processNodes.get(resource.owner());
            if (owner != null) {
                boolean highlighted = owner.deadlockDetected();
                drawArrow(
                        gc,
                        resource.x() - RESOURCE_WIDTH / 2,
                        resource.y(),
                        owner.x() + NODE_RADIUS,
                        owner.y(),
                        highlighted ? Color.web("#d1495b") : Color.web("#52677a"),
                        highlighted ? 3 : 2
                );
            }
        }

        for (RenderResource resource : resources.values()) {
            drawResource(gc, resource);
        }
        for (RenderNode node : processNodes.values()) {
            drawNode(gc, node, node.nodeId().equals(viewModel.selectedStartNodeProperty().get()));
        }

        for (MessageSnapshot message : viewModel.getMessagesInFlight()) {
            RenderNode fromNode = processNodes.get(message.from());
            RenderNode toNode = processNodes.get(message.to());
            if (fromNode != null && toNode != null) {
                drawMessageArrow(gc, fromNode, toNode, message);
            }
        }
    }

    private List<RenderNode> layoutNodes(List<TopologyNodeViewModel> nodes) {
        if (nodes.isEmpty()) {
            return List.of();
        }

        double minX = nodes.stream().mapToDouble(TopologyNodeViewModel::x).min().orElse(0);
        double maxX = nodes.stream().mapToDouble(TopologyNodeViewModel::x).max().orElse(canvas.getWidth());
        double minY = nodes.stream().mapToDouble(TopologyNodeViewModel::y).min().orElse(0);
        double maxY = nodes.stream().mapToDouble(TopologyNodeViewModel::y).max().orElse(canvas.getHeight());
        double logicalWidth = Math.max(1, maxX - minX);
        double logicalHeight = Math.max(1, maxY - minY);
        double availableWidth = Math.max(1, canvas.getWidth() - CANVAS_PADDING * 2);
        double availableHeight = Math.max(1, canvas.getHeight() - CANVAS_PADDING * 2);
        double scale = Math.min(availableWidth / logicalWidth, availableHeight / logicalHeight);
        double offsetX = (canvas.getWidth() - logicalWidth * scale) / 2 - minX * scale;
        double offsetY = (canvas.getHeight() - logicalHeight * scale) / 2 - minY * scale;

        return nodes.stream()
                .map(node -> new RenderNode(
                        node.nodeId(),
                        node.label(),
                        node.electionPriority(),
                        node.alive(),
                        node.coordinator(),
                        node.inCriticalSection(),
                        node.deadlockDetected(),
                        node.heldResources(),
                        node.awaitedResources(),
                        node.ringSuccessor(),
                        node.x() * scale + offsetX,
                        node.y() * scale + offsetY
                ))
                .toList();
    }

    private List<RenderNode> layoutProcessesForDeadlock(List<TopologyNodeViewModel> nodes) {
        double x = Math.max(CANVAS_PADDING + NODE_RADIUS, canvas.getWidth() * 0.28);
        double availableHeight = Math.max(1, canvas.getHeight() - CANVAS_PADDING * 2);
        double step = nodes.size() <= 1 ? 0 : availableHeight / (nodes.size() - 1);
        return nodes.stream()
                .map(node -> {
                    int index = nodes.indexOf(node);
                    double y = nodes.size() <= 1
                            ? canvas.getHeight() / 2
                            : CANVAS_PADDING + step * index;
                    return new RenderNode(
                            node.nodeId(),
                            node.label(),
                            node.electionPriority(),
                            node.alive(),
                            node.coordinator(),
                            node.inCriticalSection(),
                            node.deadlockDetected(),
                            node.heldResources(),
                            node.awaitedResources(),
                            node.ringSuccessor(),
                            x,
                            y
                    );
                })
                .toList();
    }

    private List<RenderResource> layoutResourcesForDeadlock(List<ResourceViewModel> resources) {
        double x = Math.min(canvas.getWidth() - CANVAS_PADDING - RESOURCE_WIDTH / 2, canvas.getWidth() * 0.72);
        double availableHeight = Math.max(1, canvas.getHeight() - CANVAS_PADDING * 2);
        double step = resources.size() <= 1 ? 0 : availableHeight / (resources.size() - 1);
        return resources.stream()
                .map(resource -> {
                    int index = resources.indexOf(resource);
                    double y = resources.size() <= 1
                            ? canvas.getHeight() / 2
                            : CANVAS_PADDING + step * index;
                    return new RenderResource(
                            resource.resourceId(),
                            resource.label(),
                            resource.owner(),
                            resource.available(),
                            x,
                            y
                    );
                })
                .toList();
    }

    private RenderNode findNodeAt(double x, double y) {
        return currentLayoutNodes().stream()
                .filter(node -> Math.hypot(node.x() - x, node.y() - y) <= NODE_RADIUS)
                .findFirst()
                .orElse(null);
    }

    private RenderResource findResourceAt(double x, double y) {
        if (!isDeadlockDetectionSelected()) {
            return null;
        }
        return layoutResourcesForDeadlock(viewModel.getResources()).stream()
                .filter(resource -> x >= resource.x() - RESOURCE_WIDTH / 2
                        && x <= resource.x() + RESOURCE_WIDTH / 2
                        && y >= resource.y() - RESOURCE_HEIGHT / 2
                        && y <= resource.y() + RESOURCE_HEIGHT / 2)
                .findFirst()
                .orElse(null);
    }

    private List<RenderNode> currentLayoutNodes() {
        if (isDeadlockDetectionSelected()) {
            return layoutProcessesForDeadlock(viewModel.getNodes());
        }
        return layoutNodes(viewModel.getNodes());
    }

    private boolean isDeadlocked(RenderNode node) {
        return node != null && node.deadlockDetected();
    }

    private boolean isDeadlockDetectionSelected() {
        AlgorithmDescriptor selectedAlgorithm = viewModel.selectedAlgorithmProperty().get();
        return selectedAlgorithm != null && selectedAlgorithm.isDeadlockDetection();
    }

    private boolean isMutexSelected() {
        AlgorithmDescriptor selectedAlgorithm = viewModel.selectedAlgorithmProperty().get();
        return selectedAlgorithm != null && selectedAlgorithm.isMutex();
    }

    private void drawTopologyEdges(GraphicsContext gc, AlgorithmDescriptor algorithm, List<RenderNode> nodes) {
        if (algorithm != null && algorithm.topologyProfile() == TopologyProfile.RING) {
            for (RenderNode node : nodes) {
                if (node.ringSuccessor() == null) {
                    continue;
                }
                RenderNode successor = findNode(nodes, node.ringSuccessor().value());
                if (successor != null) {
                    gc.strokeLine(node.x(), node.y(), successor.x(), successor.y());
                }
            }
            return;
        }

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                RenderNode first = nodes.get(i);
                RenderNode second = nodes.get(j);
                gc.strokeLine(first.x(), first.y(), second.x(), second.y());
            }
        }
    }

    private void drawNode(GraphicsContext gc, RenderNode node, boolean startNode) {
        gc.setFill(Color.rgb(15, 23, 42, node.alive() ? 0.14 : 0.08));
        gc.fillOval(node.x() - NODE_RADIUS + 2, node.y() - NODE_RADIUS + 4, NODE_RADIUS * 2, NODE_RADIUS * 2);

        if (startNode) {
            gc.setStroke(Color.web("#0f766e"));
            gc.setLineWidth(4);
            gc.strokeOval(
                    node.x() - NODE_RADIUS - 5,
                    node.y() - NODE_RADIUS - 5,
                    (NODE_RADIUS + 5) * 2,
                    (NODE_RADIUS + 5) * 2
            );
        }

        gc.setFill(nodeFillColor(node));
        gc.fillOval(node.x() - NODE_RADIUS, node.y() - NODE_RADIUS, NODE_RADIUS * 2, NODE_RADIUS * 2);

        gc.setStroke(startNode ? Color.web("#0f766e") : Color.web("#ffffff"));
        gc.setLineWidth(startNode ? 2.5 : 2);
        gc.strokeOval(node.x() - NODE_RADIUS, node.y() - NODE_RADIUS, NODE_RADIUS * 2, NODE_RADIUS * 2);

        gc.setFill(nodeTextColor(node));
        gc.setFont(Font.font("Segoe UI", 17));
        gc.fillText(node.label(), node.x() - approximateTextWidth(node.label(), 16) / 2, node.y() + 6);

        String priority = "p" + node.electionPriority();
        double badgeWidth = approximateTextWidth(priority, 9) + 8;
        double badgeX = node.x() + 7;
        double badgeY = node.y() - 22;
        gc.setFill(Color.rgb(255, 255, 255, 0.88));
        gc.fillRoundRect(badgeX, badgeY, badgeWidth, 16, 8, 8);
        gc.setFill(Color.web("#526170"));
        gc.setFont(Font.font("Segoe UI", 9));
        gc.fillText(priority, badgeX + 4, badgeY + 11);

    }

    private void drawResource(GraphicsContext gc, RenderResource resource) {
        double x = resource.x() - RESOURCE_WIDTH / 2;
        double y = resource.y() - RESOURCE_HEIGHT / 2;
        gc.setFill(Color.rgb(15, 23, 42, 0.10));
        gc.fillRoundRect(x + 2, y + 3, RESOURCE_WIDTH, RESOURCE_HEIGHT, 8, 8);
        gc.setFill(resource.available() ? Color.web("#ffffff") : Color.web("#e0f2fe"));
        gc.fillRoundRect(x, y, RESOURCE_WIDTH, RESOURCE_HEIGHT, 8, 8);
        gc.setStroke(resource.available() ? Color.web("#cbd5df") : Color.web("#38bdf8"));
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(x, y, RESOURCE_WIDTH, RESOURCE_HEIGHT, 8, 8);

        gc.setFill(TEXT_DARK);
        gc.setFont(Font.font("Segoe UI", 12));
        gc.fillText(resource.label(), x + 10, y + 18);
        String owner = resource.owner() == null ? "free" : "owner " + compactNodeLabel(resource.owner().value());
        gc.setFont(Font.font("Segoe UI", 10));
        gc.setFill(Color.web("#526170"));
        gc.fillText(owner, x + 10, y + 32);
    }

    private void drawMessageArrow(
            GraphicsContext gc,
            RenderNode fromNode,
            RenderNode toNode,
            MessageSnapshot message
    ) {
        double deltaX = toNode.x() - fromNode.x();
        double deltaY = toNode.y() - fromNode.y();
        double length = Math.hypot(deltaX, deltaY);
        if (length == 0) {
            return;
        }

        double unitX = deltaX / length;
        double unitY = deltaY / length;
        double startX = fromNode.x() + unitX * NODE_RADIUS;
        double startY = fromNode.y() + unitY * NODE_RADIUS;
        double endX = toNode.x() - unitX * (NODE_RADIUS + 4);
        double endY = toNode.y() - unitY * (NODE_RADIUS + 4);

        gc.setStroke(MESSAGE_COLOR);
        gc.setFill(MESSAGE_COLOR);
        gc.setLineWidth(3);
        gc.strokeLine(startX, startY, endX, endY);

        double baseX = endX - unitX * ARROW_HEAD_LENGTH;
        double baseY = endY - unitY * ARROW_HEAD_LENGTH;
        double normalX = -unitY;
        double normalY = unitX;
        double[] xPoints = {
                endX,
                baseX + normalX * ARROW_HEAD_WIDTH,
                baseX - normalX * ARROW_HEAD_WIDTH
        };
        double[] yPoints = {
                endY,
                baseY + normalY * ARROW_HEAD_WIDTH,
                baseY - normalY * ARROW_HEAD_WIDTH
        };
        gc.fillPolygon(xPoints, yPoints, 3);
        drawMessageLabel(gc, startX, startY, endX, endY, normalX, normalY, message);
    }

    private void drawDashedArrow(
            GraphicsContext gc,
            double startX,
            double startY,
            double endX,
            double endY,
            Color color,
            double lineWidth
    ) {
        gc.setLineDashes(8, 7);
        drawArrow(gc, startX, startY, endX, endY, color, lineWidth);
        gc.setLineDashes(null);
    }

    private void drawArrow(
            GraphicsContext gc,
            double startX,
            double startY,
            double endX,
            double endY,
            Color color,
            double lineWidth
    ) {
        double deltaX = endX - startX;
        double deltaY = endY - startY;
        double length = Math.hypot(deltaX, deltaY);
        if (length == 0) {
            return;
        }

        double unitX = deltaX / length;
        double unitY = deltaY / length;
        gc.setStroke(color);
        gc.setFill(color);
        gc.setLineWidth(lineWidth);
        gc.strokeLine(startX, startY, endX, endY);

        double baseX = endX - unitX * 12;
        double baseY = endY - unitY * 12;
        double normalX = -unitY;
        double normalY = unitX;
        gc.fillPolygon(
                new double[] {endX, baseX + normalX * 7, baseX - normalX * 7},
                new double[] {endY, baseY + normalY * 7, baseY - normalY * 7},
                3
        );
    }

    private void drawMessageLabel(
            GraphicsContext gc,
            double startX,
            double startY,
            double endX,
            double endY,
            double normalX,
            double normalY,
            MessageSnapshot message
    ) {
        String label = messageLabel(message);
        double labelX = (startX + endX) / 2 + normalX * 12;
        double labelY = (startY + endY) / 2 + normalY * 12;

        gc.setFont(Font.font("Segoe UI", 11));
        double labelWidth = gc.getFont().getSize() * label.length() * 0.62 + 10;
        gc.setFill(Color.web("#ffffff"));
        gc.fillRoundRect(labelX - 5, labelY - 15, labelWidth, 20, 8, 8);
        gc.setStroke(Color.web("#fed7aa"));
        gc.setLineWidth(1);
        gc.strokeRoundRect(labelX - 5, labelY - 15, labelWidth, 20, 8, 8);
        gc.setFill(Color.web("#9a3412"));
        gc.fillText(label, labelX, labelY);
    }

    private String messageLabel(MessageSnapshot message) {
        if (message.algorithm().equals(AlgorithmIds.RING_ELECTION)) {
            String candidate = message.attributes().get("candidate");
            if (candidate != null) {
                return message.payload() + "(" + compactNodeLabel(candidate) + ")";
            }
            return message.payload();
        }
        if (message.algorithm().equals(AlgorithmIds.RICART_AGRAWALA_MUTEX)) {
            String timestamp = message.attributes().get("timestamp");
            if (timestamp != null && message.kind() == MessageKind.MUTEX_REQUEST) {
                return message.payload() + "(t=" + timestamp + ")";
            }
            return message.payload();
        }
        if (message.algorithm().equals(AlgorithmIds.LAMPORT_DEADLOCK)) {
            String initiator = message.attributes().get("initiator");
            if (initiator != null) {
                return "PROBE("
                        + compactNodeLabel(initiator)
                        + ","
                        + compactNodeLabel(message.from().value())
                        + ","
                        + compactNodeLabel(message.to().value())
                        + ")";
            }
        }
        return message.kind().name();
    }

    private String compactNodeLabel(String nodeId) {
        int separatorIndex = nodeId.lastIndexOf('-');
        if (separatorIndex >= 0 && separatorIndex < nodeId.length() - 1) {
            return nodeId.substring(separatorIndex + 1).toUpperCase();
        }
        return nodeId;
    }

    private RenderNode findNode(List<RenderNode> nodes, String nodeId) {
        return nodes.stream()
                .filter(node -> node.nodeId().value().equals(nodeId))
                .findFirst()
                .orElse(null);
    }

    private Color nodeFillColor(RenderNode node) {
        if (node.deadlockDetected()) {
            return Color.web("#dc2626");
        }
        if (node.inCriticalSection()) {
            return Color.web("#2563eb");
        }
        if (node.coordinator()) {
            return Color.web("#facc15");
        }
        return node.alive() ? Color.web("#0f766e") : Color.web("#cbd5df");
    }

    private Color nodeTextColor(RenderNode node) {
        if (!node.alive() || node.coordinator()) {
            return TEXT_DARK;
        }
        return Color.WHITE;
    }

    private void drawCanvasBackground(GraphicsContext gc) {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        gc.setFill(CANVAS_BACKGROUND);
        gc.fillRect(0, 0, width, height);

        gc.setFill(CANVAS_GRID);
        double spacing = 24;
        for (double x = 12; x < width; x += spacing) {
            for (double y = 12; y < height; y += spacing) {
                gc.fillOval(x, y, 1.6, 1.6);
            }
        }
    }

    private double approximateTextWidth(String text, double fontSize) {
        return text.length() * fontSize * 0.6;
    }

    private record RenderNode(
            NodeId nodeId,
            String label,
            int electionPriority,
            boolean alive,
            boolean coordinator,
            boolean inCriticalSection,
            boolean deadlockDetected,
            Set<ResourceId> heldResources,
            Set<ResourceId> awaitedResources,
            NodeId ringSuccessor,
            double x,
            double y
    ) {
    }

    private record RenderResource(
            ResourceId resourceId,
            String label,
            NodeId owner,
            boolean available,
            double x,
            double y
    ) {
    }
}
