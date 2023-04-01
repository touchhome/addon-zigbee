package org.homio.bundle.zigbee;

import java.util.ArrayList;
import java.util.List;
import org.homio.bundle.api.model.OptionModel;
import org.homio.bundle.api.ui.action.DynamicOptionLoader;
import org.homio.bundle.zigbee.util.DeviceConfiguration;
import org.homio.bundle.zigbee.util.DeviceConfigurations;

public class SelectModelIdentifierDynamicLoader implements DynamicOptionLoader {

    @Override
    public List<OptionModel> loadOptions(DynamicOptionLoaderParameters parameters) {
        List<OptionModel> models = new ArrayList<>();
        for (DeviceConfiguration defineEndpoint : DeviceConfigurations.getDefineEndpoints()) {
            for (String model : defineEndpoint.getModels()) {
                models.add(
                        OptionModel.of(model, defineEndpoint.getImage())
                                .setIcon(defineEndpoint.getImage()));
            }
        }
        return models;
    }
}
