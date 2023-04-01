package org.homio.bundle.zigbee.converter.impl.onoff;

import static java.lang.Integer.parseInt;
import static java.lang.Integer.toHexString;

import com.fasterxml.jackson.databind.JsonNode;
import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.ZclCluster;
import com.zsmartsystems.zigbee.zcl.ZclCommand;
import com.zsmartsystems.zigbee.zcl.ZclCommandListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclScenesCluster;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.homio.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.homio.bundle.zigbee.converter.impl.ZigBeeConverter;
import org.jetbrains.annotations.Nullable;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.EntityContextVar.VariableType;
import org.homio.bundle.api.state.ButtonType;
import org.homio.bundle.api.state.ButtonType.ButtonPressType;
import org.homio.bundle.zigbee.util.DeviceConfiguration.EndpointDefinition;

/**
 * Generic converter for buttons (e.g., from remote controls).
 * <p>
 * This converter needs to be configured with the ZigBee commands that are triggered by the button presses. This is done by channel properties that specify the
 * endpoint, the cluster, the command ID, and (optionally) a command parameter.
 * <p>
 * As the configuration is done via channel properties, this converter is usable via static thing types only.
 */
@ZigBeeConverter(name = "button", color = "#3479CF", linkType = VariableType.Bool, clientCluster = 0, serverClusters = {
    ZclScenesCluster.CLUSTER_ID}, category = "Button")
