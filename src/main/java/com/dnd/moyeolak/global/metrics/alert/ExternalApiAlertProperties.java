package com.dnd.moyeolak.global.metrics.alert;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties("external-api.alert")
public class ExternalApiAlertProperties {

    private double thresholdPercent = 20.0;
}
