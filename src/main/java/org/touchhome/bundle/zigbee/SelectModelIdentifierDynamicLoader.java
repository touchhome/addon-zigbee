package org.touchhome.bundle.zigbee;

import java.util.ArrayList;
import java.util.List;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.ui.action.DynamicOptionLoader;
import org.touchhome.bundle.zigbee.util.DeviceConfiguration;
import org.touchhome.bundle.zigbee.util.DeviceConfigurations;

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
