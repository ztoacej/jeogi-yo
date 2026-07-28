package com.georgia.jeogiyo.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jeogiyo.delivery-area")
public class DeliveryAreaProperties {

    private String roadAddressPrefix = "서울특별시 종로구";

    private List<String> roads = List.of(
            "세종대로",
            "새문안로",
            "종로1길",
            "종로3길",
            "사직로",
            "삼봉로",
            "자하문로",
            "율곡로",
            "우정국로",
            "경희궁길",
            "경희궁1길"
    );
}