public class ZigBeeConverterGenericButton extends ZigBeeBaseChannelConverter
    implements ZclCommandListener, ZclAttributeListener {

    private final Map<ButtonType.ButtonPressType, EventSpec> handledEvents = new EnumMap<>(ButtonType.ButtonPressType.class);
    private final Set<ZclCluster> clientClusters = new HashSet<>();
    private final Set<ZclCluster> serverClusters = new HashSet<>();

    private static String getParameterName(String parameterType, ButtonType.ButtonPressType buttonPressType) {
        return String.format("zigbee_%s_%s", buttonPressType, parameterType);
    }

    private static int parseId(String id) throws NumberFormatException {
        if (id.startsWith("0x")) {
            return parseInt(id.substring(2), 16);
        } else {
            return parseInt(id);
        }
    }

    @Override
    public void initialize(Consumer<String> progressMessage) {
        for (ButtonPressType buttonPressType : ButtonPressType.values()) {
            EventSpec eventSpec = parseEventSpec(getEndpointService().getEndpointDefinition().map(EndpointDefinition::getMetadata)
                                                                     .orElse(null), buttonPressType);
            if (eventSpec != null) {
                handledEvents.put(buttonPressType, eventSpec);
            }
        }

        if (handledEvents.isEmpty()) {
            log.error("[{}]: No command is specified for any of the possible button press types {}", entityID, endpoint);
            throw new RuntimeException("No command is specified for any of the possible button press types");
        }

        boolean allBindsSucceeded = true;

        for (EventSpec eventSpec : handledEvents.values()) {
            allBindsSucceeded &= eventSpec.bindCluster();
        }

        if (!allBindsSucceeded) {
            throw new RuntimeException("Not all binds succeeded");
        }
    }

    @Override
    public void disposeConverter() {
        for (ZclCluster clientCluster : clientClusters) {
            log.debug("[{}]: Closing client cluster {} for {}", entityID, clientCluster.getClusterId(), endpoint);
            clientCluster.removeCommandListener(this);
        }

        for (ZclCluster serverCluster : serverClusters) {
            log.debug("[{}]: Closing server cluster {} for {}", entityID, serverCluster.getClusterId(), endpoint);
            serverCluster.removeAttributeListener(this);
        }
    }

    @Override
    public boolean acceptEndpoint(ZigBeeEndpoint endpoint, String entityID, EntityContext entityContext, Consumer<String> progressMessage) {
        // This converter is used only for zigbeeRequireEndpoints specified in static thing types, and cannot be used to construct
        // zigbeeRequireEndpoints based on an endpoint alone.
        return false;
    }

    @Override
    public boolean commandReceived(ZclCommand command) {
        ButtonType.ButtonPressType buttonPressType = getButtonPressType(command);
        if (buttonPressType != null) {
            log.debug("[{}]: Matching ZigBee command for press type {} received: {} for {}", entityID,
                buttonPressType, command, endpoint);
            updateChannelState(new ButtonType(buttonPressType));
            return true;
        }
        return false;
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object value) {
        ButtonType.ButtonPressType buttonPressType = getButtonPressType(attribute, value);
        if (buttonPressType != null) {
            log.debug("[{}]: Matching ZigBee attribute for press type {} received: {} for {}", entityID,
                buttonPressType, attribute, endpoint);
            updateChannelState(new ButtonType(buttonPressType));
        }
    }

    private ButtonType.ButtonPressType getButtonPressType(ZclAttribute attribute, Object value) {
        return getButtonPressType(cs -> cs.matches(attribute, value));
    }

    private ButtonType.ButtonPressType getButtonPressType(ZclCommand command) {
        return getButtonPressType(cs -> cs.matches(command));
    }

    private ButtonType.ButtonPressType getButtonPressType(Predicate<EventSpec> predicate) {
        for (Entry<ButtonType.ButtonPressType, EventSpec> entry : handledEvents.entrySet()) {
            if (predicate.test(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private EventSpec parseEventSpec(@Nullable JsonNode metadata, ButtonType.ButtonPressType pressType) {
        if (metadata == null) {
            return null;
        }
        String clusterProperty = metadata.get(getParameterName(CLUSTER, pressType)).asText();

        if (clusterProperty == null) {
            return null;
        }

        int clusterId;

        try {
            clusterId = parseId(clusterProperty);
        } catch (NumberFormatException e) {
            log.warn("[{}]: Could not parse cluster property {} for {}", entityID, clusterProperty, endpoint);
            return null;
        }

        boolean hasCommand = metadata.has(getParameterName(COMMAND, pressType));
        boolean hasAttribute = metadata.has(getParameterName(ATTRIBUTE_ID, pressType));

        if (hasCommand && hasAttribute) {
            log.warn("[{}]: Only one of command or attribute can be used {}", entityID, endpoint);
            return null;
        }

        if (hasCommand) {
            return parseCommandSpec(clusterId, metadata, pressType);
        } else {
            return parseAttributeReportSpec(clusterId, metadata, pressType);
        }
    }

    private AttributeReportSpec parseAttributeReportSpec(int clusterId, JsonNode metadata, ButtonType.ButtonPressType pressType) {
        int attributeId = metadata.path(getParameterName(COMMAND, pressType)).asInt(-1);
        if (attributeId == -1) {
            log.warn("[{}]: Missing attribute id {}", entityID, endpoint);
            return null;
        }

        String attributeValue = metadata.path(getParameterName(ATTRIBUTE_VALUE, pressType)).asText();
        if (attributeValue == null) {
            log.warn("[{}]: No attribute value for attribute {} specified {}", entityID, attributeId, endpoint);
            return null;
        }

        return new AttributeReportSpec(clusterId, attributeId, attributeValue);
    }

    private CommandSpec parseCommandSpec(int clusterId, JsonNode metadata, ButtonType.ButtonPressType pressType) {
        int commandId = metadata.path(getParameterName(COMMAND, pressType)).asInt(-1);
        if (commandId == -1) {
            log.warn("[{}]: Missing command {}", entityID, endpoint);
            return null;
        }

        String commandParameterName = metadata.path(getParameterName(PARAM_NAME, pressType)).asText();
        String commandParameterValue = metadata.path(getParameterName(PARAM_VALUE, pressType)).asText();

        if ((commandParameterName != null && commandParameterValue == null)
            || (commandParameterName == null && commandParameterValue != null)) {
            log.warn("[{}]: When specifying a command parameter, both name and value must be specified {}", entityID, endpoint);
            return null;
        }

        return new CommandSpec(clusterId, commandId, commandParameterName, commandParameterValue);
    }

    private abstract class EventSpec {

        private final int clusterId;

        EventSpec(int clusterId) {
            this.clusterId = clusterId;
        }

        int getClusterId() {
            return clusterId;
        }

        abstract boolean matches(ZclCommand command);

        abstract boolean matches(ZclAttribute attribute, Object value);

        abstract boolean bindCluster();

        boolean bindCluster(String clusterType, Collection<ZclCluster> existingClusters, int clusterId,
            Function<Integer, ZclCluster> getClusterById, Consumer<ZclCluster> registrationFunction) {
            if (existingClusters.stream().anyMatch(c -> c.getClusterId() == clusterId)) {
                // bind to each output cluster only once
                return true;
            }

            ZclCluster cluster = getClusterById.apply(clusterId);
            if (cluster == null) {
                log.error("[{}]: Error opening {} cluster {} on {}", entityID, clusterType,
                    clusterId, endpoint);
                return false;
            }

            try {
                CommandResult bindResponse = bind(cluster);
                if (!bindResponse.isSuccess()) {
                    log.error("[{}]: Error 0x{} setting {} binding for cluster {}. {}", entityID,
                        toHexString(bindResponse.getStatusCode()), clusterType, clusterId, entityID);
                }
            } catch (Exception e) {
                log.error("[{}]: Exception setting {}/{} binding to cluster {}. {}", entityID, clusterType,
                    clusterId, endpoint, e);
            }

            registrationFunction.accept(cluster);
            existingClusters.add(cluster);
            return true;
        }
    }

    protected final class AttributeReportSpec extends EventSpec {

        private final Integer attributeId;
        private final String attributeValue;

        AttributeReportSpec(int clusterId, Integer attributeId, String attributeValue) {
            super(clusterId);
            this.attributeId = attributeId;
            this.attributeValue = attributeValue;
        }

        @Override
        boolean matches(ZclCommand command) {
            return false;
        }

        @Override
        boolean matches(ZclAttribute attribute, Object value) {
            if (attributeId == null) {
                return false;
            }
            boolean attributeIdMatches = attribute.getId() == attributeId;
            boolean attributeValueMatches = Objects.equals(Objects.toString(value), attributeValue);
            return attributeIdMatches && attributeValueMatches;
        }

        @Override
        boolean bindCluster() {
            return bindCluster("server", serverClusters, getClusterId(), endpoint::getInputCluster,
                cluster -> cluster.addAttributeListener(ZigBeeConverterGenericButton.this));
        }
    }

    private final class CommandSpec extends EventSpec {

        private final Integer commandId;
        private final String commandParameterName;
        private final String commandParameterValue;

        private CommandSpec(int clusterId, Integer commandId, String commandParameterName,
            String commandParameterValue) {
            super(clusterId);
            this.commandId = commandId;
            this.commandParameterName = commandParameterName;
            this.commandParameterValue = commandParameterValue;
        }

        private boolean matchesParameter(ZclCommand command) {
            String capitalizedParameterName = commandParameterName.substring(0, 1).toUpperCase()
                + commandParameterName.substring(1);
            try {
                Method propertyGetter = command.getClass().getMethod("get" + capitalizedParameterName);
                Object result = propertyGetter.invoke(command);
                return Objects.equals(result.toString(), commandParameterValue);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
                     | InvocationTargetException e) {
                log.warn("[{}]: Could not read parameter {} for command {}. {}", entityID,
                    commandParameterName, command, entityID, e);
                return false;
            }
        }

        @Override
        boolean matches(ZclCommand command) {
            if (commandId == null) {
                return false;
            }
            boolean commandIdMatches = command.getCommandId().intValue() == commandId;
            return commandIdMatches
                && (commandParameterName == null || commandParameterValue == null || matchesParameter(command));
        }

        @Override
        boolean matches(ZclAttribute attribute, Object value) {
            return false;
        }

        @Override
        boolean bindCluster() {
            return bindCluster("client", clientClusters, getClusterId(), endpoint::getOutputCluster,
                cluster -> cluster.addCommandListener(ZigBeeConverterGenericButton.this));
        }
    }
    private static final String CLUSTER = "cluster_id";
    private static final String COMMAND = "command_id";
    private static final String PARAM_NAME = "parameter_name";
    private static final String PARAM_VALUE = "parameter_value";
    private static final String ATTRIBUTE_ID = "attribute_id";
    private static final String ATTRIBUTE_VALUE = "attribute_value";
}
