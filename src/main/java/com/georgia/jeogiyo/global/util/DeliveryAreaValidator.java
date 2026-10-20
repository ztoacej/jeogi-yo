package com.georgia.jeogiyo.global.util;

import com.georgia.jeogiyo.global.config.DeliveryAreaProperties;
import com.georgia.jeogiyo.global.exception.BusinessException;
import com.georgia.jeogiyo.global.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class DeliveryAreaValidator {

    private final DeliveryAreaProperties deliveryAreaProperties;

    public void validate(String roadAddress) {
        if (!isServiceable(roadAddress)) {
            throw new BusinessException(GlobalErrorCode.OUT_OF_SERVICE_AREA);
        }
    }

    public boolean isServiceable(String roadAddress) {
        if (!StringUtils.hasText(roadAddress)) {
            return false;
        }

        String normalizedRoadAddress = normalize(roadAddress);
        String normalizedPrefix = normalize(deliveryAreaProperties.getRoadAddressPrefix());

        if (!normalizedRoadAddress.startsWith(normalizedPrefix)) {
            return false;
        }

        return deliveryAreaProperties.getRoads().stream()
                .map(DeliveryAreaValidator::normalize)
                .anyMatch(normalizedRoadAddress::contains);
    }

    private static String normalize(String value) {
        return value.replace(" ", "");
    }
}